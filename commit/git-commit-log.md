# Git Commit Log

## 2026-05-18

### refactor(automation): UI 场景执行状态/结果统一存字典 value

- 库存与接口统一为 `status_type` 字典 value（10–12 状态，13–16 结果），删除 Controller 中文展示转换与 `AutomationUiSceneQueryHelper`。
- 新增 `AutomationUiSceneStatusCodes`：仅在外部回调/入库边界将英文或历史中文归一为字典 value。
- Liquibase `automation_ui_scene_status_migrate.sql`：迁移列字段及 `debug_record`/`test_record` JSON 内历史值。
- `main_data.sql` 补充字典项 10–16；前端 `GiCellTag` 直接使用字典 value，筛选/表单不再双向转换。

### fix(automation-ui): 修复执行状态/结果列空白或仅显示中文

- 补全 `status_type` 字典项 10–16（`main_data.sql` 与迁移脚本 `INSERT IGNORE`）。
- `automationUiSceneStatus.ts` 恢复归一化函数，兼容历史中文、英文与 `-`；列表回退实体字段展示。

## 2026-05-06

### fix(sakura-admin-ui): 用户管理列表改用分页接口 `pageUser`

- `views/system/user/index.vue`、`components/UserSelect/index.vue`：`useTable` 由 `listUser`（`/list` 全量数组）改为 `pageUser`（分页 `PageRes`），避免表格分页与总数失真。

### fix(sakura-admin-ui): 用户 API 补充 `resetUserPwd`

- `apis/system/user.ts`：新增 `resetUserPwd(data, id)`，内部调用 `resetUserPassword(id, data)`，与 `UserResetPwdModal` 入参顺序一致。

### fix(sakura-admin-ui): 用户 API 补充 `parseImportUser`、`listAllUser`

- `apis/system/user.ts`：`parseImportUser` 作为 `parseUserImport` 别名；新增 `listAllUser`（`GET /system/user/list` + 默认 sort），供 `UserImportDrawer`、`UserSelect` 等使用；经 `apis/system/index` → `@/apis` 聚合导出。

### fix(sakura-admin-ui): 恢复 `apis/project/projectConfig` 完整 CRUD 与分页

- `apis/project/projectConfig.ts`：恢复分页 `listProjectConfig`（`GET /project/projectConfig`）、全量 `getProjectConfigList`（`/list`）、`getProjectConfig` 及增删改导出与类型；修复 `ExecuteSceneModal`、`ProjectConfig` 等缺 `getProjectConfig` 报错。
- `projectEnvironmentConfig` / `projectVersionConfig` / `projectServerConfig` / `projectDataBaseConfig` / `testPlan`：项目下拉改为 `getProjectConfigList` + `res.data` 数组；`projectModuleConfig` 去掉未使用的 `listProjectConfig` 导入。

### fix(sakura-admin-ui): 测试计划时间与后端 Jackson 日期格式对齐（空格分隔）

- `test/testPlan/index.vue`：`serializePlanTimeRange`、`serializeCreateTime` 改为输出 `yyyy-MM-dd HH:mm:ss`，与全局 `LocalDateTime` 反序列化格式一致（不再提交带 `T` 的 ISO 串）。

### fix(test-plan): JSON 反序列化类型错误提示 + 成员 ID 提交校验

- `GlobalExceptionHandler.java`：`InvalidFormatException` 返回信息增加 Jackson 字段路径与目标类型，便于区分是 `memberIds[n]` 还是 `plannedStartTime` 等。
- `test/testPlan/index.vue`：`toPositiveLongIds` 规范化成员/负责人 ID；提交前校验长度防止非数字混入；编辑回填时同样过滤非法项。

### fix(sakura-admin-ui): 消除 `apis/system/index` 星导出 `updatePassword` 冲突

- `apis/system/user-profile.ts`：删除与 `updateUserPassword`（PATCH `/user/profile/password`）重复的 `updatePassword`（POST 同路径，且与 `user.ts` 忘记密码 `updatePassword` 星导出同名冲突）；登录找回密码仍使用 `user.ts` 的 `updatePassword`（POST `/system/user/password`）。

### fix(sakura-admin-ui): 恢复 `apis/system/user` 完整导出

- `apis/system/user.ts`：此前仅保留 `listSystemUser` 会覆盖 Continew 用户模块 API，`apis/system/index.ts` 的 `export * from './user'` 不再透出 `signup` 等符号；已恢复与 `UserController` 对齐的 `pageUser`、`listUser`、`signup`、导入/重置密码/分配角色等，并保留 `listSystemUser`。
- `test/testPlan/index.vue`：用户下拉数据类型改为 `UserResp`。

### feat(sakura-admin-ui): 测试计划新建表单与列表、创建人筛选

- `test/testPlan/index.vue`：新建/修改弹窗按稿改为横向表单项（所属项目、计划类型、计划名称/简称、计划描述、计划成员多选、主负责人单选、计划时间范围、计划状态默认未开始）；项目下拉对接 `/project/projectConfig/list`，成员/负责人/筛选「创建人」对接 `/system/user/list`（启用用户）；提交写入 `plannedStartTime`/`plannedEndTime`；列表增加「创建人、创建时间、更新时间」列；表单校验失败或接口失败时阻止关闭弹窗。
- `apis/test/testPlan.ts`：`TestPlanResp` 增加 `createUserString`。
- `apis/project/projectConfig.ts`、`apis/system/user.ts`：新增项目列表与用户列表请求封装。

### fix(test-plan): 筛选「创建人/创建时间」可用 + 标签列对齐

- `test/testPlan/index.vue`：标签列固定 `100px` 左对齐，避免第二列与第一行错位；「创建人」改为可输入的 `a-input-number`（`createUser` 用户 ID）；「创建时间」启用 `a-range-picker`（`value-format` 日期），列表/导出经 `pickQueryForBackend` 转为 `yyyy-MM-dd HH:mm:ss` 范围串；`reset` 清空上述字段。
- `apis/test/testPlan.ts`：`TestPlanQuery` 增加 `createUser`、`createTime`。
- `continew-test/.../TestPlanQuery.java`：增加 `createUser`（EQ）与 `createTime`（`BETWEEN` + `@Size(max=2)`），与 `BaseDO` 字段对应。

### fix(sakura-admin-ui): 测试计划筛选区与表格同宽拉满

- `test/testPlan/index.vue`：`#top` 增加 `.plan-query-top-slot` 与 `width:100%`；筛选卡片、表单、行容器链式 `width/max-width:100%`；`GiTable` 根与 `:deep(.gi-table__top)` 拉满；`Tabs` 内容区与 `Tab` 面板 `width/min-width:0`；页面内 `GiTable` 根 `flex:1`+`width:100%`，避免表格区 shrink-to-fit 导致筛选卡片右侧留白。

### fix(sakura-admin-ui): 测试计划筛选区横向标签显示异常

- `test/testPlan/index.vue`：去掉 Arco `horizontal` + `label-col-props` 在自定义三列容器内的组合（易导致标签列被拉满、标签与控件间距过大）；改为 `hide-label` + 自定义 `.plan-query-cell`（「标签：」+ 控件 flex 填充）；三列由 Grid 改为 `flex: 1` 三等分，控件 `width:100%`；小屏字段区改为纵向堆叠。

### style(sakura-admin-ui): 测试计划筛选区改为横向表单项

- `test/testPlan/index.vue`：筛选 `a-form` 改为 `layout="horizontal"`，标签在左、控件在右，统一 `label-col` 约 88px、`colon` 与参考稿一致；行容器 `align-items: center`；保留双行 Flex + 三等分子网格与固定按钮列布局。

### fix(sakura-admin-ui): 测试计划搜索区双行 Flex + 固定按钮列宽

- `test/testPlan/index.vue`：筛选改为每行「三等分子网格 + 右侧按钮」Flex，避免整行四列 `1fr` 在第三列与按钮间留白过大；日期范围与输入框同列 `width:100%`/`min-width:0` 约束右缘对齐；两行按钮列统一 `100px` 宽，保证上下行三列栅格宽度一致。

### fix(sakura-admin-ui): 测试计划搜索区改用 Grid 修正错位

- `test/testPlan/index.vue`：筛选区由 `a-row`/`a-col`（`md=8` 时四列超出 24 栅格导致换行错乱）改为 CSS Grid 固定「三列表单项 + 第四列查询/重置」；按钮区不再套占位 `label` 的 `a-form-item`，与输入控件底边对齐；小屏（≤768px）单列顺序排列且按钮全宽。

### style(sakura-admin-ui): 测试计划列表页排版对齐参考稿

- `test/testPlan/index.vue`：筛选区改为两行网格（首行项目/类型/名称 + 右侧查询，次行状态/创建人/创建时间 + 右侧重置），增加「计划列表」标题与查询卡片边框；所属项目、计划类型改为下拉（项目选项由当前列表聚合，类型为常用项合并列表数据）；表格列与参考图对齐（计划开始/结束时间，去掉场景数与执行统计等中间列）；批量导出/删除改为默认描边样式；「更多」改为文字链 + 下拉。
- `apis/test/testPlan.ts`：`TestPlanResp` 补充 `plannedStartTime`、`plannedEndTime` 字段声明。
- 场景子 Tab 内查询表单补充 `model` 与占位 `v-model`，消除 Arco 表单项类型告警。

## 2026-04-25

### fix(sakura-admin-ui): 修复自动化场景表格 loading 动画偏位

- 在 `automationUiScene/components/AutomationUiScene.vue` 增加表格 loading 容器高度与居中样式约束，修复加载动画在列表区域偏移到数据行附近的问题。
- 仅在自动化场景页面做定向样式修复，避免影响其他页面表格 loading 表现。
- 针对 Chrome 升级后的渲染差异，进一步固定 `arco-spin`/`arco-spin-mask` 蒙层定位，并强制 loading 图标以中心点旋转，减少动画抖动与漂移。
- 新增页面专用 `loading-icon` 插槽，使用纯 CSS 环形 spinner 替代默认图标，规避浏览器版本差异导致的动画锚点偏移。
- 回退会影响表格渲染流的 `arco-spin` 绝对定位与自定义 loading 插槽方案，改为仅保留 loading 图标旋转中心兼容修正，确保数据正常加载。
- 采用低风险方案重新启用页面级 `loading-icon` 插槽（固定尺寸纯 CSS spinner），不再修改 `arco-spin` 布局定位，在保证数据加载的同时稳定动画显示位置。
- 针对“首屏正常、搜索/重置/刷新后二次偏移”场景，补充 `arco-spin` 相对定位与 `arco-spin-mask` 绝对铺满居中，固定遮罩层锚点，避免数据态切换时定位漂移。
- 继续补强 loading 图标承载层：为 `arco-spin-icon` 增加全尺寸 flex 居中，并将自定义 spinner 设为 `display:block` + 中心旋转，消除 inline 基线与字体盒导致的视觉偏移。
- 最终改为页面级自定义 loading 遮罩层：关闭 `GiTable` 内置 loading，使用固定定位的 `scene-loading-mask + scene-loading-spinner` 渲染加载态，彻底规避 Arco 表格 loading 在 Chrome 新版本下的偏移问题。
- 按组件层统一接管方案，`GiTable` 内将 `a-table` 的 `loading` 统一转换为对象配置（显式 `tip`），并内置统一 `loading-icon`；页面侧恢复使用 `:loading="loading"`，移除临时遮罩实现。
- 在 `GiTable` 组件样式内进一步统一 `arco-spin`/`arco-spin-children`/`arco-spin-mask` 尺寸与居中规则，并修正 `arco-spin-icon` 与默认 spinner 的中心点，消除搜索/重置/刷新等二次加载时的图标漂移。
- 进一步改为 `GiTable` 内部自定义 loading overlay：`a-table` 关闭内置 loading，组件统一渲染遮罩层（loading-icon + tip），彻底绕过 Arco loading 蒙层定位差异，保证各浏览器与二次请求场景下位置稳定。
- 优化 `GiTable` loading overlay 视觉样式：降低遮罩侵入感、加入轻量磨砂与卡片容器、缩小并提速 spinner、统一提示文字层级，使加载态更贴合系统整体风格。
- 新增通用组件 `GiLoading`（统一遮罩、spinner、tip 样式），支持图标插槽覆盖，作为系统级加载视觉规范。
- `GiTable` 改为复用 `GiLoading`，保留 `loading`/`loadingTip` 能力并兼容 `loading-icon` 插槽，实现表格加载统一风格。
- `GiTree` 左侧功能模块加载态改为复用 `GiLoading`，替换原本 `a-spin` 临时样式，实现树与表格加载视觉一致。

### chore(sakura-admin-ui): 开发环境默认端口可配置

- 在 `.env.development` 增加 `VITE_DEV_PORT`（默认 5173），并在 `vite.config.ts` 的 `server.port` 中读取。
- 不启用 `strictPort`，在默认端口被占用时仍由 Vite 自动使用下一个可用端口。

## 2026-04-24

### fix(project/automation): 无关联配置修改不再误判失败

- 修复自动化管理环境同步返回语义：`updateProjectConfig`、`updateJenkinsConfig`、`updateNodeConfig`、`updateBrowserConfig` 在“无关联命中”场景返回成功，仅在真实异常时返回失败，避免修改无关联配置时报错。
- 同步修复项目管理环境同步返回语义：`updateVersionConfig`、`updateServerConfig`、`updateDataBaseConfig` 同步采用一致策略，防止后续调用方将“无关联”误判为失败。
- 统一了关联配置同步行为：有关联时自动同步，无关联时不阻塞主配置更新，保留异常场景的失败返回以便告警与排查。

