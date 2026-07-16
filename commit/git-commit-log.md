# Git Commit Log

## 2026-07-13

### feat: 按目标用例追加和替换步骤

- 新增 `appendStep` 和 `replaceStep`，步骤操作必须指定目标用例；`appendStep` 支持最前、末尾和指定步骤后插入。
- 新增 `replaceCase` 表示替换整个用例，保留用例 ID、order 和备注等结构性信息；旧 `replaceCaseSteps` 保留为兼容模式。
- 步骤插入或替换后统一重排目标用例内部的步骤 `id/order/pid`，避免步骤 ID 重复导致树节点定位错误。
- 追加或替换录制时根据目标用例/步骤上下文自动恢复起始地址，产品环境地址仅作为兜底；CueCast 保存录制结束时的真实页面地址。

## 具体代码改动

- `AutomationRecordingImportReq` 增加 `targetStepId`、`stepAppendPosition`、`appendAfterStepId`。
- 新增 `RecordingStepPositionResolver`，负责步骤位置解析和目标用例内步骤重排。
- `AutomationRecordingImportServiceImpl` 新增 `replaceCase`、`appendStep`、`replaceStep` 分支。
- `ChromeRecordingModal.vue` 增加目标用例、目标步骤、步骤追加位置选择和替换差异提示。
- `ChromeRecordingModal.vue` 按模式计算用例初始页、锚点用例末页或目标步骤前置页。
- `PlaywrightRecordedCaseReq` 和 `PlaywrightRecordingAssembler` 增加 `end_url` 保存。

## 2026-07-13

### fix: replaceCaseSteps 替换确认与差异上下文

- `ChromeRecordingModal.vue`：`replaceCaseSteps` 启动录制前增加破坏性操作确认，展示旧步骤数；录制结果展示“将替换 X 个旧步骤为 Y 个新步骤”的差异信息。
- `ChromeRecordingModal.vue`：替换时直接使用弹窗当前的用例名称，未修改则保持原名称，修改后覆盖原名称；备注继续保留。
- `recorder-manager.js`：透传替换前步骤数，避免扩展保存链路丢失差异上下文。

### fix: 录制追加后同步重排用例 ID

- `AutomationRecordingImportServiceImpl`：录制追加按 `FIRST`、`LAST` 或 `AFTER` 确定插入位置后，统一重排全部用例的 `order` 和连续 `CaseDO.id`，保持 ID 序号与展示顺序一致。
- `RecordingAppendCasePositionResolver`：根据现有用例 ID 保留基础前缀；无历史用例时使用 `SCENE_CASE_`，并集中处理用例 ID 重排。
- ID 重排同时更新每个步骤的 `pid` 和关联的 `copyPid`，避免 case ID 变化后步骤失去父用例。
- `RecordingAppendCasePositionResolverTest`：覆盖插入后 `SCENE_CASE_001/002/003` 连续编号、`order` 重排以及步骤父 ID 同步。

## 2026-05-22

- `GiCellTags.vue`：仅 1 个标签时展示完整文案，多个时展示数量（Popover 悬停查看全部）；表格内居中显示。

- `TestPlanSceneWorkspace.vue`：`planSceneIds` 缓存，查询/重置不再每次 `getTestPlan`；重置 `await nextTick` 后再查询，避免 GiForm 延迟 flush 导致双请求。
- `TestPlanRelateSceneModal.vue`：重置同样 `await nextTick` 后再 `search()`，修复双请求。

### fix(sakura-admin-ui): 测试计划场景页切换版本触发 listAutomationUiScene

- `TestPlanSceneWorkspace.vue`：切换版本时 watch 仅刷新模块树，列表查询交给 GiForm `search-on-change`；初始化/重置期间关闭自动 search，避免 `listAutomationUiScene` 重复请求。

- `TestPlanRelateSceneModal.vue`：监听 `queryForm.versionId` 切换时 `force` 刷新左侧模块树，并清空模块选中；`refreshModuleTreeLocal` 优先使用表单中的版本 ID。

### fix(sakura-admin-ui): 关联场景弹窗列表接口重复请求

- `TestPlanRelateSceneModal.vue`：保留 `search-on-change`（初始化期间由 `modalInitializing` 关闭）；`sceneListEnabled` + 延迟挂载表格避免 init 双请求；树节点/筛选项变更走 GiForm 自动 search。

### fix(sakura-admin-ui): 测试计划场景页树重复加载与模块筛选不请求

- `TestPlanSceneWorkspace.vue`：初始化期间跳过 `queryForm.versionId` watch，避免 `tree` 接口重复请求；移除 `loadModuleTree` 后多余的 `fetchTrees` 回退。
- 点击左侧模块树时调用 `listAutomationUiScene?moduleId=...` 并按计划已关联场景 ID 交集展示，行为对齐 UI 自动化场景页。

### perf(sakura-admin-ui): 关联场景弹窗复用外层已加载的模块树与 uiStore 数据

- `projectContext.ts`：`loadModuleTree` 按 `projectId|versionId` 内存缓存，同上下文二次打开不再请求接口；`initUiStoreForPlan` 在项目/用户列表已存在时跳过重复 fetch。
- `TestPlanRelateSceneModal.vue`：打开弹窗走缓存；切换项目或版本时 `force` 刷新模块树。

## 2026-05-21

### fix(sakura-admin-ui): 测试计划关联/取消关联后场景列表不刷新

- `TestPlanSceneWorkspace.vue`：`loadScenes` 通过 `getTestPlan` 拉取最新 `uiTestScene`；`reload` 仅刷新场景表格；计划 watch 归一化 ID 比较避免误触发 `initWorkspace`；移除对全局 `uiStore.versionId` 的监听，避免关联弹窗改动 uiStore 时连带刷新左侧树。
- `index.vue`：关联/取消关联后仅 `reload` 场景表或 `search` 列表，不再 `getTestPlan` 整对象替换 `tab.record`。

### fix: 关联场景弹窗排除已关联场景

- `AutomationUiSceneQuery` 新增 `excludeIds` 查询参数，标注 `@QueryIgnore` 避免框架默认映射为 `exclude_ids` 列；由 Service 转为 `id NOT IN (...)`。
- `TestPlanRelateSceneModal.vue`：打开弹窗时 `getTestPlan` 拉取已关联 ID，列表与「关联所有」请求携带 `excludeIds` 过滤。

## 2026-05-20

### fix(sakura-admin-ui): 测试计划场景页显示 GiPageLayout 折叠按钮

- 移除错误的 `v-model:default-collapsed=false`（该 prop 表示是否展示折叠按钮，非折叠状态），改为 `:default-collapsed="true"`。

### fix(sakura-admin-ui): 测试计划场景页左侧可拖拽与底部搜索栏固定

- `TestPlanSceneWorkspace.vue`：恢复 `GiPageLayout` 支持左侧宽度拖拽；树区域与底部搜索分层，搜索栏 `flex-shrink: 0` 避免被树列表遮挡。
- `TestPlanRelateSceneModal.vue`：弹窗左侧同样固定底部搜索栏并补全高度链。

### fix(sakura-admin-ui): 修复测试计划场景 Tab 内容区高度为 0 导致空白

- `index.vue`：为场景 Tab 增加 `scene-tab-pane` 容器；`arco-tabs-content` 使用 `height: calc(100% - 42px)` 与 `min-height`；移除 `:has()` 与 `overflow: hidden` 导致的 0 高度。
- `TestPlanSceneWorkspace.vue`：工作区增加 `min-height` 兜底，避免 flex 链塌陷。

### fix(sakura-admin-ui): 测试计划场景执行区排版与模块树搜索

- `TestPlanSceneWorkspace.vue`：改用 flex 双栏布局（`gap: 16px`）替代 `GiPageLayout`，消除左右贴边与底部大面积空白；模块树关键词搜索独立置于左侧底部。
- `TestPlanRelateSceneModal.vue`：同步底部搜索与 `gutter` 间距；模块树加载增加 `uiStore.fetchTrees` 兜底。
- `projectContext.ts`：版本 `type` 支持数字 `1` 与字符串 `'1'` 识别。

### fix(sakura-admin-ui): 测试模块雪花 ID 精度丢失统一修复

- 新增 `utils/id.ts`（`toIdString` / `toIdStringList`），`projectContext` 复用。
- `testPlan`：执行环境 ID 改字符串提交；场景关联/移除 `sceneIds` 不再 `Number()`。
- `testReport` / `timedTask` / `testMetric`：查询与表单 ID 改 `a-input` + 字符串提交/查询。
- API 类型：`testPlan` sceneIds、`testReport`、`timedTask`、`testMetric` 相关 ID 字段改为 `string`。

### fix(sakura-admin-ui): 测试计划场景页自动请求模块树接口与间距

- 修复版本 `type` 与 `'1'` 严格比较导致 `versionId` 为空、未调用 `/project/projectModuleConfig/tree` 的问题。
- 增加 `resolveVersionId` 兜底；打开场景 Tab 时拉取计划详情确保 `projectId` 有效。
- 优化场景执行区上下间距与 flex 高度，减少空白。

### fix(sakura-admin-ui): 测试计划场景页左侧计划切换与模块树布局

- 左上角改为测试计划下拉切换；模块树本地加载并修复仅取 `data[0]` 导致树为空的问题。
- 左侧与右侧增加 gutter 间距；关键词搜索移至模块树底部（与 UI 自动化一致）。
- 压缩场景执行区高度，减少大面积空白。

### fix(sakura-admin-ui): 测试计划场景页项目 ID 精度与模块树加载

- 项目/版本 ID 统一字符串传递，修复雪花 ID `Number()` 精度丢失导致模块树与场景列表无法加载。
- 项目下拉 `fallback-option=false` 并合并计划所属项目，消除下拉中重复的数字异常项。
- 优化执行页/关联弹窗左侧树布局与顶部 Tab 间距（`size=medium`）。

### feat(sakura-admin-ui): 测试计划 UI 自动化执行页对齐自动化模块布局

- `test/testPlan/index.vue`：顶部计划 Tab 改为 `card-gutter` 间距样式；场景执行 Tab 接入 `TestPlanSceneWorkspace`；关联场景改为 `TestPlanRelateSceneModal` 大图选择弹窗。
- `TestPlanSceneWorkspace.vue`：复刻 UI 自动化 `GiPageLayout` + 模块树 + `GiTable` 筛选与工具栏（关联测试场景、批量删除、批量执行、执行所有）。
- `TestPlanRelateSceneModal.vue`：关联场景弹窗复刻自动化列表布局，支持模块树筛选、勾选与「关联所有」。

### fix(sakura-admin-ui): 测试计划表单校验、成员昵称展示与编辑回填

- `test/testPlan/index.vue`：弹窗改用 `@before-ok`，校验失败返回 `false` 阻止关闭；成员/负责人/计划时间增加自定义 validator 与提交前兜底校验。
- 用户下拉 `value` 改为字符串 ID，避免 `Number(snowflakeId)` 精度丢失导致编辑时成员/负责人显示为空。
- 编辑/复制时先请求 `getTestPlan` 详情再回填；列表「查看」与详情展示用户昵称（`userLabelMap`）而非纯 ID。

### fix(sakura-admin-ui): 测试计划按用户 ID 补全昵称与下拉选项

- `test/testPlan/index.vue`：提交仅保留 `validate()` 一处判断；`listAllUser({ userIds })` + `getUser` 兜底加载已选用户（含禁用）；列表/查看/编辑前预加载用户；下拉合并已选 ID 选项以显示昵称。

### fix(sakura-admin-ui): 测试计划表单校验失败时提示必填项

- `test/testPlan/index.vue`：Arco `validate()` 失败时返回 errors 对象而非 reject，改为 `if (await validate())` 判断；表单开启 `scroll-to-first-error`。

## 2026-05-19

### fix(sakura-admin-ui): 搜索控件宽度计算与 searchControlMinWidth 属性

- 列 `min-width` 改为「标签 + 间距 + 控件」之和，避免 150px 含标签导致输入区过窄。
- 新增 `searchControlMinWidth` / `searchLabelWidth`；默认控件 180px；用户管理、UI 场景页设为 200。

### fix(sakura-admin-ui): 搜索控件 min-width 生效与收起按钮固定第 3 行

- `--gi-form-search-control-min-width` 作用于 `.gi-form__search-field` 与 `.gi-form__search-cell-control`（不再设在 width:100% 的子控件上）。
- 收起/展开放入右侧按钮栅格 `grid-row: 3`，位于重置按钮下方；`--search-rail-row-count` 与左侧行数对齐。

### fix(sakura-admin-ui): 搜索区收起/展开与左侧筛选区垂直居中对齐

- `GiForm`：双行搜索改为 CSS Grid；收起/展开置于字段与按钮列之间（`grid-row: 1 / -1` + `align-self: center`），查询/重置仍固定第 1、2 行。

### fix(sakura-admin-ui): 搜索区创建时间范围选择器加宽

- `GiForm`：`range-picker` 字段使用 `gi-form__search-field--range`，`min-width: 300px`、列 `flex-grow` 加大，避免日期时间被截断。
- `AutomationUiScene` / `testPlan`：创建时间开启 `showTime` 与完整格式。

### fix(sakura-admin-ui): 缩减搜索区左侧留白

- `GiFormFieldItem`：标签列宽 80px、仅右侧 `padding-right: 4px`，标签与控件 `gap: 4px`。
- `gi-form-search-card`：内边距 `20px` 改为 `16px`；清除表单项 content 多余 `margin-left`。

### fix(sakura-admin-ui): 搜索区标签左右留白对称并右对齐贴近视图

- `GiFormFieldItem`：标签列 `flex: 0 0 100px`、`padding: 0 8px`、`text-align: right`，与控件 `gap: 8px` 对齐原 plan-query-cell。
- `testPlan/index.vue`：同步 `.plan-query-cell__label` 样式。

### fix(sakura-admin-ui): 搜索区收起后恢复右侧纵向按钮栏布局

- `GiForm`：双行搜索改为左侧字段区 + 右侧独立操作栏；查询/重置/收起纵向排列（与改前网格搜索一致），收起后不再把「展开」插在查询与重置之间。

### fix(sakura-admin-ui): 恢复搜索区标签间距并固定查询/重置按钮行位

- `GiFormFieldItem`：标签区恢复 `flex: 0 0 80px`、`padding-right: 4px`、左对齐。
- `GiForm`：重置固定第二行；双行时收起置于首行查询下、三行及以上时收起在第三行，避免挤动重置按钮；字段与按钮列间距恢复 35px。

### fix(sakura-admin-ui): 统一 GiTable 搜索区双行布局与按钮对齐

- `GiForm.vue`：`search-rows` 末行支持收起/展开；行间距与按钮列对齐优化；双行模式表单项样式内聚到组件。
- `GiFormFieldItem.vue`：搜索单元格标签右对齐。
- `system/user/index.vue`：改用 `search-columns-per-row=3` 双行布局，移除破坏对齐的 scoped 表单样式。
- `automationUiScene/AutomationUiScene.vue`：同步双行搜索布局，`foldable` 替代 `show(collapsed)`。

### fix(sakura-admin-ui): GiForm 无收起字段或仅两行时不显示展开收起按钮

- `GiForm.vue`：新增 `showFoldBtn`，仅当存在 `foldable: true` 字段且（双行布局时）超过 2 行时展示收起/展开。
- `type.ts`：`ColumnItem` 补充 `foldable` 类型说明。

### refactor(system): 用户手机号/邮箱改为明文存储

- `UserDO`、`UserDetailResp`：移除 `phone`、`email` 的 `@FieldEncrypt`。
- `UserMapper`：手机号/邮箱相关查询参数去掉 `@FieldEncrypt`。
- `UserServiceImpl`：列表筛选改为 `t1.phone`/`t1.email` 模糊查询；导入重复校验不再 AES 加密比对。

### feat(system): 用户列表支持手机号/邮箱/性别独立筛选

- `UserQuery`：新增 `phone`、`email`、`gender` 查询字段。
- `UserServiceImpl.buildQueryWrapper`：性别等值筛选；`description` 用于用户名/昵称/描述模糊 OR 查询。
- `sakura-admin-ui`：`UserQuery` 类型补充字段；用户管理页性别下拉接入 `GenderList`。

### refactor(sakura-admin-ui): 测试计划列表查询改用 GiForm

- `test/testPlan/index.vue`：计划列表 `#top` 使用 `<GiForm v-model="queryForm" :columns="queryFormColumns" search />`，字段含所属项目、计划类型/名称、状态、创建人、创建时间；原 `a-card` + 自定义两行布局整段注释保留。

### fix(sakura-admin-ui): 测试计划查询区恢复卡片边框与两行布局

- `test/testPlan/index.vue`：`GiForm` 放入 `plan-query-card` 边框容器；拆为 `queryFormRow1Columns` / `queryFormRow2Columns` 两行，每行右侧保留「查询」「重置」按钮，样式对齐原 `plan-query-line` 布局。

### feat(sakura-admin-ui): GiForm 支持双行搜索布局（searchColumnsPerRow）

- `GiForm.vue`：新增 `searchColumnsPerRow`，首行「查询」、末行「重置」；`searchOnChange` 控制变更是否自动 search（默认 true）；抽取 `GiFormFieldItem`；优化默认搜索区 flex 布局。
- `test/testPlan/index.vue`：单表单 `<GiForm search :search-columns-per-row="3" search-btn-text="查询" :search-on-change="false" />` + `queryFormColumns`，保留 `plan-query-card` 边框。

### fix(sakura-admin-ui): 测试计划筛选区间距与标签对齐

- `plan-query-form`：标签右对齐、列宽 100px、行间距 16px、列间距 16px、按钮列 100px，对齐原 `plan-query-line` 布局。
- `GiForm`：`search-row` 网格使用 `gridProps.colGap`，按钮列宽度撑满。

### fix(sakura-admin-ui): GiForm 双行搜索改用 flex 修复排版错位

- `searchColumnsPerRow` 模式由 `a-grid` 改为 `flex` 三等分（`gi-form__search-row-fields`），避免输入框过窄、按钮与日期重叠；表单项级 `layout="horizontal"`。

### fix(sakura-admin-ui): GiForm searchCell 修复双行搜索排版

- 根因：外层 `layout="horizontal"` 使 form 变 flex，字段区被挤压、按钮占第三列。
- `searchColumnsPerRow` 强制外层 `vertical`；字段用 `searchCell`（hide-label + 行内标签/控件，对齐原 `plan-query-cell`）。
- `testPlan` 移除多余的 label-col / layout 配置。

### fix(sakura-admin-ui): searchCell 标签换行修复

- `GiFormFieldItem`：searchCell 样式移至子组件 scoped，标签与控件 `flex nowrap` 同行；`layout="horizontal"` + `hide-label`。

### fix(sakura-admin-ui): 测试计划筛选标签去冒号、左右间距一致

- `queryFormColumns` 标签去掉「：」。
- `GiFormFieldItem` searchCell 标签 `padding: 0 8px`、`text-align: right`，与控件间距对称。

### fix(sakura-admin-ui): searchCell 标签文字左右留白对称

- 标签区 100px 内 `text-align: center`、`padding: 0 4px`；标签与控件 `gap: 8px`（对齐原 plan-query-cell）。

### feat(sakura-admin-ui): GiForm search 模式内置边框卡片

- `GiForm` 新增 `searchCard`（默认 `true`）：`search` 时自动包裹边框容器（`.gi-form-search-card`），GiTable `#top` 筛选区风格统一。
- `testPlan` 移除外层 `plan-query-card`；`AutomationUiScene` 移除多余 divider。

### fix(sakura-admin-ui): GiForm searchCard 边框不显示

- 改用 `div.gi-form-search-card` 替代 `a-card bordered=false`（Arco 会清掉 border）；padding 直接写在容器上。

### fix(sakura-admin-ui): GiForm searchCard 边框仍不显示

- `defineOptions({ inheritAttrs: false })` + 根节点合并 `$attrs.class`；`searchCard` 默认 `true`。
- `.gi-form-search-card` 改为非 scoped 全局样式，确保边框类命中。

### fix(sakura-admin-ui): 统一 GiForm 搜索/重置按钮尺寸

- 默认搜索布局 `.gi-form__search-actions` 与双行布局一致：`100px` 列宽、按钮 `width: 100%`、`size` 跟随表单；UI 自动化补充 `search-btn-text="查询"`。

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

