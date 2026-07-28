# UI 自动化导致 MySQL 磁盘耗尽的治理手册

## 1. 结论

本次故障不是 MySQL 无缘无故生成垃圾文件，而是应用层三个问题叠加形成写放大：

1. 操作日志默认保存请求头、请求体、响应头和响应体。UI 自动化的场景、执行结果和轮询响应包含大 JSON，持续进入 `sys_log`。
2. 执行状态变化使用 `updateById` 更新完整 `automation_ui_scene` 行，反复重写 `case_list`、`debug_record`、`test_record` 等 JSON 大字段。
3. Runner 日志、实时画面和执行历史采用高频全量轮询，制造重复网络传输、日志记录和数据库读取。

最终表现为：

- `continew_admin/sys_log.ibd` 持续增长；
- 每次大事务同时写入 binlog，`binlog.000064`、`binlog.000065` 快速轮转；
- 数据盘和 `/tmp` 所在文件系统耗尽后，MySQL 无法创建排序、临时表或 DDL 临时文件，抛出 `OS errno 28 - No space left on device`；
- 页面查询和更新排队，直至服务不可用。

## 2. 三类文件分别做什么

### `continew_admin/sys_log.ibd`

这是启用 InnoDB 独立表空间时 `continew_admin.sys_log` 的数据文件，保存表数据、索引和内部空闲页。当前 28 GiB 主要说明 `sys_log` 曾经写入了大量日志正文。

`DELETE` 只会让表内页面可复用，通常不会立即缩小 `.ibd` 文件。需要在数据清理后通过 `OPTIMIZE TABLE sys_log` 重建表，物理空间才可能返还给文件系统。重建需要额外临时空间，并可能造成锁等待，不能在磁盘已满时直接执行。

### `binlog.000064`、`binlog.000065`

这是 MySQL 二进制日志，按顺序记录数据变更，主要用于主从复制和时间点恢复。它不是临时文件，也不能直接用 `rm` 删除。

本故障中，大量 `sys_log` 插入和场景大 JSON 更新都会进入 binlog；如果没有合理配置过期时间，旧文件还会一直保留。1 GiB 文件通常是达到 `max_binlog_size` 后轮转，92.3 MiB 的 `binlog.000065` 是当前正在写入的新文件。

### `/tmp/MLZD6hEy`

这是 MySQL 执行排序、临时表、索引构建或表重建时尝试创建的临时文件。它不是根因，而是磁盘已经没有可用块或 inode 后最先暴露的失败点。

## 3. 最新代码状态（2026-07-26）

- 操作日志只保存审计元数据；认证头脱敏，普通正文有界截断。
- 执行写入已切换到 `automation_ui_scene_execution_state` 和 execution/case/step/artifact 事实表；执行期间不再更新 `automation_ui_scene.debug_record/test_record`。
- 事实表摘要按字节限制并脱敏，截图 base64 不入库；截图、trace、HTML 等只能保存为文件对象引用。
- 场景列表/报告轮询默认不读取 `case_list` 和历史 JSON，前端使用 `execution_revision` 进行增量刷新。
- 旧数据由 `MigrateAutomationUiExecutionHistory` 任务分批迁移；每个场景事务校验通过后才清空旧列，可重复执行。
- `CleanupAutomationStorage` 负责 sys_log、Runner Job、过期文件对象、artifact 引用和终态执行事实的分批回收，并尊重 `retention_hold`。
- 清空结果、删除计划占位或删除场景时，artifact 引用会先进入立即到期状态，再由清理任务删除真实文件；场景定义版本随场景删除，避免引用先删造成文件孤儿。
- `AutomationStoragePressureGuard` 在场景、批次、Runner 创建入口按挂载盘/数据库容量熔断，防止磁盘满时继续放大写入。

旧 `debug_record/test_record` 的历史读取 fallback 仅用于迁移窗口；确认迁移任务剩余为 0、抽样比对完成后再关闭 fallback 并删除旧列。

## 4. 生产环境止血顺序

以下命令必须先确认备份、复制拓扑和业务窗口。不要直接删除 `.ibd` 或 binlog 文件。

### 4.1 先确认磁盘、临时目录和大表

```bash
df -h /tmp /data
df -i /tmp /data
du -sh /data/1panel/apps/mysql/mysql/data/* | sort -hr | head -20
```

```sql
SHOW VARIABLES WHERE Variable_name IN (
  'datadir', 'tmpdir', 'log_bin', 'binlog_format',
  'max_binlog_size', 'binlog_expire_logs_seconds', 'expire_logs_days'
);

SELECT table_schema,
       table_name,
       ROUND(data_length / 1024 / 1024, 1) AS data_mb,
       ROUND(index_length / 1024 / 1024, 1) AS index_mb,
       ROUND(data_free / 1024 / 1024, 1) AS free_mb,
       table_rows
FROM information_schema.tables
WHERE table_schema = 'continew_admin'
ORDER BY data_length + index_length DESC
LIMIT 20;
```

### 4.2 确认 `sys_log` 的增长来源

```sql
SELECT request_url,
       COUNT(*) AS row_count,
       ROUND(SUM(COALESCE(OCTET_LENGTH(request_body), 0)
               + COALESCE(OCTET_LENGTH(response_body), 0)) / 1024 / 1024, 1) AS body_mb,
       MIN(create_time) AS first_time,
       MAX(create_time) AS last_time
FROM continew_admin.sys_log
GROUP BY request_url
ORDER BY body_mb DESC
LIMIT 30;
```

应用修复版本尚未发布时，应先停止 UI 自动化任务，或临时停止应用写流量，再处理空间；否则清理速度可能赶不上增长速度。

### 4.3 安全清理旧 binlog

先查看文件和复制状态：

```sql
SHOW BINARY LOGS;
SHOW MASTER STATUS;
SHOW REPLICA STATUS\G
```

确认不再被副本、备份或时间点恢复需要后，通过 MySQL 命令清理，例如保留最近 7 天：

```sql
PURGE BINARY LOGS BEFORE (NOW() - INTERVAL 7 DAY);
```

MySQL 8 建议在 1Panel 的 MySQL 配置中持久化：

```ini
[mysqld]
binlog_expire_logs_seconds=604800
max_binlog_size=256M
```

修改后按维护流程重启 MySQL，并验证：

```sql
SHOW VARIABLES LIKE 'binlog_expire_logs_seconds';
SHOW VARIABLES LIKE 'max_binlog_size';
```

`max_binlog_size` 只改变单个文件的轮转大小，不减少总日志量；真正控制保留量的是过期策略和应用写入量。

### 4.4 分批清理 `sys_log`

先做数据库备份。根据审计要求确定保留期，以下示例保留 30 天，每次删除 10,000 行：

```sql
DELETE FROM continew_admin.sys_log
WHERE create_time < NOW() - INTERVAL 30 DAY
ORDER BY id
LIMIT 10000;
```

重复执行，直到受影响行数为 0。批量小事务可降低锁持有时间、undo 和 binlog 峰值。若当前磁盘极度紧张，先扩容或安全清理旧 binlog，为删除事务和后续重建留出空间。

确认剩余数据：

```sql
SELECT COUNT(*) AS retained_rows,
       MIN(create_time) AS oldest_time,
       MAX(create_time) AS newest_time
FROM continew_admin.sys_log;
```

### 4.5 回收 `sys_log.ibd` 物理空间

仅在已经释放足够磁盘、完成备份并进入维护窗口后执行：

```sql
OPTIMIZE TABLE continew_admin.sys_log;
```

执行前应至少预留接近当前表大小的额外空间，并评估锁表时间。空间不足或无法停机时，应先扩容，再考虑使用经过验证的在线表重建工具；不要手工删除 `sys_log.ibd`。

## 5. 发布后验证

### 5.1 验证操作日志不再保存大正文

执行一轮 UI 自动化后检查：

```sql
SELECT id,
       request_url,
       OCTET_LENGTH(request_body) AS request_bytes,
       OCTET_LENGTH(response_body) AS response_bytes,
       create_time
FROM continew_admin.sys_log
WHERE create_time > NOW() - INTERVAL 30 MINUTE
  AND request_url LIKE '%/automation/%'
ORDER BY id DESC
LIMIT 100;
```

验收标准：实时帧和产物接口没有日志行；场景、Playwright 用例和任务接口的正文为空；其他接口正文不超过约 8 KiB。

### 5.2 验证场景更新不再包含执行大 JSON

临时开启 MySQL general log 会产生额外开销，不建议在生产长期开启。优先通过应用 SQL 日志或测试环境确认执行状态更新形如：

```sql
UPDATE automation_ui_scene_execution_state
SET execute_status = ?, execute_result = ?, execution_revision = execution_revision + 1,
    update_time = CURRENT_TIMESTAMP(3)
WHERE scene_id = ?;
```

验收标准：执行过程中的 UPDATE 不再出现 `case_list`、`debug_record`、`test_record` 或场景元数据字段。

### 5.3 执行迁移和清理任务

1. 先执行 Liquibase changeset `automation_ui_execution_v2.sql`，确认五类新表和 `retention_hold` 字段存在。
2. 在 SnailJob 创建 `MigrateAutomationUiExecutionHistory`，低峰期重复触发，直到日志中的 `remaining=false` 且旧列非空场景数为 0：

```sql
SELECT COUNT(*) AS legacy_scene_count
FROM automation_ui_scene
WHERE debug_record IS NOT NULL OR test_record IS NOT NULL;
```

3. 创建 `CleanupAutomationStorage` 每日任务；首次运行观察文件对象删除失败是否可重试，再逐步缩短保留期。
4. 为 admin 容器只读挂载 MySQL 数据盘并设置 `SAKURA_AUTOMATION_STORAGE_MONITORED_PATHS`，否则水位熔断只能监测 admin 的临时目录。

### 5.4 连续观察增长速度

```sql
SHOW BINARY LOGS;

SELECT ROUND((data_length + index_length) / 1024 / 1024, 1) AS sys_log_mb,
       table_rows,
       update_time
FROM information_schema.tables
WHERE table_schema = 'continew_admin'
  AND table_name = 'sys_log';
```

建议每 5 分钟采样一次，至少观察一轮高并发 UI 自动化。磁盘告警应同时覆盖容量和 inode，建议在 70%、80%、90% 设置分级告警。

## 6. 回滚说明

- 应用修复仅改变日志留存、执行历史上限、轮询响应和执行字段更新，不改变 `AutomationUiSceneDO -> CaseDO -> StepDO` 主数据结构。
- `playwright_step`、`locator_meta` 和场景 `case_list` 不会被裁剪。
- 回滚应用版本不会恢复已经按保留策略删除的日志，因此清理前必须按审计要求备份。
- binlog 清理不可通过文件恢复；执行 `PURGE BINARY LOGS` 前必须确认副本和备份依赖。
