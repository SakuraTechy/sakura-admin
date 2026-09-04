-- liquibase formatted sql

-- changeset sakura:automation-menu-20260822
-- comment 初始化自动化管理目录及功能菜单；模块资源目录中的菜单 SQL 不会被 Liquibase 主 changelog 自动执行。
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(714856722048483340, '自动化管理', 0, 1, '/automation', 'Automation', 'Layout', NULL, 'align-left', b'0', b'0', b'0', NULL, 7, 1, 1, NOW()),
(714856826889306127, '项目配置', 714856722048483340, 2, '/automation/projectConfig', 'AutomationProjectConfig', 'automation/automationProjectConfig/index', NULL, 'calendar', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
(714883660246941836, 'Jenkins配置', 714856722048483340, 2, '/automation/jenkinsConfig', 'AutomationJenkinsConfig', 'automation/automationJenkinsConfig/index', NULL, 'bulb', b'0', b'0', b'0', NULL, 2, 1, 1, NOW()),
(715210734472003594, '节点配置', 714856722048483340, 2, '/automation/nodeConfig', 'AutomationNodeConfig', 'automation/automationNodeConfig/index', NULL, 'archive', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
(718495661808418821, '浏览器配置', 714856722048483340, 2, '/automation/browserConfig', 'AutomationBrowserConfig', 'automation/automationBrowserConfig/index', NULL, 'arco', b'0', b'0', b'0', NULL, 4, 1, 1, NOW()),
(718525015707877379, '环境配置', 714856722048483340, 2, '/automation/environmentConfig', 'AutomationEnvironmentConfig', 'automation/automationEnvironmentConfig/index', NULL, 'apps', b'0', b'0', b'0', NULL, 5, 1, 1, NOW()),
(721370537913221325, 'UI自动化', 714856722048483340, 2, '/automation/automationUiScene', 'AutomationUiScene', 'automation/automationUiScene/index', NULL, 'apps', b'0', b'0', b'0', NULL, 6, 1, 1, NOW()),
(1933371197522239508, '用例评审', 0, 2, '/automation/automationCaseReview', 'AutomationAutomationCaseReview', 'automation/automationCaseReview/index', NULL, 'check-square', b'0', b'1', b'0', 'automation:automationUiScene:review:view', 999, 1, 1, NOW());

-- 基础 CRUD 和 Runner 权限；危险操作权限由后续自动化 changeset 增量补充。
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(1933371197522239488, '列表', 721370537913221325, 3, 'automation:automationUiScene:list', 1, 1, 1, NOW()),
(1933371197522239489, '详情', 721370537913221325, 3, 'automation:automationUiScene:get', 2, 1, 1, NOW()),
(1933371197522239490, '新增', 721370537913221325, 3, 'automation:automationUiScene:create', 3, 1, 1, NOW()),
(1933371197522239491, '修改', 721370537913221325, 3, 'automation:automationUiScene:update', 4, 1, 1, NOW()),
(1933371197522239492, '删除', 721370537913221325, 3, 'automation:automationUiScene:delete', 5, 1, 1, NOW()),
(1933371197522239493, '导出', 721370537913221325, 3, 'automation:automationUiScene:export', 6, 1, 1, NOW()),
(1933371197522239494, 'Playwright Runner 回放', 721370537913221325, 3, 'automation:automationUiScene:execute', 7, 1, 1, NOW()),
(1933371197522239503, '查看 UI 用例评审', 721370537913221325, 3, 'automation:automationUiScene:review:view', 16, 1, 1, NOW()),
(1933371197522239504, '提交 UI 用例评审', 721370537913221325, 3, 'automation:automationUiScene:review:submit', 17, 1, 1, NOW()),
(1933371197522239505, '评论 UI 用例评审', 721370537913221325, 3, 'automation:automationUiScene:review:comment', 18, 1, 1, NOW()),
(1933371197522239506, '批准 UI 用例评审', 721370537913221325, 3, 'automation:automationUiScene:review:approve', 19, 1, 1, NOW()),
(1933371197522239507, '管理 UI 用例评审', 721370537913221325, 3, 'automation:automationUiScene:review:admin', 20, 1, 1, NOW()),
(1924363034915627009, '列表', 1924363034915627008, 3, 'automation:automationProjectConfig:list', 1, 1, 1, NOW()),
(1924363034915627010, '详情', 1924363034915627008, 3, 'automation:automationProjectConfig:get', 2, 1, 1, NOW()),
(1924363034915627011, '新增', 1924363034915627008, 3, 'automation:automationProjectConfig:create', 3, 1, 1, NOW()),
(1924363034915627012, '修改', 1924363034915627008, 3, 'automation:automationProjectConfig:update', 4, 1, 1, NOW()),
(1924363034915627013, '删除', 1924363034915627008, 3, 'automation:automationProjectConfig:delete', 5, 1, 1, NOW()),
(1924363034915627014, '导出', 1924363034915627008, 3, 'automation:automationProjectConfig:export', 6, 1, 1, NOW()),
(1928023875242184705, '列表', 1928023875242184704, 3, 'automation:automationEnvironmentConfig:list', 1, 1, 1, NOW()),
(1928023875242184706, '详情', 1928023875242184704, 3, 'automation:automationEnvironmentConfig:get', 2, 1, 1, NOW()),
(1928023875242184707, '新增', 1928023875242184704, 3, 'automation:automationEnvironmentConfig:create', 3, 1, 1, NOW()),
(1928023875242184708, '修改', 1928023875242184704, 3, 'automation:automationEnvironmentConfig:update', 4, 1, 1, NOW()),
(1928023875242184709, '删除', 1928023875242184704, 3, 'automation:automationEnvironmentConfig:delete', 5, 1, 1, NOW()),
(1928023875242184710, '导出', 1928023875242184704, 3, 'automation:automationEnvironmentConfig:export', 6, 1, 1, NOW()),
(1927993812610310145, '列表', 1927993812610310144, 3, 'automation:automationBrowserConfig:list', 1, 1, 1, NOW()),
(1927993812610310146, '详情', 1927993812610310144, 3, 'automation:automationBrowserConfig:get', 2, 1, 1, NOW()),
(1927993812610310147, '新增', 1927993812610310144, 3, 'automation:automationBrowserConfig:create', 3, 1, 1, NOW()),
(1927993812610310148, '修改', 1927993812610310144, 3, 'automation:automationBrowserConfig:update', 4, 1, 1, NOW()),
(1927993812610310149, '删除', 1927993812610310144, 3, 'automation:automationBrowserConfig:delete', 5, 1, 1, NOW()),
(1927993812610310150, '导出', 1927993812610310144, 3, 'automation:automationBrowserConfig:export', 6, 1, 1, NOW()),
(1924666739833565185, '列表', 1924666739833565184, 3, 'automation:automationNodeConfig:list', 1, 1, 1, NOW()),
(1924666739833565186, '详情', 1924666739833565184, 3, 'automation:automationNodeConfig:get', 2, 1, 1, NOW()),
(1924666739833565187, '新增', 1924666739833565184, 3, 'automation:automationNodeConfig:create', 3, 1, 1, NOW()),
(1924666739833565188, '修改', 1924666739833565184, 3, 'automation:automationNodeConfig:update', 4, 1, 1, NOW()),
(1924666739833565189, '删除', 1924666739833565184, 3, 'automation:automationNodeConfig:delete', 5, 1, 1, NOW()),
(1924666739833565190, '导出', 1924666739833565184, 3, 'automation:automationNodeConfig:export', 6, 1, 1, NOW()),
(1924389315099066369, '列表', 1924389315099066368, 3, 'automation:automationJenkinsConfig:list', 1, 1, 1, NOW()),
(1924389315099066370, '详情', 1924389315099066368, 3, 'automation:automationJenkinsConfig:get', 2, 1, 1, NOW()),
(1924389315099066371, '新增', 1924389315099066368, 3, 'automation:automationJenkinsConfig:create', 3, 1, 1, NOW()),
(1924389315099066372, '修改', 1924389315099066368, 3, 'automation:automationJenkinsConfig:update', 4, 1, 1, NOW()),
(1924389315099066373, '删除', 1924389315099066368, 3, 'automation:automationJenkinsConfig:delete', 5, 1, 1, NOW()),
(1924389315099066374, '导出', 1924389315099066368, 3, 'automation:automationJenkinsConfig:export', 6, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `parent_id` = 714856722048483340 OR `id` IN (714856722048483340, 1933371197522239508);

-- changeset codex:interfaces-certificate-menu-20260904
-- comment 新增接口管理目录及证书制作菜单。
INSERT IGNORE INTO `continew_admin`.`sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`, `update_user`, `update_time`)
VALUES
(886073387531878404, '接口管理', 0, 1, '/interfaces', 'Interfaces', 'Layout', NULL, 'align-center', b'0', b'0', b'0', NULL, 999, 1, 1, '2026-09-04 02:19:18', NULL, NULL),
(886073596630515719, '证书制作', 886073387531878404, 2, '/interfaces/certificate', 'InterfacesCertificate', 'interfaces/certificate/index', NULL, 'arco', b'0', b'0', b'0', 'interfaces:certificate:list', 999, 1, 1, '2026-09-04 02:20:08', NULL, NULL);

-- rollback DELETE FROM `sys_menu` WHERE `id` IN (886073387531878404, 886073596630515719);
