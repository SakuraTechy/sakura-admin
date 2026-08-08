# 测试计划定时任务模块现状分析与修复方案

## 1. 文档信息

- 分析日期：2026-08-05
- 分析范围：测试域定时任务模块 `/test/timedTask`
- 关联能力：SnailJob 调度中心、测试计划执行、Selenium、Playwright Runner、邮件通知
- 代码仓库：`sakura-admin`、`sakura-admin-ui`
- 本文目标：判断当前功能完成度，并给出可实施、可验证的修复方案

## 2. 原始分析结论（2026-08-05）

分析时，模块已经实现主要页面和业务代码，但尚未达到生产可用标准。

已实现的主链路包括：

- 定时任务列表、创建、编辑、删除、导出和启停。
- Cron 快捷配置、自定义表达式和未来执行时间预览。
- 手动触发和 SnailJob Cron 触发。
- 测试计划执行、业务执行记录、调度日志和结果链接。
- 禁止重叠执行或允许并行执行。
- 执行完成后的多邮箱通知。
- Selenium 和 Playwright Runner 的后端执行模型。

分析时尚未完成的关键闭环包括：

- 当前开发运行环境没有启动或启用调度服务，Cron 无法执行。
- Docker 默认配置无法从 WebAPI 容器访问调度中心管理 API。
- 删除测试计划时可能遗留仍在运行的调度任务。
- 已删除、已禁用任务的在途调度指令缺少执行前防护。
- `CANCELLED` 状态不能使定时任务运行记录进入终态。
- 本地数据库和远程调度中心之间没有可靠的一致性与对账机制。
- 前端不能配置 Playwright Runner 定时执行。
- 定时任务核心服务缺少单元测试、集成测试和端到端测试。

因此，分析时模块应判定为“主体实现完成，生产闭环未完成”，不能按已完成状态验收。修复后的最新判定见第 10 节。

## 3. 当前实现结构

```text
定时任务页面
    -> TestTimedTaskController
    -> TestTimedTaskServiceImpl
       -> test_timed_task
       -> SnailJob JobService / JobClient
          -> schedule-server
             -> ExecuteTestPlanJob
                -> TestPlanService.execute
                   -> Selenium 或 Playwright Runner
                   -> test_report
                -> test_timed_task_run
                -> 邮件通知
```

主要代码位置：

- 前端页面：`sakura-admin-ui/src/views/test/timedTask/`
- 前端接口：`sakura-admin-ui/src/apis/test/timedTask.ts`
- 控制器：`continew-test/src/main/java/top/continew/admin/test/controller/TestTimedTaskController.java`
- 任务服务：`continew-test/src/main/java/top/continew/admin/test/service/impl/TestTimedTaskServiceImpl.java`
- 运行记录服务：`continew-test/src/main/java/top/continew/admin/test/service/impl/TestTimedTaskRunServiceImpl.java`
- 调度执行器：`continew-test/src/main/java/top/continew/admin/test/job/TestPlanJobExecutor.java`
- 调度客户端：`continew-plugin/continew-plugin-schedule/src/main/java/top/continew/admin/schedule/api/JobClient.java`
- 数据库变更：`continew-webapi/src/main/resources/db/changelog/mysql/test_table.sql`
- 运行记录变更：`continew-webapi/src/main/resources/db/changelog/mysql/test_schedule_v2.sql`

## 4. 验证结果

本次分析执行了以下验证：

| 验证项 | 结果 | 说明 |
| --- | --- | --- |
| 前端 TypeScript 检查 | 通过 | `pnpm typecheck` |
| 定时任务前端文件 ESLint | 通过 | API 和 3 个定时任务页面文件无错误 |
| 后端编译 | 通过 | JDK 17 下相关 Reactor 模块编译成功 |
| 后端测试 | 通过 | 依赖模块 150 条、`continew-test` 25 条，共 175 条 |
| 定时任务专属测试 | 不足 | 仅有 `TestPlanJobExecutorTest` 的 3 条执行器测试 |
| 当前 WebAPI | 已运行 | 监听 `8000` |
| 当前前端 | 已运行 | 监听 `5173` |
| 当前调度 HTTP 服务 | 未运行 | `8001` 未监听 |
| 当前调度 Netty 服务 | 未运行 | `1788` 未监听 |
| 当前 SnailJob 客户端 | 未启用 | `1789` 未监听 |

测试通过只能证明已有断言和编译基线正常，不能覆盖下文列出的跨系统一致性和运行环境问题。

## 5. 问题清单

### 5.1 P0：调度运行环境没有形成闭环

现状：

- `application-dev.yml` 明确配置 `snail-job.enabled: false`。
- 当前机器没有进程监听 `8001`、`1788` 和 `1789`。
- 创建、修改、启停任务需要调用调度中心管理 API。
- Cron 执行要求 WebAPI 注册 `ExecuteTestPlanJob` 执行器。

影响：

- 当前开发实例无法自动执行定时任务。
- 调度中心不可达时，任务创建、修改或启停可能直接失败。
- 页面存在不代表功能在当前环境可用。

### 5.2 P0：Docker 调度地址配置错误

现状：

- schedule-server 生产端口为 `18001`，并运行在独立容器。
- WebAPI 的调度 API 地址硬编码为 `http://127.0.0.1:8001/snail-job`。
- compose 中 `SCHEDULE_HOST` 指向 `172.17.0.1`，而不是 `schedule-server` 服务名。
- WebAPI 没有声明对 schedule-server 的健康依赖。

影响：

- 默认 Docker 部署下，WebAPI 容器会访问自身的 `8001`，不能访问调度容器。
- 调度客户端和管理 API 可能分别连接到错误地址。

### 5.3 P0：删除测试计划会遗留调度任务

现状：

- `TestPlanServiceImpl.deleteByIds` 直接把关联 `test_timed_task` 记录设置为删除状态。
- 该路径没有调用 `TestTimedTaskService.deleteByIds`。
- 远程 SnailJob 任务没有被禁用或删除。
- `TestPlanJobExecutor` 使用 `selectById` 读取任务，没有检查 `delFlag`。

影响：

- 测试计划从业务页面删除后，调度中心仍可能继续投递任务。
- 被软删除的任务仍能进入测试计划执行链路。

### 5.4 P0：任务执行前缺少最终状态校验

现状：

- 执行器只校验任务 ID 和任务是否存在。
- 没有校验任务删除状态。
- 定时触发没有校验任务是否仍为 `ENABLED`。
- 没有校验测试计划是否处于正常未删除状态。

影响：

- 禁用、删除与任务投递并发时，在途指令仍可能执行。
- 调度中心存在孤儿任务时，业务侧缺少最后一道保护。

建议规则：

- `SCHEDULE` 触发：任务必须正常、启用，计划必须正常。
- `MANUAL` 触发：允许任务处于禁用状态，但任务和计划不能被删除。
- 不满足条件时生成 `SKIPPED` 记录，不执行测试计划。

### 5.5 P1：取消状态不能收敛

现状：

- 测试报告和聚合服务会产生 `CANCELLED`。
- `TestTimedTaskRunServiceImpl.isTerminal` 只识别 `PASSED` 和 `FAILED`。
- 前端运行记录状态类型和筛选项也没有 `CANCELLED`。

影响：

- 被取消的定时执行会长期显示为 `RUNNING`。
- 通知不会按取消结果正常发送。
- 后续重叠判断可能错误地跳过新任务。

### 5.6 P1：数据库与调度中心缺少一致性机制

现状：

- 创建、修改、启停和删除在本地数据库事务中直接调用远程调度 API。
- 数据库事务无法回滚已经成功的远程操作。
- 远程创建成功、本地提交失败时会产生孤儿调度任务。
- 本地保存了失效的 `scheduleJobId` 时，只会尝试更新，不会自动重建。
- 直接删除任务时没有检查 `jobService.delete` 返回的布尔结果。
- 没有周期性对账或修复入口。

影响：

- 两个系统的 Cron、启停状态和删除状态可能不一致。
- 网络抖动或服务重启后需要人工处理。
- 远程调用会延长数据库事务和行锁持有时间。

### 5.7 P1：下次执行时间会过期

现状：

- `nextExecuteTime` 只在创建、修改、启停和手动触发前同步。
- Cron 正常触发后，没有回写下一次触发时间。
- 列表查询直接展示本地字段。

影响：

- 页面中的“下次执行”可能显示已经过去的时间。
- 用户无法据此判断任务是否健康。

### 5.8 P1：前端未开放 Playwright Runner 定时执行

现状：

- 后端请求对象和执行器支持 `PLAYWRIGHT_RUNNER`。
- 前端 `TestTimedTaskReq` 没有 `executionEngine` 和 `executionConfig`。
- 表单始终要求选择 Jenkins 自动化环境。

影响：

- 用户只能创建默认的 Selenium 定时任务。
- Playwright Runner 支持停留在后端代码层，功能不完整。

### 5.9 P1：运行记录缺少独立回收与幂等终态更新

现状：

- 超过 24 小时的 `RUNNING` 记录只在下一次任务启动时被清理。
- 被禁用或删除的任务可能永远不会再次启动。
- 完成、失败回调采用先查询再更新，缺少 `status = RUNNING` 条件更新。
- 并发回调存在重复通知或终态覆盖风险。

影响：

- 僵死运行记录长期存在。
- 通知可能重复发送。
- 失败和成功回调并发时最终状态不确定。

### 5.10 P2：查询和存储会随历史记录增长退化

现状：

- `latestByTaskIds` 查询任务的全部历史运行记录，再在 Java 内存中选最新一条。
- `test_timed_task_run` 没有业务侧保留或归档策略。
- 调度日志页面固定只取前 50 条，没有分页控件。

影响：

- 列表页耗时和内存占用会随执行历史线性增长。
- 运行记录表会持续膨胀。

### 5.11 P2：删除标记数据库默认值错误

现状：

- `StatusTypeEnum.NORMAL` 的值为 `3`。
- `test_timed_task` 建表 SQL 的 `del_flag` 默认值为 `1`。
- 同一文件中的 `test_plan` 和 `test_report` 也存在相同问题。

影响：

- 通过 SQL、迁移脚本或不写入 `del_flag` 的路径创建的数据可能被正常查询过滤掉。

## 6. 修复设计

### 6.1 阶段一：恢复调度服务闭环

目标：确保本地开发和 Docker 部署都能稳定连接调度中心。

实施项：

1. 将配置参数化：
   - `SCHEDULE_ENABLED`
   - `SCHEDULE_HOST`
   - `SCHEDULE_PORT`
   - `SCHEDULE_API_URL`
   - `SCHEDULE_NAMESPACE`
   - `SCHEDULE_GROUP`
   - `SCHEDULE_TOKEN`
   - `SCHEDULE_USERNAME`
   - `SCHEDULE_PASSWORD`
2. Docker 使用以下容器内地址：
   - Netty：`schedule-server:1788`
   - HTTP API：`http://schedule-server:18001/snail-job`
3. 为 schedule-server 增加 HTTP 和 Netty 健康检查。
4. WebAPI 增加对 schedule-server 的健康依赖和启动重试。
5. 扩展 `dev-start.ps1`，提供 WebAPI 与 schedule-server 的统一启动方式。
6. 增加调度能力探测接口，返回客户端注册、管理 API、命名空间和分组状态。
7. 调度不可用时，前端禁用启用和立即执行操作，并显示明确错误状态。

完成标准：

- 本地一条命令启动完整链路。
- Docker compose 启动后 WebAPI 能访问调度 API，调度中心能发现执行器。
- `ExecuteTestPlanJob` 在 `continew-admin` 分组下处于在线状态。

### 6.2 阶段二：修复删除和执行安全

目标：任何删除、禁用或在途竞态都不能产生非预期测试执行。

实施项：

1. 测试计划删除统一委托给定时任务服务处理关联任务。
2. 删除任务先进入 `DELETING` 或同步失败状态，再删除远程任务，最后完成软删除。
3. 检查远程删除返回值，失败时保留可重试状态和错误信息。
4. 执行器在执行前重新读取任务和计划主数据。
5. 对 `SCHEDULE`、`MANUAL` 分别应用状态校验规则。
6. 被拒绝的在途任务创建 `SKIPPED` 运行记录，记录具体原因。
7. 为计划删除增加事务，避免计划、报告、任务只删除一部分。

完成标准：

- 删除任务或测试计划后，调度中心不存在对应活动任务。
- 模拟已投递消息在删除后到达时，不执行测试计划。
- 删除失败可观察、可重试，不会被错误报告为成功。

### 6.3 阶段三：建立调度一致性与对账

目标：允许网络故障和服务重启，并最终恢复本地与调度中心一致。

推荐新增字段：

| 字段 | 含义 |
| --- | --- |
| `schedule_sync_status` | `PENDING`、`SYNCED`、`FAILED`、`DELETING` |
| `schedule_sync_error` | 最近一次同步错误摘要 |
| `schedule_sync_time` | 最近同步时间 |
| `schedule_sync_version` | 本地配置版本，用于避免旧同步覆盖新配置 |

实施项：

1. 本地事务只保存业务状态和同步事件。
2. 事务提交后由同步处理器调用远程调度 API。
3. 使用 `test-plan-task-{taskId}` 作为幂等外部键。
4. 同步处理支持指数退避、最大重试和人工重试。
5. 增加周期对账任务：
   - 补建本地存在但远程缺失的任务。
   - 删除远程存在但本地已删除的任务。
   - 修复 Cron、参数和启停状态差异。
   - 刷新 `scheduleJobId` 和 `nextExecuteTime`。
6. 列表返回同步状态，前端展示“同步中”或“同步失败”。

不建议继续依赖“本地事务内同步调用远程 API”的方式，因为它无法提供跨系统原子性。

### 6.4 阶段四：统一运行状态和通知

目标：所有执行都能准确、幂等地进入终态。

实施项：

1. 统一状态集合：
   - `RUNNING`
   - `PASSED`
   - `FAILED`
   - `CANCELLED`
   - `SKIPPED`
2. `completeByReport` 正确映射 `CANCELLED`，不再转为 `FAILED`。
3. 更新前端 TypeScript 类型、状态筛选、标签颜色和文案。
4. 更新邮件标题和正文中的取消状态。
5. 终态更新使用条件 SQL：只允许 `RUNNING -> 终态`。
6. 只有成功完成条件更新的调用方可以触发通知。
7. 增加独立 watchdog，定期回收超时运行记录。
8. 执行派发失败时仍将已创建的 `test_report_id` 关联到运行记录。

完成标准：

- 同一报告多次回调只产生一次终态和一次通知。
- 取消执行后运行记录显示 `CANCELLED`。
- 禁用任务的历史僵死记录也能被 watchdog 回收。

### 6.5 阶段五：补齐 Playwright Runner 前端能力

目标：使后端已有的无人值守执行引擎真正可用。

实施项：

1. 定时任务请求和响应类型增加：
   - `executionEngine`
   - `executionConfig`
2. 复用测试计划执行页面的执行引擎选项和 Runner 配置。
3. Selenium 模式要求产品环境和自动化环境。
4. Playwright Runner 模式要求产品环境，不要求 Jenkins 自动化环境。
5. CDP 模式在定时任务中明确禁用，并说明其依赖浏览器会话。
6. 列表增加执行引擎列或标识。
7. 编辑时完整回显执行引擎和配置。

### 6.6 阶段六：数据和性能治理

实施项：

1. 新增 Liquibase changeset：
   - 把相关表 `del_flag` 默认值修改为 `3`。
   - 谨慎回填确认属于正常数据的存量 `del_flag = 1` 记录。
2. 最新运行记录改为数据库侧查询：窗口函数、分组最大 ID 或专用 Mapper SQL。
3. 增加建议索引：
   - `(timed_task_id, status, start_time)`
   - `(status, start_time)`
   - `(notification_status, end_time)`
4. 为业务运行记录配置保留期和分批清理任务。
5. 调度日志页增加服务端分页。
6. `nextExecuteTime` 由对账任务刷新，或列表查询时从调度中心批量获取，不能长期依赖一次性快照。

## 7. 测试方案

### 7.1 单元测试

`TestTimedTaskServiceImplTest` 至少覆盖：

- 创建任务默认为禁用。
- Cron、邮箱、环境和执行引擎校验。
- 调度任务创建、更新、缺失重建和删除失败。
- 测试计划删除时级联清理调度任务。
- 调度同步失败状态和重试。
- 下次执行时间刷新。

`TestTimedTaskRunServiceImplTest` 至少覆盖：

- 禁止并发时生成 `SKIPPED`。
- 允许并发时生成多个运行记录。
- 24 小时超时回收。
- `PASSED`、`FAILED`、`CANCELLED` 状态映射。
- 并发终态回调只成功一次。
- 通知只发送一次。

`TestPlanJobExecutorTest` 增加：

- 已删除任务拒绝执行。
- 已禁用的定时触发拒绝执行。
- 已禁用任务允许手动执行。
- 已删除计划拒绝执行。
- Playwright Runner 配置正确映射。
- 派发异常仍关联报告并关闭运行记录。

### 7.2 集成测试

- 使用 Testcontainers MySQL 验证 `SELECT ... FOR UPDATE` 并发行为。
- 验证 Liquibase 从空库和存量库升级。
- 验证邮件发送失败只影响通知状态，不覆盖执行结果。
- 验证对账任务补建、修复和删除远程任务。
- 验证多实例同时消费时的幂等性。

### 7.3 端到端测试

完整部署 WebAPI、schedule-server、MySQL、Redis 和前端，覆盖：

1. 创建禁用任务。
2. 启用并确认调度中心状态。
3. 到达 Cron 时间后自动触发。
4. 生成业务运行记录和测试报告。
5. 报告进入终态。
6. 邮件只发送一次。
7. 页面显示正确的最近结果和下一次执行时间。
8. 修改 Cron 后旧周期不再触发。
9. 禁用后不再触发。
10. 删除任务和计划后远程任务被清理。
11. 调度中心短暂不可用后自动对账恢复。

## 8. 验收标准

必须全部满足以下条件后，模块才能标记为完成：

- 本地和 Docker 环境都有明确且可重复的一键启动方式。
- WebAPI、调度中心、执行客户端三方健康状态可观测。
- 创建、编辑、启停、手动触发和 Cron 触发均可正常工作。
- 删除任务或计划后不会再产生新的执行。
- 调度中心孤儿任务和本地失联任务能够自动对账。
- `PASSED`、`FAILED`、`CANCELLED`、`SKIPPED` 均正确展示和通知。
- 禁止并发和允许并发两种策略符合配置。
- Selenium 和 Playwright Runner 均通过端到端验证。
- 下次执行时间在每次触发后保持准确。
- 服务重启、网络抖动和重复回调不会产生重复执行或重复通知。
- 数据库迁移可在空库和存量库上成功执行。
- 定时任务核心服务具备足够的单元、集成和端到端测试。
- CI 必须显式使用 `-DrunTests=true`，避免 Maven 默认跳过测试。

## 9. 推荐实施顺序

| 顺序 | 工作包 | 前置依赖 | 交付结果 |
| --- | --- | --- | --- |
| 1 | 调度配置和健康检查 | 无 | 环境可运行 |
| 2 | 删除级联和执行前校验 | 工作包 1 | 消除误执行风险 |
| 3 | 状态收敛和通知幂等 | 工作包 2 | 运行记录可信 |
| 4 | 同步状态、事件和对账 | 工作包 1 | 跨系统最终一致 |
| 5 | Playwright Runner 前端 | 工作包 2、3 | 功能面完整 |
| 6 | 数据迁移和性能治理 | 工作包 3、4 | 可长期运行 |
| 7 | 集成与端到端验收 | 全部 | 模块完成判定 |

P0 工作包完成前，不建议在生产环境启用测试计划定时任务。

## 10. 实施结果与最终判定（2026-08-07）

### 10.1 总体结论

本方案规划的代码修复已经完成，当前开发数据库能够正常启动 WebAPI、schedule-server 和 SnailJob 客户端。原始分析中的调度不可用、删除后误执行、运行状态不收敛、通知重复、跨系统状态不一致、Playwright Runner 不可配置、查询退化和留存治理不足等问题均已有对应实现。

2026-08-07 已补做真实业务链路验收，覆盖任务配置保存、手动触发、真实 Cron 触发、Playwright Runner 浏览器启动、报告和运行记录收敛、调度中心故障与恢复、手动同步、计划删除级联和远程 Job 清理。

当前判定为：**模块代码和核心调度闭环已完成，生产上线验收附带外部环境条件**。

尚未完成的是 SMTP 成功投递、执行 Agent、Selenium/Jenkins、Docker 编排和多实例故障注入验收。这些项目不再构成当前定时任务核心链路的代码阻断，但仍属于第 8 节定义的生产上线前置条件。

### 10.2 阶段实施状态

| 阶段 | 状态 | 已交付内容 |
| --- | --- | --- |
| 1. 调度配置、启动与健康探测 | 已完成 | 补齐本地及 Docker 调度配置、开发启动脚本、调度能力探测接口和前端能力控制；调度不可用时仅禁用启停、执行和同步，不阻断本地创建、编辑。 |
| 2. 删除和执行防护 | 已完成 | 删除定时任务或测试计划时清理远程任务；执行前校验任务、计划、删除状态和触发来源；禁用任务仍允许显式手动执行。 |
| 3. 终态、通知幂等与超时回收 | 已完成 | 统一 `PASSED`、`FAILED`、`CANCELLED`、`SKIPPED` 收敛；终态更新和通知采用条件更新防重；增加超时运行回收。 |
| 4. 调度同步、重试与对账 | 已完成 | 增加同步状态、失败原因、重试、手动同步和周期对账；支持补建、更新、删除孤儿远程任务并刷新下次执行时间。 |
| 5. Playwright Runner 前端能力 | 已完成 | 增加执行引擎及 Runner 配置；Selenium 条件要求 Jenkins 自动化环境；Runner 支持浏览器、质量、会话、Trace、视频、超时、慢动作和结束延迟；CDP 在无人值守任务中禁用。 |
| 6. 数据迁移、查询性能与留存治理 | 已完成 | 新增 v3/v4 迁移、逻辑删除默认值修复和存量回填、运行记录索引、数据库侧最新记录查询、分批留存清理、调度日志分页。 |
| 7. 测试与验收审计 | 核心闭环已完成 | 目标单元测试、Runner 测试、前端检查、数据库迁移、Maven 打包、四端口启动、手动触发、真实 Cron、故障恢复和级联删除均通过；外部系统成功路径仍需在集成环境补测。 |

### 10.3 真实验收发现的补充修复

真实进程和业务链路验收额外发现并修复了以下问题：

1. `dev-start.ps1` 不再盲目使用陈旧的 `JAVA_HOME`，而是读取当前 `java` 命令实际对应的 `java.version` 和 `java.home`，要求 JDK 17 及以上，并让 Maven、schedule-server、WebAPI 使用同一 JDK。
2. Hadoop 传递引入的 `protobuf-java:2.5.0` 会覆盖 SnailJob 1.4.0 所需的 `3.25.3`，导致客户端 gRPC 服务启动时报 `GeneratedMessageV3` 缺失。调度插件现已显式声明 `protobuf-java:3.25.3`，干净构建产物中不再出现 2.5.0。
3. SnailJob 1.4.0 执行器参数已从字符串签名改为 `JobArgs`，并兼容字符串和结构化 `jobParams`，解决调度中心已经投递但业务执行器无法正确取参的问题。
4. 调度线程不具备 Web 上下文时，令牌读取除 `SaTokenContextException` 外还会抛出 `NotWebContextException`。当前实现同时处理两类异常，并允许 Runner 使用进程级服务令牌。
5. Playwright Runner 增加 `RUNNER_BROWSER_EXECUTABLE_PATH` 和 `--browser-executable-path`，普通上下文与持久化上下文均传递 `executablePath`，本机已使用系统 Chrome 启动成功。
6. Runner 基础设施步骤在超时后原先仍会继续轮询。现在 `cancelActive()` 会设置取消状态，并在 sleep、HTTP 查询前后检查该状态，避免 Java 已失败而 Node 仍长期运行。
7. `selectByIdForUpdate()` 是自定义 SQL，会绕过实体的 `autoResultMap`。当前 Mapper 显式使用 `@ResultMap("mybatis-plus_TestTimedTaskDO")`，保证 `executionConfig` 和 `notificationEmails` 在调度线程中按 JSON 类型正确反序列化。
8. 调度对账器原先未受 `snail-job.enabled` 约束，导致默认关闭 SnailJob 的单体 WebAPI 仍会在启动 30 秒后访问 `8001`。当前对账器使用 `@ConditionalOnProperty`，仅在显式启用 SnailJob 时注册。
9. 孤儿 Job 清理原先在调度中心不可达时把异常抛给 Spring 定时线程。当前实现会记录可恢复告警并等待下一轮对账，不再产生 `Unexpected error occurred in scheduled task`。
10. 非生产环境的 SnailJob Feign 日志原先使用 `FULL`，会把 `Snail-Job-Auth` 认证头写入日志。当前日志级别固定为 `BASIC`，保留请求方法、URL、状态和耗时，不记录认证头或请求体。

### 10.4 真实端到端验收记录

临时验收数据在测试完成后已通过正式计划删除接口清理：

| 对象 | 标识 |
| --- | --- |
| 测试计划 | `876159432088723475` |
| 定时任务 | `876159433711919126` |
| SnailJob Job | `3` |
| UI 场景 | `872905521655451665` |
| 项目环境 | `837064881025843439` |

任务使用 `PLAYWRIGHT_RUNNER`、`0 * * * * ?`、禁止并发、系统 Chrome、`stepTimeoutMs=4000`、`caseTimeoutMs=10000` 和 `notificationEmails=["admin@example.com"]`。

| 验收场景 | 结果 | 证据 |
| --- | --- | --- |
| 四端口启动 | 通过 | WebAPI `8000`、schedule-server HTTP `8001`、Netty `1788`、SnailJob 客户端 gRPC `1789` 同时就绪。 |
| 手动触发 | 通过 | Run `876171961787613194`、Report `876171962257375243`；`MANUAL` 触发，约 110 秒收敛为 `FAILED`。 |
| 配置映射 | 通过 | 报告运行环境和 Runner case 均实际使用 `caseTimeoutMs=10000`、`stepTimeoutMs=4000`；运行记录保留 `admin@example.com`。 |
| 系统浏览器与产物 | 通过 | 系统 Chrome 成功初始化为 1920x1080，执行过程生成结果、截图、Trace 和 Video 产物。 |
| 真实 Cron | 通过 | `2026-08-07 18:38:00` 创建 Run `876172826443382992`、Report `876172826716012753`；触发来源为 `SCHEDULE`，约 111 秒自然收敛为 `FAILED`。 |
| 禁用传播 | 通过 | Cron 创建运行后立即禁用任务，任务保持 `DISABLED/SYNCED`，未产生后续调度执行。 |
| 调度不可用 | 通过 | 停止 schedule-server 后能力接口返回 `ready=false`，手动同步进入 `FAILED`，记录连接错误和重试次数 1。 |
| 调度恢复与手动同步 | 通过 | schedule-server 恢复后能力接口返回 `ready=true`；再次同步回到 `SYNCED`，错误清空、重试次数归零、Job ID 保持为 `3`。 |
| 计划删除级联 | 通过 | 删除计划后任务和计划的业务列表数量均为 0，远端 Job `3` 不再存在。 |
| 进程清理 | 通过 | 两次运行终态后 Runner Node 残留为 0；验收结束后四个后端临时端口均释放，临时脚本和日志已删除。 |

两次运行的业务结果为 `FAILED`，原因是场景中的执行 Agent `127.0.0.1:19091` 未启动，且 SMTP 密码为空。该结果符合当前外部环境，重点验证的调度、Runner、回传、聚合和终态收敛链路均已完成。

### 10.5 自动化与构建验证

| 验证项 | 结果 | 说明 |
| --- | --- | --- |
| 后端最终定向回归 | 通过 | `TestPlanJobExecutorTest`、`TestPlanServiceImplTest`、`TestTimedTaskRunServiceImplTest` 合计 23/23。 |
| 后端阶段目标测试 | 通过 | 此前定时任务目标套件 28/28；覆盖同步、并发、终态、超时、通知幂等和删除防护。 |
| 调度关闭与故障降级回归 | 通过 | 同步与能力测试 8/8；默认关闭 SnailJob 的临时 WebAPI 在 `18080` 启动并观察 40 秒，未访问 `JobApi`，未产生对账异常。 |
| Playwright Runner 检查 | 通过 | `npm run check` 通过，完整单元测试 51/51。 |
| 前端 TypeScript 检查 | 通过 | `pnpm typecheck`。 |
| 定时任务前端 ESLint | 通过 | API、列表、编辑抽屉和运行记录抽屉无错误。 |
| Maven 最终打包 | 通过 | JDK 17 下 `mvn -pl continew-webapi -am package -DskipTests -DrunTests=false` 的 11 个 Reactor 模块全部成功。 |
| 运行产物一致性 | 通过 | `continew-test` 源 Jar 与 WebAPI `app/lib` 中运行 Jar 的 SHA-256 完全一致。 |
| PowerShell 语法 | 通过 | `dev-start.ps1` 及临时烟测脚本解析无错误。 |
| Docker Compose 配置解析 | 通过 | 配置可解析；因 Docker daemon 未运行，未启动容器。 |
| Liquibase 空库迁移 | 通过 | 78/78 changeset 执行成功。 |
| Liquibase 存量库 v3/v4 升级 | 通过 | 默认值、存量回填、同步状态和索引断言通过，临时测试库已清理。 |
| 完整开发库启动 | 通过 | 当前开发库正常完成 Liquibase 启动，历史迁移不再阻断本次验收。 |
| 真实业务链路 | 通过 | 手动触发、真实 Cron、失败收敛、故障恢复、手动同步和删除级联均已验证。 |

### 10.6 外部环境限制与上线前补测

1. 启动执行 Agent `127.0.0.1:19091` 后，补做包含 `server_command` 的全成功场景，并确认报告最终为 `PASSED`。
2. 配置有效 SMTP 账号后，分别验证成功、失败、取消和跳过通知只发送一次，且投递失败不覆盖执行结果。
3. 在隔离的 Jenkins 测试 Job 上执行 Selenium 定时任务；当前场景会调用外部 Jenkins，本次验收未直接触发，避免影响外部系统。
4. Docker daemon 可用后执行完整 Compose 编排，验证容器服务名、健康检查、重启和持久化数据恢复。
5. 在集成环境补做多 WebAPI 实例、重复回调和网络抖动场景，验证数据库条件更新、调度对账和通知幂等。
6. CI 持续显式传入 `-DrunTests=true`，防止项目默认 `skipTests=true` 使测试被跳过。

### 10.7 最终验收判定

- 修复方案：已实施并保存。
- 定时任务核心代码、前端配置和调度一致性：已完成。
- 当前开发环境的手动触发、真实 Cron、Playwright Runner、故障恢复和删除清理闭环：已通过。
- 当前是否存在已知核心代码阻断：否。
- 生产上线状态：有条件通过；完成第 10.6 节的外部系统成功路径和部署环境补测后再正式启用。

### 10.8 测试计划场景悬空引用修复（2026-08-07）

故障现象：执行测试计划 `851848429083557985` 时提示“测试场景不存在或已删除，sceneId=872070799261962242”。

根因：计划保存的三个场景 ID 中，`872070799261962242` 和 `747843461465542671` 已不在 `automation_ui_scene` 表中，但删除场景时没有同步清理计划 JSON 字段；同时该历史计划使用 `version_id=0` 表示未指定版本，严格版本校验无法从剩余有效场景解析真实版本。

实施修复：

1. 测试计划执行前对场景引用进行对账，自动剔除不存在或已逻辑删除的场景，并回写场景列表和 `scene_count`。
2. `version_id<=0` 统一按“未指定版本”处理，从有效场景解析并回写实际项目版本。
3. 为悬空场景引用和零版本兼容增加单元测试，防止同类历史数据再次阻断整份计划。
4. 精确修复计划 `851848429083557985` 的存量数据，只保留有效场景 `872905521655451665`，版本修正为 `722845686030139416`，场景数修正为 1。
5. `dev-start.ps1 -WithSchedule` 自动排除 Spring DevTools，避免 SnailJob 静态客户端线程在热重启后残留并占用 `1789`，造成 WebAPI `8000` 启动失败；不带调度启动时仍保留热重启能力。
6. 无人值守 Runner 不再强制依赖人工配置的长期登录令牌；调度器以测试计划创建人为执行主体签发独立的短期 Sa-Token，执行完成后立即注销，现有批次 capability 继续限制 Runner 只能访问本次执行上下文。

验证结果：相关测试 11/11 通过；计划、有效场景、项目版本和定时任务关联已完成只读一致性复核。实际再次执行前必须确认 WebAPI 已加载最新编译产物，且 `8000`、`8001`、`1788`、`1789` 四个端口同时就绪。

### 10.9 调度 Runner 鉴权与失败原因展示修复（2026-08-07）

最新运行 `876242582487773191` 已成功创建报告 `876242583381159944`，原“测试场景不存在或已删除”错误不再出现。该次运行的三个用例均失败于同一原因：Playwright Runner 没有服务端鉴权令牌；页面仅显示“测试计划执行失败”，是报告聚合未向定时运行记录传递实际用例错误导致的次生问题。

实施修复：

1. 手工执行继续复用当前用户登录令牌；无人值守调度没有请求令牌时，以测试计划创建人为执行主体签发独立的短期 Sa-Token，默认有效期 6 小时。
2. 临时令牌在报告执行结束后立即注销；异步任务提交失败时同样回收，避免后台调度依赖长期管理员令牌。
3. 原有 execution capability 继续限制 Runner 只能访问本次批次的执行上下文，临时身份令牌不替代执行范围校验。
4. 报告聚合按“场景、用例、步骤”的顺序提取首个有效 `errorMessage`、`error` 或 `playwrightError`，写入 `statisticAnalysis.ui.failureReason`。
5. 定时运行记录按“`runtimeEnvironment.dispatchError`、`statisticAnalysis.ui.failureReason`、通用失败文案”的优先级保存失败原因，并限制到 `failure_reason varchar(1000)` 的字段上限。
6. SMTP 密码为空只会使 `notification_status=FAILED`，不会改变测试运行和报告的业务状态；需单独配置有效 SMTP 密码，或取消任务通知邮箱。

验证结果：临时令牌签发与注销、调度派发、悬空场景清理、零版本兼容、报告错误聚合和定时运行映射等相关测试合计 36/36 通过。当前运行中的 WebAPI 启动于本轮代码修改之前，且调度模式已禁用 DevTools 热重启，因此必须人工重启 WebAPI 后再触发实际任务验收。验收时应确认不再出现“缺少服务端鉴权令牌”，并在失败场景下确认运行记录展示真实用例错误。
