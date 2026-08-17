# 测试度量模块重构与上线实施方案

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 方案状态 | 代码与本地验收已完成，待执行预发数据对账和生产发布 |
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
4. 旧版的缺陷数和人力指标都是可计算指标，不是“伪指标”；问题在于名称和边界不够严谨：当前缺陷数的数据源是功能失败场景次数，人力值是场景执行次数除以 70 场景/人天得到的标准人工工作量估算。页面必须保留这两个业务指标，同时披露来源、公式、单位和边界，避免被误解为缺陷系统去重单量或已经核销的实际净节省。
5. 日期边界、引擎别名、运行批次去重和查询范围缺少统一规则。
6. 页面以雷达图和大而全的数字为主，无法回答“哪个项目版本、哪个时间段、哪里失败”。

重构后的原则是：执行事实不可变、指标可复算、分子分母可见、异常单独分类、查询范围受控、历史清理必须有聚合水位兜底。

## 3. 建设范围

### 3.1 本期范围

- UI 自动化场景执行，覆盖 Selenium/Jenkins、Playwright Runner、Chrome DevTools Protocol。
- 测试计划、测试报告、报告场景执行范围快照。
- 项目、版本、日期、执行引擎、触发方式、项目环境筛选。
- 模块和场景资产概览、核心 KPI、执行效能、质量信号、趋势、维度分布、计划与定时任务概览、失败场景排行和失败详情。
- 日聚合、历史回填、迟到数据重算、执行事实与明细分层保留。
- 旧 `/test/testMetric/overview` 接口兼容。

### 3.2 本期不做

- 不把失败场景次数直接命名为缺陷单数量；本期保留与旧版兼容的失败场景数，并在页面标明统计来源和边界。
- 不把标准人力估算宣称为实际节省工时；实际净节省仍需人工基线、执行频率和维护成本模型，本期展示场景执行次数除以 70 场景/人天的可复算估算。
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

所有终态必须且只能进入一个分类，固定优先级为：`CANCELLED > INFRA_FAILED > PASSED > FAILED > SKIPPED > OTHER`。取消优先于基础设施诊断，基础设施失败优先于结果文本；未知终态进入 `OTHER`，不得静默丢失或重复计数。必须满足：

`sceneExecutionCount = passCount + failCount + skipCount + cancelCount + infraFailCount + otherCount`

### 4.3 核心指标

| 指标 | 公式 | 说明 |
| --- | --- | --- |
| 自动化通过率 | `通过数 / (通过数 + 功能失败数)` | 跳过、取消、基础设施失败不进入分母 |
| 执行覆盖率 | `范围内有终态执行的去重场景 / 当前启用场景` | 分子按 `scene_id` 去重；分母限定当前项目及可选版本 |
| 运行批次 | 终态执行的去重 `run_key` 数 | 报告、批次和单次执行使用统一运行键 |
| 场景执行次数 | 终态执行事实行数 | 不做场景去重 |
| 平均耗时 | 有耗时的终态场景执行 `duration_ms` 平均值 | 用于执行效率计算，不直接等同实际节省工时 |
| 失败场景排行 | 按场景聚合功能失败和基础设施失败 | 两类失败分别展示 |
| 维度质量 | `EXACT/INFERRED/MISSING` 执行事实数 | 用于判断统计可信度 |

所有百分比保留两位小数；分母为 0 时 API 返回 `0.00` 并同时返回分子、分母，前端显示 `--` 和“暂无对比”，避免把无样本伪装成真实 0%。分母有效时（例如 `0/2` 的执行覆盖率）仍显示真实的 `0.00%`。上期比较使用与当前区间等长、紧邻当前区间之前的自然日窗口，返回百分点变化 `changePoints`。

#### 4.3.1 兼容价值指标

以下指标用于兼容旧版测试报告的展示口径，均由执行事实复算，并在页面显示公式和估算边界：

| 指标 | 公式 | 口径边界 |
| --- | --- | --- |
| 缺陷数（失败场景次数） | `功能失败场景次数`，等价于旧版 `sceneFail` 汇总 | 是可计算的缺陷信号指标；未关联缺陷系统时表示失败发生次数，不等同唯一缺陷单；基础设施失败单独统计 |
| 失败场景率 | `功能失败场景次数 / 场景执行次数` | 用于观察失败信号，不代表缺陷率或缺陷密度 |
| 标准人工工作量估算 | `场景执行次数 / baselineScenesPerPersonDay` | 默认全局基线为 `70 场景/人天`，计算结果单位为`人天`；例如 `58 / 70 = 0.83 人天`。该值可作为理论节省量参考，但不等同已核销的实际净节省，未扣除维护成本 |
| 自动化执行效率 | `场景执行次数 / (总耗时毫秒 / 3,600,000)` | 单位为个/小时；无有效耗时时显示 `--` |

趋势中的近 7 天和日均价值指标只使用 `trends.points` 派生。后端若未来提供缺陷系统关联数或经过确认的人工基线，可在不改变执行事实的前提下增加独立指标，不覆盖上述兼容口径。

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
| `automation_ui_execution` | `project_id/version_id/module_id/scene_level` | 执行时维度快照；模块缺失写 `0`，等级缺失写 `UNSPECIFIED` |
| `automation_ui_execution` | `run_key` | 跨场景运行去重 |
| `automation_ui_execution` | `dimension_quality` | 维度可信度 |
| `automation_ui_execution` | `metric_time` | `COALESCE(finished_at, started_at, create_time)` 的 STORED 生成列，用于范围查询和清理 |

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
7. 同一报告一旦存在场景快照，任何重试都不得删除或覆盖；调用方必须复用原快照或创建新报告。

历史报告展示和未来范围统计不得重新读取计划 JSON 推断当时执行范围。

## 6. 查询 API

统一功能权限：`test:testMetric:list`。数据范围在服务层二次校验：超级管理员、项目创建人或 `project_config.member` 中列出的项目成员可以访问；其他用户返回“无权访问当前项目的测试度量”。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /test/metrics/summary` | KPI、分子分母、上期比较、维度质量 |
| `GET /test/metrics/trends` | 按自然日的结果趋势和通过率 |
| `GET /test/metrics/breakdowns` | 结果、引擎、触发、等级、模块分布 |
| `GET /test/metrics/failures` | 失败场景排行及最近错误 |

`summary` 除既有字段外返回 `otherCount`、`durationTotalMs`、`durationSampleCount`。`trends.points` 同时返回场景执行发生次数 `sceneExecutionCount`、当日去重场景数 `executedSceneCount`、六类终态、耗时总量和耗时样本数。前端不得再用旧字段 `executedCount` 代替这两个不同口径。

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

核心执行指标只读取 v2 API。页面的资产与编排概览复用现有只读接口，不把这些数据并入度量事实：

| 接口 | 页面用途 | 时间口径 |
| --- | --- | --- |
| `GET /project/projectModuleConfig/list` | 当前版本启用模块库存 | 查询时点快照 |
| `GET /automation/automationUiScene/list` | 当前版本启用场景及等级库存 | 查询时点快照 |
| `GET /test/testPlan` | 当前项目最近更新的测试计划 | 当前业务状态 |
| `GET /test/timedTask` | 当前项目启用中的定时任务 | 当前调度状态 |

辅助接口必须按当前用户原有权限校验。核心与辅助请求使用部分成功策略：单个接口失败时清空对应旧值并局部显示空状态，不能阻断其他成功区域；请求序号保护必须阻止项目快速切换或维度连续切换时的旧响应覆盖新结果。旧接口 `/test/testMetric/overview` 保留为兼容适配器；旧模型字段继续兼容，但不再固定返回无意义的 0：`discoveredDefectCount = summary.failCount`、`savedManHours = summary.sceneExecutionCount / 70`、`defectRate = summary.failCount / summary.sceneExecutionCount`、`automationExecuteRate = summary.sceneExecutionCount / (summary.durationTotalMs / 3,600,000)`。v2 页面依据 `summary.failCount`、`summary.sceneExecutionCount`、`summary.durationTotalMs`、`summary.durationSampleCount` 和全局常量 `70 场景/人天` 按本方案公式派生价值指标。旧字段名称中的“缺陷”仍表示失败场景次数，“savedManHours”仍表示标准人工工作量估算，不等同缺陷系统唯一单量或已核销实际净节省。

## 7. 前端交互

测试度量页面直接进入工作界面，不增加营销式说明页。信息从“有什么可以测”逐步下钻到“执行得怎样、哪里需要处理”，避免只展示一组孤立 KPI。

### 7.1 页面信息架构

| 层级 | 回答的问题 | 展示内容 | 数据来源 |
| --- | --- | --- | --- |
| 查询范围 | 正在看哪个项目、版本和时间段 | 项目、版本、日期、引擎、触发、环境 | 项目配置 + 用户筛选 |
| 测试资产 | 当前版本有什么可以测 | 模块总数及模块场景分布、场景总数及等级分布 | 模块、场景只读接口 |
| 核心质量 | 当前自动化整体表现如何 | 通过率、执行覆盖率、运行批次、平均耗时 | summary |
| 终态结果 | 240 次执行分别是什么结果 | 通过、功能失败、跳过、取消、基础设施失败、其他 | summary + result breakdown |
| 执行效能 | 执行是否持续、密度如何 | 范围批次、近 7 天批次、活跃天数、日均执行 | summary + trends 前端派生 |
| 质量信号 | 样本是否有效、失败性质如何 | 有效结果占比、用例通过率、步骤通过率、基础设施失败占比 | summary 前端派生 |
| 变化趋势 | 质量是在改善还是恶化 | 每日终态结果和通过率双轴趋势 | trends |
| 维度定位 | 问题集中在哪类执行 | 结果分布及引擎、触发、等级、模块切换分布 | breakdowns |
| 执行编排 | 接下来会执行什么 | 最近测试计划、启用中的定时任务、进度、周期、引擎、环境和最近结果 | 计划、任务只读接口 |
| 失败治理 | 具体哪里失败 | 失败场景排行、功能/基础设施失败分列、最近错误详情 | failures |
| 数据可信度 | 指标是否可以信任 | 精确、推断、缺失维度数量 | summary |

### 7.2 交互规则

1. 项目必选，版本和项目环境随项目级联加载；项目切换时清理不再适用的引擎、触发和环境条件。
2. 日期、引擎、触发方式、项目环境可组合筛选核心执行指标；点击查询后统一刷新 summary、trends、breakdowns 和 failures。
3. 资产概览跟随项目和版本，不跟随日期、引擎、触发、环境筛选，因为它表达的是查询时点的当前启用库存。
4. 功能模块资产不把层级模块压缩成长图例：左侧保留模块总量环图，右侧使用可展开模块树，节点显示当前节点及子模块汇总场景数；树区域固定高度、内部滚动，模块超过 8 个时提供搜索。测试场景资产继续按 P0/P1/P2/P3 等等级使用环图展示。
5. 测试计划跟随项目和可选版本；定时任务跟随项目并只展示 `ENABLED`。二者是当前编排状态，不伪装成所选历史日期的快照。
6. 自动化通过率和执行覆盖率必须显示分子、分母及上期百分点变化。
7. 展示运行批次、场景执行次数、平均耗时、用例和步骤数；近 7 天与日均值只从已返回趋势派生，不另造统计口径。
8. 有效结果占比为 `(通过 + 功能失败) / 场景执行数`；用例和步骤通过率排除各自失败以外的跳过样本；基础设施失败占比为 `基础设施失败 / 场景执行数`。
9. 趋势图分别显示通过、功能失败、跳过、取消、基础设施失败、其他和通过率。
10. 结果分布固定展示；第二分布可切换引擎、触发、等级、模块，切换只刷新对应分布。
11. 测试计划表展示名称、类型、负责人、已执行/场景数、进度、状态和计划周期；定时任务表展示名称、关联计划、执行周期、引擎、环境、下次执行、最近结果和启用状态。
12. 计划和任务区提供“查看全部”入口，跳转到既有管理页面；度量页不复制编辑、启停、立即执行等管理能力。
13. 失败场景表分列显示功能失败和基础设施失败，详情抽屉显示最近错误；页面明确失败场景与缺陷单的边界，不把失败次数伪装成唯一缺陷单。
14. 自动化价值产出区显示缺陷数（失败场景次数）、失败场景率、标准人工工作量估算和执行效率，并提供公式、70 场景/人天全局基线及“估算不等同已核销实际净节省”的说明。
15. 所有统计指标统一提供问号说明入口；说明至少明确数据来源或统计对象、计算公式、分子与分母、排除项及指标边界。资产库存、核心 KPI、六类终态、执行效能、质量信号、自动化价值、趋势、分布、执行编排、失败排行和维度质量均须覆盖，不能只为少数核心卡片提供口径说明。
16. 底部展示精确、推断、缺失维度质量。
17. 当前范围无执行数据时仍展示资产库存、核心 KPI、效能与质量信号以及计划/任务；执行趋势、分布、失败排行和维度质量改为紧凑空状态。
18. 单个资产或编排辅助接口失败时局部降级为空状态，不清空已经成功返回的核心度量区域。

### 7.3 本地演示数据

开发环境提供 `sakura-admin-ui/src/views/test/testMetric/testMetricDemoData.json`，用于在不写数据库的前提下完整验收页面。JSON 必须包含项目、版本、环境、模块、库存、summary、30 天 trends、全部 breakdowns、测试计划、定时任务和失败排行，并满足以下约束：

- `databaseWriteRequired` 固定为 `false`，演示项目 ID 固定为 `metric-demo-local`。
- 模块库存分项之和等于模块关联场景总数，等级库存分项之和等于场景总数。
- result、engine、trigger、level、module 五类执行分布总数分别等于 `sceneExecutionCount`。
- summary 和每个趋势点均满足六类终态之和等于 `sceneExecutionCount`；30 天趋势的执行次数、耗时总量和耗时样本数分别与 summary 对账。
- 演示项目只在 `import.meta.env.DEV` 下加入项目选项；真实项目继续使用后端接口。
- 演示数据是一个固定聚合快照，不支持按日期、引擎、触发和环境重算，因此这些筛选控件在本地项目下只读。
- `valueMetrics.baselineScenesPerPersonDay` 固定为 `70`，用于复算标准人力估算；缺陷指标定义字段必须说明其统计的是失败场景次数而非唯一缺陷单。

### 7.4 响应式与可用性

1. 页面在主布局固定高度内容区内独立纵向滚动，页面和文档根节点禁止横向溢出。
2. 资产、效能、质量和分布区在宽屏双列展示，在 `900px` 以下改为单列；KPI 在平板两列、手机单列。
3. 表格允许自身横向滚动，固定宽度不能撑开页面；长名称、错误文本和 Cron 表达式采用截断或上下两行展示。
4. ECharts 必须使用稳定高度和 autoresize；移动端环图图例移到底部，不能覆盖中心数值。
5. 模块或场景库存为 0 时显示明确空状态，不创建空白环图。
6. 自动化价值区在 `900px` 以上使用两个等宽主指标列，执行效率、失败场景率和基线组成的指标条横跨整行；`900px` 以下主指标改为单列，`520px` 以下底部指标条也改为单列。长耗时和样本说明允许换行，不得截断或撑宽页面。
7. 测试度量页使用 `Segoe UI / Microsoft YaHei UI / Microsoft YaHei / sans-serif` 本地字体栈，不依赖 13 MiB 的 PingFang WebFont；ECharts 与 DOM 使用相同字体栈，避免图表和卡片中文字形不一致。
8. 页面标题使用 `20px/600/28px`，分区标题使用 `15px/600/22px`，正文和表格使用 `13px/400/20px`，辅助说明使用 `12px/400/18px`，核心数字使用 `600` 字重。所有数值启用等宽数字，字距固定为 0；表头最多使用 `500`，不得让表格正文继承浏览器默认 `bolder`。

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
- 查询强制项目、限制 366 天，并使用项目/版本/`metric_time` 组合索引。
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

1. 执行 Liquibase `update`，确认当前 master 引用的 88 个 SQL changeset 和其中 `test_metric_v2.sql` 的 25 个 changeset 全部成功；master 中每个迁移文件必须使用独立 `include`，禁止重复 `file` 键。
2. 校验新增列、5 张新表、`metric_time` 生成列、`scene_other_count/other_count` 和两个范围索引。
3. 不立即执行 DDL rollback；本次迁移为加法式变更，旧代码可忽略新字段。

### 11.3 后端发布

1. 部署包含执行维度快照、报告范围快照和 v2 API 的后端。
2. 验证旧 overview 接口仍返回成功且旧字段与 v2 summary 按公式一致；同时用 v2 summary/trends 复算失败场景数、失败场景率、标准人力估算和执行效率。
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
3. 验证模块/场景库存与当前版本启用数据一致，库存分项合计无差异。
4. 验证计划和启用任务的项目隔离、跳转入口及辅助接口局部降级。
5. 验证无数据、加载、接口失败、长错误文本和移动端布局。
6. 对比旧页面关键结果，差异必须能由新口径解释；兼容价值指标必须有来源、基线、公式和估算边界，不得展示无来源的缺陷单数量或实际节省工时。

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
WHERE e.metric_time < CURRENT_TIMESTAMP - INTERVAL 730 DAY
  AND NOT EXISTS (
    SELECT 1
    FROM test_metric_aggregation_state a
    WHERE a.metric_date = DATE(e.metric_time)
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
- 六类终态严格互斥且总和等于 `sceneExecutionCount`；当状态和结果冲突时按 `CANCELLED > INFRA_FAILED > PASSED > FAILED > SKIPPED > OTHER` 归类。
- 趋势日期连续，无数据日期为 0。
- 趋势同时区分执行发生次数和去重场景数，且耗时总量、样本数与 summary 对账。
- 非白名单 breakdown 维度被拒绝。
- 失败排行最多返回 50 条，功能失败和基础设施失败分开。
- 非管理员、非创建人且不在项目成员列表中的用户无法读取项目度量。

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
- `continew-automation` 178 个单元测试全部通过。
- `continew-test` 69 个单元测试全部通过。
- 两个相关模块合计 247 个测试，失败、错误、跳过均为 0；其 Reactor 依赖模块测试也通过。
- 前端 `pnpm typecheck` 通过。
- 测试度量 API、页面及布局验收脚本定向 ESLint 通过。
- 前端 `pnpm build` 通过；Sass `@import`、字体运行时解析和大 chunk 为仓库既有警告，不影响本次构建成功。
- 测试度量相关前后端代码及本文档 `git diff --check` 通过；仓库内其他并行开发变更应由各自任务独立验收。
- 已使用 `scripts/verify-test-metric-layout.cjs` 和真实 Chrome 分别实测完整本地 JSON 的 1920×1080、1674×870、1440×900、1024×900、390×844。五个视口均无文档或度量内容区横向溢出，价值区栅格符合断点规则，可滚动到最后的维度质量区域。
- 浏览器验收确认功能模块资产区域始终存在模块层级树；树节点可承载父子层级和场景汇总数，树区域不改变资产卡片固定高度。
- 浏览器验收同时读取实际计算样式，确认页面字体栈包含 `Segoe UI/Microsoft YaHei`、页面标题为 `20px/600`、分区标题为 `15px/600`、KPI 数字为 `600`、表格正文为 `13px/400/20px`；每个视口分别保存页面顶部和价值区截图。
- 无执行数据时不创建趋势和执行分布图；无库存时不创建资产环图；通过率和平均耗时无样本时显示 `--`。
- 完整本地 JSON 在五个视口均检测到 5 个非空 ECharts 画布（2 个资产环图、1 个趋势图、2 个执行分布图）。
- 完整本地 JSON 在五个视口均检测到 36 个指标说明入口，覆盖资产、核心 KPI、六类终态、执行效能、质量信号、自动化价值、趋势、分布、编排、失败排行和维度质量；布局脚本中的 `metricHelpCoverage` 验收全部通过。
- 本地 JSON 通过运行时结构校验：`databaseWriteRequired=false`、4 个模块、12 个场景、30 个趋势点、3 个测试计划、3 个启用任务、10 条失败排行；六类终态、趋势执行次数、耗时和五类执行分布均与 summary 对账。

### 14.2 数据库验证

已在一次性 MySQL 8.4.11 中完成以下验证：

- 历史 20 个度量 changeset 曾在一次性 MySQL 8.4.11 空库中完成迁移与二次幂等验证；本次新增 5 个 changeset 后，必须按 11.2 节在预发同构库重新执行完整 master。
- 5 张项目配置基线表全部创建成功，后续基础设施迁移正确追加两列 `binding_key` 及 `(project_id, binding_key)` 唯一索引。
- 当前 `test_metric_v2.sql` 共 25 个唯一 changeset；已消除重复 changeset ID，并修复 master 中度量迁移与执行器注册迁移共用一个 `include` 的错误。
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
5. 主要页面在 1674×870、1440×900、1024×900、390×844 下无重叠、截断、横向溢出和空白图表。
6. API P95、错误率达到第 10.2 节目标。
7. 无来源的缺陷单数量和实际节省工时不出现在新页面、导出或管理汇报中；兼容失败场景数和标准人力估算必须同时展示来源、基线、公式及边界说明。
8. 模块/场景库存、测试计划和启用任务与各自管理页面在相同项目版本条件下抽样一致。
9. 任一辅助接口失败不会阻断核心 KPI、趋势、分布和失败排行展示。

## 15. 实现位置

| 内容 | 位置 |
| --- | --- |
| 项目配置空库基线 | `continew-webapi/src/main/resources/db/changelog/mysql/project_config_baseline.sql` |
| Liquibase 度量迁移 | `continew-webapi/src/main/resources/db/changelog/mysql/test_metric_v2.sql` |
| v2 Controller | `continew-test/.../controller/TestMetricV2Controller.java` |
| 查询服务 | `continew-test/.../service/impl/TestMetricQueryServiceImpl.java` |
| 共享终态分类 | `continew-test/.../service/impl/TestMetricSqlExpressions.java` |
| 聚合与回填 | `continew-test/.../service/impl/TestMetricAggregationServiceImpl.java` |
| SnailJob | `continew-test/.../job/TestMetricAggregationJob.java` |
| 报告范围快照 | `continew-test/.../service/impl/TestReportSceneSnapshotService.java` |
| 执行事实写入 | `continew-automation/.../AutomationUiExecutionRecordServiceImpl.java` |
| 清理水位 | `continew-webapi/.../AutomationStorageCleanupJob.java` |
| 前端 API | `sakura-admin-ui/src/apis/test/testMetric.ts` |
| 前端页面 | `sakura-admin-ui/src/views/test/testMetric/index.vue` |
| 本地完整演示数据 | `sakura-admin-ui/src/views/test/testMetric/testMetricDemoData.json` |
| 浏览器布局验收 | `sakura-admin-ui/scripts/verify-test-metric-layout.cjs` |

## 16. 责任分工建议

| 角色 | 责任 |
| --- | --- |
| 后端 | 写入契约、API、聚合、回填和兼容接口 |
| 前端 | 筛选联动、资产与编排概览、指标解释、图表、局部降级和响应式布局 |
| DBA | 备份、DDL 窗口、索引评估、对账和容量监控 |
| 测试 | 口径样本、边界日期、别名、权限、兼容和布局验收 |
| 运维 | SnailJob 配置、告警、灰度、任务停启和回滚演练 |
| 产品/质量负责人 | 确认失败场景与缺陷单边界，确认 70 场景/人天基线及估算说明，审核实际人力节省指标的新增条件 |
