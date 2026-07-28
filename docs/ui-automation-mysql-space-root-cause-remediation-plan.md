# UI 自动化 MySQL 磁盘问题根治方案

## 1. 文档目的

本文给出 UI 自动化执行导致 MySQL 页面卡顿、`sys_log.ibd` 和 binlog 快速增长、最终出现 `/tmp/... No space left on device` 的完整根治方案。

现有止血措施和存量空间处理命令见：

- [UI 自动化导致 MySQL 磁盘耗尽的治理手册](./ui-automation-mysql-space-remediation.md)

本文关注长期架构治理，不把减少日志正文、限制历史 JSON 条数或缩短轮询响应视为最终完成。

## 2. 当前结论

当前故障由四类问题叠加产生：

1. `sys_log` 曾长期保存 UI 自动化大请求、大响应和高频轮询数据。
2. `automation_ui_scene` 同时保存场景定义、执行摘要和完整执行历史，执行状态变化会反复更新大字段。
3. `debug_record`、`test_record` 使用整段 JSON 读改写，存在写放大、并发覆盖和无界数据风险。
4. binlog、系统日志、Runner 任务、审计文件和自动化产物没有形成统一、可验证的生命周期闭环。

现有选择性 UPDATE、日志正文关闭、增量轮询和 5 MiB 历史限制可以止血，但仍存在以下根本缺陷：

- MySQL `ROW + binlog_row_image=FULL` 下，选择性 UPDATE 仍可能把整行大字段写入 binlog。
- 每次结果回传仍会整体序列化和更新 `debug_record` 或 `test_record`。
- 多个回调并发修改同一场景历史时可能发生最后写入覆盖。
- 单条超大结果仍可能突破历史总量限制。
- 已有 28 GiB `sys_log.ibd`、旧 binlog 和旧历史不会因发布新代码自动缩小。

因此，根治必须同时完成数据模型拆分、写入一致性、查询瘦身、日志与产物生命周期、MySQL 参数治理、存量清理和持续监控。

### 2.1 本次已实施内容（2026-07-26 收口版）

本次已完成从写入、读取、迁移到清理的代码闭环：

1. Liquibase 新增 `automation_ui_scene_definition_revision`、`automation_ui_scene_execution_state`、execution/case/step/artifact 五类表；执行事实保存有界摘要，定义快照剥离 screenshot/base64。
2. `AutomationUiExecutionRecordService` 接管 Playwright 批次、用例状态、结果回传、Jenkins 回传、测试计划占位和正式报告聚合；通过 `execution_key`、用例 attempt 和步骤 event sequence 实现幂等更新。
3. `automation_ui_scene` 的 `debug_record/test_record` 已停止由运行链路写入；读取层优先规范化表，保留旧 JSON 只读 fallback，迁移完成后可清空旧列。
4. 列表、详情和报告轮询默认排除大 `case_list`/历史字段；前端轮询改用 `execution_revision`，不再把场景定义 `updateTime` 当执行版本。
5. 新增 `MigrateAutomationUiExecutionHistory` SnailJob：按小批次迁移旧 JSON，逐条验证 execution key，事务内仅在成功后清空旧列，可重复执行。
6. `CleanupAutomationStorage` 改为先清理过期文件对象和 artifact 引用，再按 `retention_hold=0`、终态和 finished_at 小批量删除步骤/用例/执行；不执行 `OPTIMIZE TABLE`。
   清空执行结果、删除测试计划占位或删除场景时不再直接丢弃 artifact 引用，而是将其立即置为到期，由统一清理任务删除真实文件；删除场景同时回收不可变定义版本，避免数据库和对象存储孤儿。
7. 新增存储水位熔断器 `AutomationStoragePressureGuard`，在场景、批次、Runner 创建入口检查挂载盘可用空间/使用率和可选数据库逻辑容量上限。
8. Surefire 默认保持快速构建，但 `-DrunTests=true` 可真实执行测试；本轮相关模块共 63 项测试通过（0 failure、0 error、0 skipped），管理端 11 个关联模块离线编译通过；前端 typecheck 与生产构建通过。

上线后仍需人工完成三项运行操作：执行 v2 Liquibase changeset；在 SnailJob 创建并低峰运行迁移/清理任务；将 `SAKURA_AUTOMATION_STORAGE_MONITORED_PATHS` 指向 MySQL 数据盘（或设置数据库逻辑容量上限）。这些是部署环境动作，不能由代码在未知生产路径上代替执行。

## 3. 根治目标与边界

### 3.1 必须实现的目标

1. `sakura-admin` 继续作为唯一主数据源。
2. `AutomationUiSceneDO -> CaseDO -> StepDO` 场景定义层级保持不变。
3. `case_list` 只保存场景定义，不保存执行截图、视频、trace、报告正文或执行日志。
4. `playwright_step` 和 `locator_meta` 继续完整保存，不因数据库治理而丢失。
5. 场景执行状态变化不再整体更新执行历史 JSON。
6. 每个批次、用例、步骤结果可以独立、幂等、并发安全地写入。
7. 列表和轮询接口默认不查询 `case_list` 和历史详情。
8. `sys_log`、binlog、Runner 任务、审计文件和产物都有明确保留期和自动清理机制。
9. 磁盘达到危险阈值时，系统能够阻止新的自动化执行继续扩大故障。
10. 原 Jenkins 入口、测试计划、测试报告和现有 UI 自动化场景管理默认行为不被破坏。

### 3.2 不允许采用的方案

- 不允许直接删除 `sys_log.ibd`、binlog 文件或 InnoDB 数据文件。
- 不允许仅靠调大磁盘、迁移 `/tmp` 或降低 `max_binlog_size` 宣称根治。
- 不允许把 screenshot base64、视频、trace 或 HTML 报告重新写入 `case_list`、`debug_record`、`test_record`。
- 不允许继续用无版本控制的“读取整段 JSON、修改、整段覆盖”作为正式执行结果模型。
- 不允许为减少 binlog 未评估就关闭 binlog，或未经复制验证直接修改 `binlog_row_image`。

## 4. 目标数据架构（2026-07-26 修订版）

### 4.1 数据职责拆分

```text
automation_ui_scene
└── 只保存场景元数据和当前定义；执行期间不更新大 JSON

automation_ui_scene_definition_revision
└── 不可变的 CaseDO -> StepDO 定义快照

automation_ui_scene_execution_state
└── scene_id -> latest_execution_id、execution_revision、最新摘要
    （独立窄表，执行回调只更新此表）

automation_ui_execution
├── 一次场景执行或测试计划中的场景执行
├── batch_id、test_plan_id、test_report_id、trigger_type、execution_engine
├── 状态、结果、开始/结束时间、执行人
└── 场景级统计摘要

automation_ui_execution_case
├── execution_id + case_id + attempt_no 唯一
├── 用例状态、结果、耗时、错误摘要、job_id
└── 用例级统计摘要

automation_ui_execution_step
├── execution_case_id + step_index + attempt 唯一
├── 步骤状态、耗时、动作类型、定位结果
└── 有硬上限的诊断 JSON

automation_ui_execution_artifact
├── execution/case/step 关联
├── artifact 类型、file_id；受保护 URL 按请求动态生成
├── size、sha256、存储状态
└── expires_at
```

`automation_ui_scene` 不再承担执行历史存储，也不再新增 `latest_execution_id` 或 `execution_revision` 字段。此前把这两个字段放到场景表的设计已废弃：在 `ROW + binlog_row_image=FULL` 下，频繁更新场景行仍可能记录整行。最新状态必须存放在独立的 `automation_ui_scene_execution_state` 窄表中。数据库迁移脚本为：

`continew-webapi/src/main/resources/db/changelog/mysql/automation_ui_execution_v2.sql`

### 4.2 建议表结构

以下为已落地 changeset 的约束摘要；以迁移脚本为准。执行表采用追加式事实记录，状态表不包含 `LONGTEXT`、`JSON` 或截图正文。

```sql
CREATE TABLE automation_ui_scene_execution_state (
  scene_id BIGINT NOT NULL,
  latest_execution_id BIGINT NULL,
  execution_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
  execute_status VARCHAR(32) NULL,
  execute_result VARCHAR(32) NULL,
  case_total INT UNSIGNED NULL,
  case_pass INT UNSIGNED NULL,
  case_fail INT UNSIGNED NULL,
  case_skip INT UNSIGNED NULL,
  step_total INT UNSIGNED NULL,
  step_pass INT UNSIGNED NULL,
  step_fail INT UNSIGNED NULL,
  step_skip INT UNSIGNED NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (scene_id)
);
```

```sql
CREATE TABLE automation_ui_execution (
  id BIGINT NOT NULL,
  execution_key VARCHAR(128) NOT NULL COMMENT '跨请求幂等执行标识',
  scene_id BIGINT NOT NULL,
  scene_key VARCHAR(128) NOT NULL,
  batch_id VARCHAR(128) NULL,
  test_plan_id BIGINT NULL,
  test_report_id BIGINT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  execution_engine VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  result VARCHAR(32) NULL,
  case_total INT UNSIGNED NOT NULL DEFAULT 0,
  case_pass INT UNSIGNED NOT NULL DEFAULT 0,
  case_fail INT UNSIGNED NOT NULL DEFAULT 0,
  case_skip INT UNSIGNED NOT NULL DEFAULT 0,
  step_total INT UNSIGNED NOT NULL DEFAULT 0,
  step_pass INT UNSIGNED NOT NULL DEFAULT 0,
  step_fail INT UNSIGNED NOT NULL DEFAULT 0,
  step_skip INT UNSIGNED NOT NULL DEFAULT 0,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  duration_ms BIGINT UNSIGNED NULL,
  executor_id BIGINT NULL,
  executor_name VARCHAR(128) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(2000) NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_automation_ui_execution_key (execution_key),
  KEY idx_automation_ui_execution_scene_time (scene_id, create_time),
  KEY idx_automation_ui_execution_batch (batch_id),
  KEY idx_automation_ui_execution_report (test_report_id),
  KEY idx_automation_ui_execution_status_time (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

```sql
CREATE TABLE automation_ui_execution_case (
  id BIGINT NOT NULL,
  execution_id BIGINT NOT NULL,
  case_id VARCHAR(128) NOT NULL,
  case_name VARCHAR(255) NULL,
  case_index INT UNSIGNED NOT NULL,
  attempt_no SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  job_id VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  result VARCHAR(32) NULL,
  step_total INT UNSIGNED NOT NULL DEFAULT 0,
  step_pass INT UNSIGNED NOT NULL DEFAULT 0,
  step_fail INT UNSIGNED NOT NULL DEFAULT 0,
  step_skip INT UNSIGNED NOT NULL DEFAULT 0,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  duration_ms BIGINT UNSIGNED NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(2000) NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_automation_ui_execution_case (execution_id, case_id, attempt_no),
  KEY idx_automation_ui_execution_case_job (job_id),
  KEY idx_automation_ui_execution_case_status (execution_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

```sql
CREATE TABLE automation_ui_execution_step (
  id BIGINT NOT NULL,
  execution_case_id BIGINT NOT NULL,
  step_id VARCHAR(128) NULL,
  step_index INT UNSIGNED NOT NULL,
  attempt SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  action_type VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  duration_ms BIGINT UNSIGNED NULL,
  locator_source VARCHAR(64) NULL,
  locator_type VARCHAR(64) NULL,
  locator_value VARCHAR(2000) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(2000) NULL,
  diagnostics JSON NULL COMMENT '仅保存受控诊断字段，序列化后不得超过64KiB',
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_automation_ui_execution_step (execution_case_id, step_index, attempt),
  KEY idx_automation_ui_execution_step_status (execution_case_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

```sql
CREATE TABLE automation_ui_execution_artifact (
  id BIGINT NOT NULL,
  execution_id BIGINT NOT NULL,
  execution_case_id BIGINT NOT NULL DEFAULT 0,
  execution_step_id BIGINT NOT NULL DEFAULT 0,
  artifact_type VARCHAR(64) NOT NULL,
  file_id BIGINT NULL,
  storage_status VARCHAR(32) NOT NULL,
  size_bytes BIGINT UNSIGNED NULL,
  sha256 CHAR(64) NULL,
  expires_at DATETIME NULL,
  create_time DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_automation_ui_execution_artifact
    (execution_id, execution_case_id, execution_step_id, artifact_type),
  KEY idx_automation_ui_execution_artifact_expire (expires_at, storage_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

不建议把完整原始 Runner 结果再次作为一个无约束 JSON 字段保存。需要保留的扩展诊断必须经过 DTO 白名单和大小限制；超限部分转为 artifact 文件。

## 5. 写入模型与并发一致性

### 5.1 幂等键

所有写接口必须携带稳定业务键：

- 执行：`execution_key`。
- 用例：`execution_id + case_id`。
- 步骤：`execution_case_id + step_index + attempt`。
- 产物：`execution/case/step + artifact_type`。

服务端使用唯一索引和 upsert 保证客户端重试不会生成重复记录。

### 5.2 状态机

状态变化必须使用条件 UPDATE，不允许任意覆盖终态：

```sql
UPDATE automation_ui_execution_case
SET status = ?,
    version = version + 1,
    update_time = CURRENT_TIMESTAMP(3)
WHERE id = ?
  AND version = ?
  AND status IN ('queued', 'starting', 'running');
```

更新行数为 0 时，服务端重新读取状态并判断是幂等重试、合法补充还是冲突。取消、失败、通过等终态转换规则必须集中在领域服务中。

### 5.3 事务边界

一次用例结果回传建议在单个短事务中完成：

1. upsert 用例结果。
2. 批量 upsert 步骤结果。
3. 根据执行表中的用例行重新汇总执行统计。
4. 更新执行状态和 `version`。
5. 更新 `automation_ui_scene_execution_state` 的最新摘要、`latest_execution_id` 和 `execution_revision + 1`，不得更新场景大行。

禁止在事务中上传大文件、读取远程报告或执行耗时网络调用。artifact 先上传，数据库事务只写元数据引用。

### 5.4 过渡期并发保护

执行表改造完成前，如果仍需写旧 JSON，必须至少做到：

- 查询场景时 `SELECT ... FOR UPDATE`；或
- 增加版本字段并使用 `WHERE id = ? AND version = ?`；
- 同一 `sceneId + batchId` 的状态回调串行处理；
- 冲突时重新读取、合并后重试，禁止无条件覆盖。

该措施只是迁移期保护，不替代执行历史拆表。

## 6. API 与查询改造

### 6.1 列表 DTO

场景列表只返回：

- 场景基本信息。
- 当前状态与最新执行摘要。
- `latestExecutionId`。
- `executionRevision`。

列表 SQL 必须显式列字段，禁止 `SELECT *`，不得包含：

- `case_list`
- `debug_record`
- `test_record`
- 其他大 JSON 或 LONGTEXT

### 6.2 详情与历史接口

建议拆分为：

```text
GET /automation/automationUiScene/{id}/definition
GET /automation/automationUiScene/{id}/execution-summary
GET /automation/executions?sceneId=&page=&size=
GET /automation/executions/{executionId}
GET /automation/executions/{executionId}/cases
GET /automation/execution-cases/{caseExecutionId}/steps
GET /automation/executions/revisions?sceneIds=
```

历史记录必须数据库分页，禁止查询所有场景后在 Java 中过滤 JSON 再分页。

### 6.3 增量轮询

- 场景版本使用单调递增的 `execution_revision`，不再使用秒级 `update_time` 作为唯一版本标识。
- Runner 日志继续使用 `afterSequence`。
- 前端轮询采用一次请求完成后再 `setTimeout` 下一次，禁止异步 `setInterval` 产生重叠请求。
- 页面不可见时降低轮询频率或暂停。
- 终态后停止轮询。
- 后续规模扩大时可演进为 SSE/WebSocket，但不是本次根治的前置条件。

## 7. 入库数据大小与安全限制

### 7.1 白名单策略

结果 DTO 只允许保存业务必需字段。未知字段默认不入数据库，需要诊断时写入受保护 artifact。

建议限制：

| 数据 | 建议上限 | 超限处理 |
| --- | ---: | --- |
| 单条错误信息 | 2 KiB | 截断并标记 |
| 单步骤 diagnostics | 64 KiB | 文件化并保存引用 |
| 单用例结构化摘要 | 256 KiB | 拒绝或文件化 |
| 单次结果回传 JSON | 2 MiB | 返回明确错误码 |
| 实时 JPEG | 按画质 2～16 MiB | 沿用画质限制 |
| trace/video/report | 不进入 JSON | 文件或对象存储 |

### 7.2 base64 防护

服务端入库前递归检查字符串字段：

- 拒绝 `data:image/`、`data:video/`、`data:application/zip` 等 data URL。
- 对疑似超长 base64 字符串拒绝入库并提示上传 artifact。
- screenshot、DOM 快照、响应快照必须文件化。
- `playwright_step` 和 `locator_meta` 需要完整保留，但不应包含执行期截图正文。

## 8. `sys_log` 根治方案

### 8.1 采集策略

默认只保存审计元数据：描述、模块、方法、URL、状态码、耗时、用户、IP、时间。

接口分类：

| 接口类型 | 日志策略 |
| --- | --- |
| 创建执行、取消执行、权限变更 | 保存审计元数据 |
| Runner 状态 GET、revision GET | 不落 `sys_log` 或采样 |
| live-frame、artifact 二进制传输 | 完全不落 `sys_log` |
| 普通业务接口 | 正文默认关闭，诊断环境受控开启 |

路径规则必须覆盖真实 Controller 路径，例如 `/automation/playwright/runner/jobs`，并增加单元测试，避免依赖容易遗漏的字符串片段。

### 8.2 自动保留

至少实现以下一种方式：

1. 应用定时任务按 `create_time` 分批删除。
2. 独立运维任务执行相同批处理。
3. 日志量极大时评估按月分区，但必须先解决分区表唯一键限制和归档要求。

推荐初始策略：

- 成功日志保留 30 天。
- 失败和安全审计日志保留 90 天或按合规要求归档。
- 每批删除 5,000～10,000 行并提交。
- 每日低峰执行，记录删除行数和执行耗时。
- 设置单次最大运行时间，避免清理任务长时间占锁。

删除只释放表内可复用页。是否执行 `OPTIMIZE TABLE` 应根据 `data_free`、磁盘余量和维护窗口决定，不应每日执行。

## 9. binlog 根治方案

### 9.1 上线前必须确认

```sql
SHOW VARIABLES WHERE Variable_name IN (
  'log_bin',
  'binlog_format',
  'binlog_row_image',
  'binlog_row_value_options',
  'binlog_expire_logs_seconds',
  'binlog_expire_logs_auto_purge',
  'max_binlog_size'
);

SHOW BINARY LOGS;
SHOW REPLICA STATUS\G
```

### 9.2 参数原则

- 保留 binlog 是否开启由复制、备份和时间点恢复要求决定，不通过简单关闭规避问题。
- `max_binlog_size` 只改变单个文件轮转大小，不降低总写入量。
- `binlog_expire_logs_seconds` 必须大于最大副本延迟和备份恢复窗口。
- MySQL 8.0.29 及以上还要确认 `binlog_expire_logs_auto_purge=ON`。
- `binlog_row_image=NOBLOB` 或 `MINIMAL` 可以减少 ROW 日志，但必须在与生产相同的复制拓扑中验证后再调整。
- 即使调整 row image，执行历史拆表仍必须完成；不能用数据库参数掩盖应用大 JSON 整体更新。

MySQL 官方说明：`binlog_row_image=FULL` 会记录更新行的完整 before/after image；`MINIMAL` 只记录定位行和 SQL 指定的更新列。参考：

- [MySQL Binary Logging Options and Variables](https://dev.mysql.com/doc/mysql-replication-excerpt/8.0/en/replication-options-binary-log.html)

## 10. artifact 和本地文件生命周期

### 10.1 Runner 本地目录

- 上传和结果回传都成功后，成功执行本地产物建议保留 24 小时。
- 失败执行本地产物建议保留 7 天，便于上传失败时人工恢复。
- 上传失败或结果回传失败的目录不得立即删除，但必须进入告警和补偿队列。
- `session-audit.log`、Runner 结构化日志按天滚动并配置保留天数和总大小上限。

### 10.2 统一文件或对象存储

建议按 artifact 类型配置：

| 类型 | 建议保留期 |
| --- | ---: |
| 成功执行 console/report | 30～90 天 |
| 失败截图和 HTML | 90 天 |
| trace/video | 30 天或按报告生命周期 |
| 合规要求保留的报告 | 跟随测试报告策略 |

删除任务必须先把数据库记录标记为 `deleting`，对象删除成功后标记 `deleted`；失败可重试，避免数据库和存储状态不一致。

## 11. `automation_playwright_job` 生命周期

Runner 运行时内存清理不等于数据库记录清理。建议：

- queued/running 记录不可自动删除。
- passed/cancelled 记录保留 30 天。
- failed/interrupted 记录保留 90 天。
- 删除前确认执行历史已经保存所需 `job_id` 和错误摘要。
- 定时任务分批删除，并通过 `status + finished_at` 索引控制扫描范围。

## 12. 磁盘水位保护

在 Runner 创建任务入口增加磁盘健康检查。建议同时检查：

- MySQL 数据盘可用容量。
- `/tmp` 所在文件系统容量。
- inode 使用率。
- artifact 本地目录容量。

建议策略：

| 使用率 | 动作 |
| --- | --- |
| 70% | 告警并记录增长趋势 |
| 80% | 限制批量并发、触发清理 |
| 90% | 拒绝新自动化任务，只允许查询、取消和清理 |

拒绝执行时返回明确错误，例如 `AUTOMATION_STORAGE_PRESSURE`，不能等 MySQL 抛出 errno 28 后才暴露问题。

监控项至少包括：

- 文件系统容量和 inode。
- `sys_log` 行数、数据大小、每日新增量。
- `automation_ui_scene` 大字段最大值和分位数。
- 执行表每日新增行数。
- binlog 每小时增长量和最旧文件时间。
- artifact 数量、总大小、上传失败数和过期未删除数。
- MySQL 临时表、磁盘临时表和 `Binlog_cache_disk_use`。

## 13. 存量数据治理

新代码不会自动缩小现有文件，必须单独执行存量治理。

### 13.1 操作顺序

1. 暂停新的 UI 自动化任务。
2. 完成数据库备份并确认复制状态。
3. 安全 PURGE 不再需要的旧 binlog，禁止使用 `rm`。
4. 分批清理过期 `sys_log`。
5. 为需要保留的旧执行历史导出归档文件或迁移到新执行表。
6. 校验迁移数量、批次、状态和抽样详情。
7. 备份旧 JSON 后，将已迁移场景的 `debug_record/test_record` 清空。
8. 在有足够额外磁盘空间的维护窗口重建 `sys_log`，回收 `.ibd` 物理空间。
9. 观察数据盘和 `/tmp` 水位后恢复任务。

### 13.2 旧历史迁移原则

- 一条旧 `debug_record/test_record` 转换为一条 execution。
- 其中 cases 转为 execution_case，steps 转为 execution_step。
- artifact URL 和文件 ID 转为 execution_artifact。
- 无法识别的旧字段写入迁移归档文件，不静默丢弃。
- 每个场景记录迁移状态、迁移数量、失败原因和校验摘要。
- 迁移脚本必须可重入，依靠 `execution_key` 唯一索引避免重复。

## 14. 分阶段实施计划

### 阶段 0：生产止血与基线采样

交付内容：

- 发布现有日志正文关闭、增量轮询、选择性 UPDATE 和临时历史限制。
- 修正 Runner 日志路径匹配。
- 清理存量 binlog 和过期 `sys_log`。
- 记录修复前 24 小时的 binlog、`sys_log` 和场景更新增长基线。

完成标准：

- 自动化接口不再把正文写入 `sys_log`。
- 数据盘有至少 30% 可用空间。
- 连续执行一轮高并发任务不再出现 errno 28。

### 阶段 1：新增执行表与并发写模型（代码已完成）

交付内容：

- 新增 execution/case/step/artifact 表及 Mapper、DTO、Service。
- 实现幂等键、状态机、乐观锁和短事务。
- 新增独立 `automation_ui_scene_execution_state`，承载 `latest_execution_id` 和 `execution_revision`。
- 增加结果大小和 base64 拒绝策略。

完成标准：

- 10 个并发回调重复提交时无重复、无丢失。
- 场景执行过程中不再更新 `debug_record/test_record`。
- 单条超大结果被拒绝或文件化。

### 阶段 2：前端和报告查询切换（代码已完成）

交付内容：

- 场景列表改为 Summary DTO。
- 历史、用例、步骤按新接口分页查询。
- revision 使用单调版本号。
- 测试计划和测试报告读取新执行表。
- 保留旧 JSON 只读 fallback。

完成标准：

- 列表 SQL 不读取任何历史大字段。
- 同一秒多次更新不会漏刷新。
- 原 Jenkins 和 Playwright 报告均可展示完整执行详情。

### 阶段 3：旧数据迁移与停止旧写入（代码已完成，生产需运行任务）

交付内容：

- 可重入迁移工具。
- 迁移校验报告。
- 停止写 `debug_record/test_record`。
- 备份并分批清空已迁移旧历史。

完成标准：

- 迁移记录数、批次状态、用例数和步骤数校验一致。
- 新旧页面抽样结果一致。
- 回滚时可以继续读取旧数据或归档文件。

### 阶段 4：生命周期与磁盘保护闭环（代码已完成，生产需配置监控盘）

交付内容：

- `sys_log`、job、Runner 日志、artifact 自动清理。
- binlog 保留策略持久化并验证。
- 磁盘水位保护和分级告警。
- 运维仪表盘和清理失败告警。

完成标准：

- 所有数据类型都有 owner、保留期、清理任务和失败重试。
- 达到 90% 磁盘水位时新任务会被安全拒绝。
- 清理任务连续运行一周无过期数据堆积。

## 15. 验证与验收方案

### 15.1 单元测试

- 状态机合法/非法转换。
- 幂等重复回调。
- 乐观锁冲突重试。
- 历史 DTO 白名单和大小限制。
- base64 拒绝和 artifact 引用保留。
- 日志接口分类和真实路径匹配。
- revision 单调递增。

项目已提供 `-DrunTests=true` 开关；本轮自动化模块 48 项测试通过。MySQL 集成、binlog 事件和真实磁盘水位仍必须在预发布/生产镜像环境执行，不能用离线单测替代。

### 15.2 MySQL 集成测试

使用与生产一致的 MySQL 大版本和 binlog 参数，至少验证：

1. 一个含 1 MiB `case_list` 的场景执行 100 次。
2. 10 个并发客户端重复回传相同和不同用例结果。
3. 单条 10 MiB 异常诊断结果被拒绝或文件化。
4. 执行过程中 `automation_ui_scene` 不更新历史 JSON。
5. 通过 `mysqlbinlog -vv` 抽查 row event 不包含无关大字段。
6. 副本无延迟扩大、无数据不一致。

### 15.3 压测与稳定性测试

建议压测规模：

- 100 个场景。
- 每场景 20 个用例、每用例 50 个步骤。
- 10～20 个并发 Runner。
- 持续执行至少 24 小时，预发布环境建议 72 小时。

量化验收标准：

- 执行结果丢失数为 0。
- 幂等重试重复记录数为 0。
- 场景列表 P95 响应时间不因历史累计明显增长。
- 高频轮询接口在 `sys_log` 中无正文，忽略接口无日志行。
- 相同压测下 binlog 增长量较故障版本下降至少 90%，并与有效结构化结果量近似线性。
- `sys_log` 日增量符合纯元数据预期，并能被保留任务稳定回收。
- artifact 到期删除成功率达到 99.9%，失败项可重试并告警。
- 数据盘与 `/tmp` 峰值使用率低于 70%，或保留不少于 30% 安全余量。
- 全程无 errno 28、锁等待堆积、长事务或明显 JVM GC 抖动。

## 16. 发布、灰度与回滚

### 16.1 发布原则

- 所有表结构变更采用 Liquibase 增量 changeset，不修改已执行 changeset。
- 先加表和字段，再发布新写入，最后切换读取和清理旧字段。
- 使用明确配置开关控制新执行存储和新查询路径，默认值按灰度阶段设置。
- 先单场景、单项目灰度，再扩大到测试计划和定时任务。
- 灰度期间同时采样新旧统计，但避免长期双写完整历史 JSON。

### 16.2 回滚策略

- 新增表和字段不在应用回滚时删除。
- 读取层保留旧 JSON fallback，直到迁移验收完成。
- 新写入出现问题时停止创建新任务，保留新执行表数据供排查。
- 已经 PURGE 的 binlog、删除的日志和过期 artifact 不可通过应用版本回滚恢复，执行前必须确认备份和合规要求。
- 旧 JSON 清空前必须完成备份、迁移校验和回滚演练。

## 17. 最终完成定义

只有同时满足以下条件，才可宣布当前问题根治：

1. 执行历史已从 `automation_ui_scene` 拆出，不再整段更新 `debug_record/test_record`。
2. 并发回调具有幂等键、唯一索引和乐观锁或等价一致性控制。
3. 列表和轮询默认不读取大字段，revision 使用单调版本号。
4. `sys_log` 高频接口策略和自动保留任务已在生产生效。
5. binlog row image、自动过期、复制和备份窗口已经核验并持久化。
6. screenshot、trace、video、HTML 和大诊断全部文件化并有生命周期。
7. Runner job、审计文件和对象存储均有自动清理及失败告警。
8. 旧 `sys_log.ibd`、旧 binlog 和旧执行历史已经安全治理。
9. 磁盘水位保护可以在故障前拒绝新任务。
10. 24～72 小时高并发压测和生产灰度满足量化验收标准。

在以上条件完成前，只能描述为“止血完成”或“根治实施中”，不能以短期磁盘不再增长作为最终验收依据。
