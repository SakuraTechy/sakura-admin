# 测试度量模块重构与上线实施方案

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 方案状态 | 已完成代码实现，待按本文执行生产发布 |
| 适用分支 | 后端、前端 `dev1` |
| 后端仓库 | `sakura-admin` |
| 前端仓库 | `sakura-admin-ui` |
| 数据库 | MySQL 8.x、Liquibase |
| 编制日期 | 2026-08-05 |
| 变更性质 | 加法式数据迁移、写入链路补强、查询口径重构、前端页面重构 |

本文是测试度量 v2 的唯一落地基线。指标口径、数据来源、回填、调度、保留策略和验收均以本文及 `test_metric_v2.sql` 为准。

## 2. 结论

当前模块的问题不是单个页面或 SQL 错误，而是度量对象、历史范围和指标口径没有形成稳定契约：

1. 测试计划和报告缺少明确版本，历史统计依赖可变场景和 JSON 关联。
2. 项目、版本、模块、等级等执行维度没有在执行时固化，场景修改后历史数据会漂移。
3. 通过、失败、跳过、取消和基础设施失败混算，导致通过率不可解释。
4. 场景失败被当作缺陷、执行耗时被换算为节省工时，产生无法审计的价值指标。
5. 日期边界、引擎别名、运行批次去重和查询范围缺少统一规则。
6. 页面以雷达图和大而全的数字为主，无法回答“哪个项目版本、哪个时间段、哪里失败”。

重构后的原则是：执行事实不可变、指标可复算、分子分母可见、异常单独分类、查询范围受控、历史清理必须有聚合水位兜底。

## 3. 建设范围

### 3.1 本期范围

- UI 自动化场景执行，覆盖 Selenium/Jenkins、Playwright Runner、Chrome DevTools Protocol。
- 测试计划、测试报告、报告场景执行范围快照。
- 项目、版本、日期、执行引擎、触发方式、项目环境筛选。
- 概览、趋势、维度分布、失败场景排行和失败详情。
- 日聚合、历史回填、迟到数据重算、执行事实与明细分层保留。
- 旧 `/test/testMetric/overview` 接口兼容。

### 3.2 本期不做

- 不把自动化失败直接等价为缺陷。缺陷指标必须等缺陷系统建立显式关联后另行建设。
- 不根据执行耗时推导节省工时。节省工时必须有人工基线、执行频率和维护成本模型。
- 不提供跨项目全局大盘。v2 查询强制项目必填，避免权限和口径失控。
- 不支持超过 366 天的在线明细查询。长期趋势后续在日汇总对账稳定后切换到聚合表。
- 不回溯虚构历史库存。当前覆盖率分母使用查询时点的当前启用场景。

## 4. 指标口径

### 4.1 时间规则

- 用户传入的 `startDate`、`endDate` 均为自然日且包含边界。
- SQL 统一转换为半开区间 `[startDate 00:00:00, endDate + 1 day 00:00:00)`。
- 执行归属时间为 `COALESCE(finished_at, started_at, create_time)`。
- 默认查询最近 30 天，单次最多 366 天。
- 趋势缺失日期补零，不跳过日期。

### 4.2 终态分类

| 分类 | 判定规则 |
| --- | --- |
| 通过 | `result` 为 `passed`、`14` 或 `全部通过` |
| 功能失败 | `result` 为 `failed`、`15` 或 `不通过`，且不满足基础设施失败 |
| 跳过 | `result` 为 `skipped`、`16` 或 `跳过` |
| 取消 | `result/status` 为 `cancelled`、`canceled` 或 `17` |
| 基础设施失败 | `status` 为 `blocked/interrupted`，或错误码以 `infra/executor/browser/environment/network` 开头 |

基础设施失败优先于功能失败，二者互斥。未知终态可进入分布中的 `OTHER`，不得静默计入通过或功能失败。

### 4.3 核心指标

| 指标 | 公式 | 说明 |
| --- | --- | --- |
| 自动化通过率 | `通过数 / (通过数 + 功能失败数)` | 跳过、取消、基础设施失败不进入分母 |
| 执行覆盖率 | `范围内有终态执行的去重场景 / 当前启用场景` | 分子按 `scene_id` 去重；分母限定当前项目及可选版本 |
| 运行批次 | 终态执行的去重 `run_key` 数 | 报告、批次和单次执行使用统一运行键 |
| 场景执行次数 | 终态执行事实行数 | 不做场景去重 |
| 平均耗时 | 有耗时的终态场景执行 `duration_ms` 平均值 | 不换算节省工时 |
| 失败场景排行 | 按场景聚合功能失败和基础设施失败 | 两类失败分别展示 |
| 维度质量 | `EXACT/INFERRED/MISSING` 执行事实数 | 用于判断统计可信度 |

所有百分比保留两位小数；分母为 0 时返回 `0.00`，同时通过响应中的分子、分母向用户说明无样本，而不是伪装成真实 0%。上期比较使用与当前区间等长、紧邻当前区间之前的自然日窗口，返回百分点变化 `changePoints`。

### 4.4 运行键

运行键按以下优先级生成：

1. 有报告：`REPORT:{testReportId}`。
2. 有批次：`{CANONICAL_ENGINE}:BATCH:{batchId}`。
3. 其他：`EXECUTION:{executionId}`。

引擎规范值为：

| 历史别名 | 规范值 |
| --- | --- |
| `playwright`、`runner`、`playwright_runner`、`PLAYWRIGHT_RUNNER` | `playwright-runner` |
| `cdp`、`chrome_devtools_protocol`、`CHROME_DEVTOOLS_PROTOCOL` | `extension-cdp` |
| `selenium` | `selenium` |
| `jenkins` | `jenkins` |

触发方式统一小写并把下划线转换为连字符，例如 `TEST_PLAN` 规范为 `test-plan`。

## 5. 数据架构

```text
测试计划 test_plan
  -> 执行时创建 test_report
  -> 同事务保存 test_report_scene 不可变范围快照
  -> 执行器写 automation_ui_execution 不可变执行事实和维度快照
  -> v2 API 查询最近 366 天原始事实，保证实时性
  -> SnailJob 按日重算 test_metric_*_daily 与 aggregation_state
  -> 成功聚合水位覆盖后，清理任务才允许删除过期父事实
```

### 5.1 现有表增量字段

| 表 | 新增字段 | 用途 |
| --- | --- | --- |
| `test_plan` | `version_id` | 明确计划所属项目版本 |
| `test_report` | `version_id` | 明确报告所属项目版本 |
| `test_report` | `started_at/finished_at` | 报告真实时间边界 |
| `automation_ui_execution` | `project_id/version_id/module_id/scene_level` | 执行时维度快照 |
| `automation_ui_execution` | `run_key` | 跨场景运行去重 |
| `automation_ui_execution` | `dimension_quality` | 维度可信度 |

`dimension_quality` 迁移默认值为 `MISSING`。新写入链路显式写 `EXACT`；从当前场景定义推断的历史记录写 `INFERRED`；无法关联场景的历史记录保持 `MISSING`。

### 5.2 新表

| 表 | 粒度 | 主键/唯一性 | 用途 |
| --- | --- | --- | --- |
| `test_report_scene` | 报告 × 场景 | `test_report_id + scene_id` 唯一 | 固化场景顺序、名称、模块、等级和定义版本 |
| `test_metric_daily` | 日 × 项目 × 版本 × 引擎 × 触发 × 环境 | 上述全部维度复合主键 | 运行和执行日汇总 |
| `test_metric_scene_daily` | 日 × 项目 × 版本 × 模块 × 场景 × 等级 × 引擎 × 触发 × 环境 | 上述全部维度复合主键 | 场景失败和维度分析汇总 |
| `test_metric_inventory_daily` | 日 × 项目 × 版本 × 模块 × 等级 | 上述全部维度复合主键 | 聚合时点的启用场景库存快照 |
| `test_metric_aggregation_state` | 日 × 项目 × 版本 | 复合主键 | 记录源事实最大 ID、数量、状态和聚合时间 |

场景日汇总主键必须包含 `module_id` 和 `scene_level`，因为执行事实按这两个不可变快照分组。否则同一场景在一天内发生模块或等级变更时会产生主键冲突。

### 5.3 报告范围快照

计划执行前必须完成：

1. 场景 ID 非空、无重复。
2. 场景存在、未删除、已启用。
3. 场景属于当前项目。
4. 所有场景属于同一项目版本，且与计划版本一致。
5. 报告主记录、报告场景快照、场景报告关联和计划运行状态在同一个短事务中提交。
6. 执行器派发在事务外进行；派发失败时报告转为 `FAILED` 并记录完成时间。

历史报告展示和未来范围统计不得重新读取计划 JSON 推断当时执行范围。

## 6. 查询 API

统一权限：`test:testMetric:list`。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /test/metrics/summary` | KPI、分子分母、上期比较、维度质量 |
| `GET /test/metrics/trends` | 按自然日的结果趋势和通过率 |
| `GET /test/metrics/breakdowns` | 结果、引擎、触发、等级、模块分布 |
| `GET /test/metrics/failures` | 失败场景排行及最近错误 |

公共查询参数：

| 参数 | 必填 | 规则 |
| --- | --- | --- |
| `projectId` | 是 | 必须存在且未删除 |
| `versionId` | 否 | 必须属于当前项目 |
| `startDate/endDate` | 否 | ISO 日期，默认最近 30 天，最多 366 天 |
| `executionEngine` | 否 | 接受规范值和历史别名 |
| `triggerType` | 否 | 小写规范化匹配 |
| `environmentId` | 否 | 项目环境 ID |

`breakdowns.dimension` 只允许 `result/engine/trigger/level/module`；`failures.limit` 限制在 1 至 50。所有动态维度均走白名单，不允许把客户端值拼接为任意 SQL 字段。

旧接口 `/test/testMetric/overview` 保留为兼容适配器。旧模型中的缺陷数、缺陷率和节省工时固定返回 0，不继续传播伪指标。前端只调用 v2 API。

## 7. 前端交互

测试度量页面直接进入工作界面，不增加营销式说明页。页面结构如下：

1. 项目必选，版本和项目环境随项目级联加载。
2. 日期、引擎、触发方式、项目环境可组合筛选。
3. 自动化通过率和执行覆盖率显示分子、分母及上期百分点变化。
4. 展示运行批次、场景执行次数、平均耗时、用例和步骤数。
5. 趋势图分别显示通过、功能失败、跳过、取消、基础设施失败和通过率。
6. 结果分布固定展示；第二分布可切换引擎、触发、等级、模块。
7. 失败场景表分列显示功能失败和基础设施失败，详情抽屉显示最近错误。
8. 底部展示精确、推断、缺失维度质量。

报告页面的查询版本列表和编辑版本列表必须独立维护。查询项目变化只刷新查询版本选项，不得清空或污染正在编辑的表单。

## 8. 聚合与回填

### 8.1 SnailJob 任务

| Executor | 建议调度 | 配置 | 说明 |
| --- | --- | --- | --- |
| `AggregateTestMetrics` | 每日 01:10 | `SAKURA_TEST_METRIC_RECOMPUTE_DAYS=3` | 重算今天及最近窗口，吸收迟到回调 |
| `BackfillTestMetrics` | 仅人工触发 | `SAKURA_TEST_METRIC_BACKFILL_DAYS=30` | 首次发布或修复时回填，单次最多 730 天 |
| `CleanupAutomationStorage` | 每日 02:00 | 保留策略见第 9 节 | 必须晚于聚合任务 |

代码中的 `@JobExecutor` 只声明执行器，生产发布仍需在 SnailJob 管理端创建任务、设置集群和告警人。`BackfillTestMetrics` 不配置周期 Cron，防止误触发长期全量回填。

### 8.2 幂等与事务边界

- 单日聚合在一个事务内先删除该日全部聚合记录，再重新插入。
- 同一天可重复执行，结果由当前源事实决定。
- 维度回填单独使用一个短事务。
- 每个自然日聚合使用独立事务；730 天回填不会形成一个超大事务。
- 回填日期校验为闭区间且最多 730 天。
- 默认重算最近 3 天；若回调最长延迟超过 3 天，应提高配置但不超过 30 天。

### 8.3 历史回填规则

1. 计划 JSON 同时兼容数值场景 ID 和字符串场景 ID。
2. 只有当计划内可匹配场景全部落在唯一版本时才回填 `test_plan.version_id`；跨版本歧义保持空值并进入人工治理清单。
3. 报告版本从同项目计划继承。
4. 执行项目、版本、模块、等级从当前场景推断，质量标为 `INFERRED`。
5. 历史运行键按报告、批次、单执行优先级补齐。
6. 引擎和触发别名统一为规范值。
7. 完成维度回填后，再逐日生成聚合和成功水位。

## 9. 数据保留与清理

| 数据 | 默认保留 | 配置 |
| --- | --- | --- |
| 父执行事实 `automation_ui_execution` | 730 天 | `SAKURA_AUTOMATION_EXECUTION_RETENTION_DAYS` |
| Case/Step 明细 | 90 天 | `SAKURA_AUTOMATION_EXECUTION_DETAIL_RETENTION_DAYS` |
| Artifact | 90 天 | 由到期时间和清理任务控制 |
| 日汇总与聚合状态 | 长期保留 | 当前不自动删除 |

父执行事实只有同时满足以下条件才能删除：

1. 已终态且超过保留期。
2. `retention_hold = 0`。
3. 没有未删除 artifact 引用。
4. 同日、同项目、同版本存在 `SUCCESS` 聚合状态。
5. `aggregation_state.source_max_execution_id >= execution.id`。

第 5 条用于防止迟到事实尚未重聚合时被错误清理。清理仍按 5000 条小批次执行，不运行在线 `OPTIMIZE TABLE`。

## 10. 性能、安全与可观测性

### 10.1 查询策略

- v2 在线 API 当前直接查询 `automation_ui_execution`，获得迟到回调后的实时准确结果。
- 查询强制项目、限制 366 天，并使用项目/版本/完成时间及维度组合索引。
- 日汇总当前用于对账、长期保留水位和未来长周期查询，不在对账稳定前替换在线口径。
- 当 P95 超过目标时，先用 `EXPLAIN ANALYZE` 校验索引和扫描行数，再决定按完整日使用聚合、当天使用事实的混合查询。

### 10.2 SLO 与告警

| 指标 | 目标/阈值 | 处理 |
| --- | --- | --- |
| v2 API P95 | 30 天查询小于 800 ms | 连续 10 分钟超阈值告警 |
| v2 API 错误率 | 小于 1% | 5 分钟窗口超阈值告警 |
| 聚合延迟 | 前一日 02:00 前成功 | 未成功则阻断清理并告警 |
| 聚合对账差异 | 源事实数与聚合状态记录一致 | 任一项目版本不一致告警 |
| 维度缺失率 | 新数据 0%；历史数据持续下降 | 新数据出现 `MISSING` 立即告警 |
| 回填失败 | 0 个失败日期 | 记录日期并仅重跑失败日期 |

日志不得输出执行配置正文、凭据或完整错误堆栈中的敏感参数。失败详情 API 最多返回有界错误消息，沿用现有操作日志脱敏和截断策略。

## 11. 发布实施步骤

### 11.1 发布前检查

1. 备份 `test_plan`、`test_report`、`automation_ui_execution` 及相关明细表，确认可恢复时间点。
2. 确认 MySQL 为 8.x，字符集和时区与生产一致。
3. 查询 `project_server_config`、`project_version_config`、`project_module_config` 等项目表是否存在。
4. 在生产同构预发库执行完整 master changelog。
5. 对大表执行索引 DDL 时间和磁盘空间评估，必要时安排维护窗口。
6. 记录迁移前事实数量、最早/最晚时间和各引擎原始值。
7. 确认 SnailJob 服务可用，但暂不启用回填和清理任务。

仓库已通过 `project_config_baseline.sql` 补齐历史功能缺失的项目版本、模块、环境、服务器和数据库配置表，并在 `main_table.sql` 之后、所有业务引用之前加载。服务器和数据库的 `binding_key` 仍由后续 `automation_infrastructure_task.sql` 按升级顺序追加，既支持空库安装，也不改变已有升级库的执行语义。该基线取自现有开发库 `SHOW CREATE TABLE` 并与当前实体字段核对，不能删除或移动到基础设施迁移之后。

### 11.2 数据库发布

1. 执行 Liquibase `update`，确认 master 的 58 个 changeset 和其中 `test_metric_v2.sql` 的 20 个 changeset 全部成功。
2. 校验新增列、5 张新表和索引。
3. 不立即执行 DDL rollback；本次迁移为加法式变更，旧代码可忽略新字段。

### 11.3 后端发布

1. 部署包含执行维度快照、报告范围快照和 v2 API 的后端。
2. 验证旧 overview 接口仍返回成功且伪价值字段为 0。
3. 触发一组 Selenium、Playwright Runner、CDP 执行，检查规范引擎、运行键和 `EXACT` 质量。
4. 观察 30 分钟写入错误后再继续。

### 11.4 回填与聚合

1. 先以 7 天或 30 天窗口人工执行 `BackfillTestMetrics`。
2. 检查事务时长、锁等待、binlog 增长和磁盘余量。
3. 对账通过后分批扩大到需要的历史范围，单批不超过 730 天。
4. 创建并启用每日 `AggregateTestMetrics`。
5. 至少连续观察 3 个聚合周期后再启用父事实清理。

### 11.5 前端发布

1. 发布测试度量新页面。
2. 验证项目、版本、日期、引擎、触发、环境组合筛选。
3. 验证无数据、加载、接口失败、长错误文本和移动端布局。
4. 对比旧页面关键结果，差异必须能由新口径解释。

## 12. 验收 SQL 与检查项

### 12.1 迁移对象

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'test_report_scene', 'test_metric_daily', 'test_metric_scene_daily',
    'test_metric_inventory_daily', 'test_metric_aggregation_state'
  );
```

应返回 5 行。

### 12.2 维度质量

```sql
SELECT dimension_quality, COUNT(*)
FROM automation_ui_execution
GROUP BY dimension_quality;

SELECT COUNT(*) AS invalid_exact_count
FROM automation_ui_execution
WHERE dimension_quality = 'EXACT'
  AND (project_id IS NULL OR version_id IS NULL OR run_key IS NULL OR run_key = '');
```

上线后的新数据 `invalid_exact_count` 必须为 0。历史无法推断记录应为 `MISSING`，不得伪装为 `EXACT`。

### 12.3 引擎规范化

```sql
SELECT execution_engine, COUNT(*)
FROM automation_ui_execution
GROUP BY execution_engine
ORDER BY COUNT(*) DESC;
```

回填后不应继续出现 `PLAYWRIGHT_RUNNER`、`playwright_runner`、`CHROME_DEVTOOLS_PROTOCOL` 等别名。

### 12.4 聚合状态

```sql
SELECT metric_date, project_id, version_id, status,
       source_execution_count, aggregated_execution_count,
       source_max_execution_id, aggregation_time
FROM test_metric_aggregation_state
WHERE metric_date >= CURRENT_DATE - INTERVAL 7 DAY
ORDER BY metric_date DESC, project_id, version_id;
```

所有应聚合项目版本必须为 `SUCCESS`，两类数量相等，最大执行 ID 非空。

### 12.5 清理水位

```sql
SELECT COUNT(*) AS unsafe_cleanup_candidates
FROM automation_ui_execution e
WHERE e.finished_at < CURRENT_TIMESTAMP - INTERVAL 730 DAY
  AND NOT EXISTS (
    SELECT 1
    FROM test_metric_aggregation_state a
    WHERE a.metric_date = DATE(COALESCE(e.finished_at, e.started_at, e.create_time))
      AND a.project_id = e.project_id
      AND a.version_id = COALESCE(e.version_id, 0)
      AND a.status = 'SUCCESS'
      AND a.source_max_execution_id >= e.id
  );
```

该查询允许返回历史待治理记录，但清理任务绝不能删除这些记录。

### 12.6 API 验收

- 项目缺失返回参数错误。
- 跨项目版本返回业务错误。
- 起止日期反转、超过 366 天返回业务错误。
- 结束日 23:59:59 的记录被包含，次日 00:00:00 不被包含。
- `PLAYWRIGHT_RUNNER` 与 `playwright-runner` 查询结果一致。
- 通过率分母不含跳过、取消、基础设施失败。
- 趋势日期连续，无数据日期为 0。
- 非白名单 breakdown 维度被拒绝。
- 失败排行最多返回 50 条，功能失败和基础设施失败分开。

## 13. 回滚与故障处理

### 13.1 应用回滚

1. 先禁用 `AggregateTestMetrics`、`BackfillTestMetrics` 和父事实清理。
2. 回滚前端到旧页面。
3. 回滚后端到旧版本；新增表和字段保留，不影响旧代码。
4. 恢复期间继续保留执行事实，不执行删除。

### 13.2 数据库处理

- 生产故障期间不要直接执行迁移文件中的 `DROP TABLE/DROP COLUMN` rollback。
- 新增对象是加法式的，应用回滚时保留它们是风险最低的策略。
- 聚合错误按自然日删除后重算，不手工修改汇总数字。
- 回填错误先停任务、修复规则、从失败日期重跑。
- 只有确认不再回滚应用且完成数据备份后，才能在独立变更单中考虑清理废弃对象。

## 14. 测试与完成标准

### 14.1 自动化测试

- 后端 JDK 17 Reactor 构建成功。
- `continew-automation` 94 个单元测试全部通过。
- `continew-test` 23 个单元测试全部通过。
- 后端合计 117 个测试，失败、错误、跳过均为 0。
- 前端 `pnpm typecheck` 通过。
- 本次修改的 3 个测试 API 文件和测试度量、测试计划、测试报告 3 个页面定向 ESLint 通过。
- 前端 `pnpm build` 通过，4489 个模块完成生产打包。
- 两个仓库 `git diff --check` 通过。
- 浏览器实测 1920×1080 和 390×844 视口无页面内容重叠、横向溢出或核心文本截断。
- 3 个 ECharts 画布在桌面和移动视口均有非空像素，趋势和分布图不是空白画布。

### 14.2 数据库验证

已在一次性 MySQL 8.4.11 中完成以下验证：

- 全新空库从 `db.changelog-master.yaml` 首项开始执行，58 个 changeset 全部成功，未标记跳过。
- 同一空库再次执行 master，新增执行 0 个、已执行 58 个，证明迁移幂等。
- 5 张项目配置基线表全部创建成功，后续基础设施迁移正确追加两列 `binding_key` 及 `(project_id, binding_key)` 唯一索引。
- `test_metric_v2.sql` 20 个 changeset 全部执行成功。
- 数值 `1001` 和字符串 `"1002"` 两种 JSON 场景 ID 可在同一计划中匹配并回填唯一版本。
- 包含两个版本场景的计划保持 `version_id = NULL`，不做错误推断。
- 同一场景、同一天、不同模块和等级的两条日汇总可以同时写入。
- `PLAYWRIGHT_RUNNER/TEST_PLAN` 可规范为 `playwright-runner/test-plan`。
- 历史执行维度成功推断并标为 `INFERRED`。
- `test_metric_scene_daily` 主键包含 `module_id` 和 `scene_level`，完整主键结构已在空库中核验。

### 14.3 业务完成标准

满足以下全部条件才可宣布上线完成：

1. 新执行维度快照完整率为 100%。
2. 最近 30 天源事实与 v2 API 抽样对账无不可解释差异。
3. 最近 7 天聚合状态连续成功。
4. 清理水位校验生效，未聚合或迟到事实不会被删除。
5. 主要页面在 1920×1080、1440×900、390×844 下无重叠、截断和空白图表。
6. API P95、错误率达到第 10.2 节目标。
7. 旧伪指标不再出现在新页面、导出或管理汇报中。

## 15. 实现位置

| 内容 | 位置 |
| --- | --- |
| 项目配置空库基线 | `continew-webapi/src/main/resources/db/changelog/mysql/project_config_baseline.sql` |
| Liquibase 度量迁移 | `continew-webapi/src/main/resources/db/changelog/mysql/test_metric_v2.sql` |
| v2 Controller | `continew-test/.../controller/TestMetricV2Controller.java` |
| 查询服务 | `continew-test/.../service/impl/TestMetricQueryServiceImpl.java` |
| 聚合与回填 | `continew-test/.../service/impl/TestMetricAggregationServiceImpl.java` |
| SnailJob | `continew-test/.../job/TestMetricAggregationJob.java` |
| 报告范围快照 | `continew-test/.../service/impl/TestReportSceneSnapshotService.java` |
| 执行事实写入 | `continew-automation/.../AutomationUiExecutionRecordServiceImpl.java` |
| 清理水位 | `continew-webapi/.../AutomationStorageCleanupJob.java` |
| 前端 API | `sakura-admin-ui/src/apis/test/testMetric.ts` |
| 前端页面 | `sakura-admin-ui/src/views/test/testMetric/index.vue` |

## 16. 责任分工建议

| 角色 | 责任 |
| --- | --- |
| 后端 | 写入契约、API、聚合、回填和兼容接口 |
| 前端 | 筛选联动、指标解释、图表和异常状态 |
| DBA | 备份、DDL 窗口、索引评估、对账和容量监控 |
| 测试 | 口径样本、边界日期、别名、权限、兼容和布局验收 |
| 运维 | SnailJob 配置、告警、灰度、任务停启和回滚演练 |
| 产品/质量负责人 | 确认指标定义，禁止重新引入未经数据关联的缺陷和节省工时指标 |
