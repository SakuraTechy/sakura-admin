-- liquibase formatted sql

-- changeset sakura:automation-menu-repair-20260822
-- comment 将历史自动化权限从已废弃的菜单 ID 迁移到生产环境实际使用的 UI 自动化菜单。
UPDATE `sys_menu`
SET `parent_id` = 721370537913221325
WHERE `parent_id` = 1933371197518045184;

-- changeset codex:automation-menu-permission-contract-20260831
-- comment 统一自动化模块权限码并修复历史按钮菜单父节点，避免角色授权后接口仍返回 403。
UPDATE `sys_menu`
SET `permission` = CASE `permission`
    WHEN 'project:AutomationProjectConfig:create' THEN 'automation:automationProjectConfig:create'
    WHEN 'project:AutomationProjectConfig:update' THEN 'automation:automationProjectConfig:update'
    WHEN 'project:AutomationProjectConfig:delete' THEN 'automation:automationProjectConfig:delete'
    WHEN 'project:AutomationProjectConfig:export' THEN 'automation:automationProjectConfig:export'
    WHEN 'project:AutomationJenkinsConfig:create' THEN 'automation:automationJenkinsConfig:create'
    WHEN 'project:AutomationJenkinsConfig:update' THEN 'automation:automationJenkinsConfig:update'
    WHEN 'project:AutomationJenkinsConfig:delete' THEN 'automation:automationJenkinsConfig:delete'
    WHEN 'project:AutomationJenkinsConfig:export' THEN 'automation:automationJenkinsConfig:export'
    WHEN 'project:automationNodeConfig:create' THEN 'automation:automationNodeConfig:create'
    WHEN 'project:automationNodeConfig:update' THEN 'automation:automationNodeConfig:update'
    WHEN 'project:automationNodeConfig:delete' THEN 'automation:automationNodeConfig:delete'
    WHEN 'project:automationNodeConfig:export' THEN 'automation:automationNodeConfig:export'
    WHEN 'project:automationNodeConfig:sync' THEN 'automation:automationNodeConfig:sync'
    WHEN 'automation:projectEnvironmentConfig:list' THEN 'automation:automationEnvironmentConfig:list'
    WHEN 'automation:AutomationEnvironmentConfig:export' THEN 'automation:automationEnvironmentConfig:export'
    WHEN 'automation:AutomationBrowserConfig:export' THEN 'automation:automationBrowserConfig:export'
    WHEN 'automation:AutomationUiScene:export' THEN 'automation:automationUiScene:export'
    ELSE `permission`
END
WHERE `permission` IN (
    'project:AutomationProjectConfig:create', 'project:AutomationProjectConfig:update',
    'project:AutomationProjectConfig:delete', 'project:AutomationProjectConfig:export',
    'project:AutomationJenkinsConfig:create', 'project:AutomationJenkinsConfig:update',
    'project:AutomationJenkinsConfig:delete', 'project:AutomationJenkinsConfig:export',
    'project:automationNodeConfig:create', 'project:automationNodeConfig:update',
    'project:automationNodeConfig:delete', 'project:automationNodeConfig:export',
    'project:automationNodeConfig:sync',
    'automation:AutomationEnvironmentConfig:export', 'automation:AutomationBrowserConfig:export',
    'automation:AutomationUiScene:export'
);

UPDATE `sys_menu`
SET `permission` = 'automation:automationEnvironmentConfig:list'
WHERE `permission` = 'automation:projectEnvironmentConfig:list'
  AND `parent_id` IN (718525015707877379, 1928023875242184704);

UPDATE `sys_menu`
SET `parent_id` = CASE `permission`
    WHEN 'automation:automationProjectConfig:list' THEN 714856826889306127
    WHEN 'automation:automationProjectConfig:get' THEN 714856826889306127
    WHEN 'automation:automationProjectConfig:create' THEN 714856826889306127
    WHEN 'automation:automationProjectConfig:update' THEN 714856826889306127
    WHEN 'automation:automationProjectConfig:delete' THEN 714856826889306127
    WHEN 'automation:automationProjectConfig:export' THEN 714856826889306127
    WHEN 'automation:automationEnvironmentConfig:list' THEN 718525015707877379
    WHEN 'automation:automationEnvironmentConfig:get' THEN 718525015707877379
    WHEN 'automation:automationEnvironmentConfig:create' THEN 718525015707877379
    WHEN 'automation:automationEnvironmentConfig:update' THEN 718525015707877379
    WHEN 'automation:automationEnvironmentConfig:delete' THEN 718525015707877379
    WHEN 'automation:automationEnvironmentConfig:export' THEN 718525015707877379
    WHEN 'automation:automationBrowserConfig:list' THEN 718495661808418821
    WHEN 'automation:automationBrowserConfig:get' THEN 718495661808418821
    WHEN 'automation:automationBrowserConfig:create' THEN 718495661808418821
    WHEN 'automation:automationBrowserConfig:update' THEN 718495661808418821
    WHEN 'automation:automationBrowserConfig:delete' THEN 718495661808418821
    WHEN 'automation:automationBrowserConfig:export' THEN 718495661808418821
    WHEN 'automation:automationNodeConfig:list' THEN 715210734472003594
    WHEN 'automation:automationNodeConfig:get' THEN 715210734472003594
    WHEN 'automation:automationNodeConfig:create' THEN 715210734472003594
    WHEN 'automation:automationNodeConfig:update' THEN 715210734472003594
    WHEN 'automation:automationNodeConfig:delete' THEN 715210734472003594
    WHEN 'automation:automationNodeConfig:export' THEN 715210734472003594
    WHEN 'automation:automationNodeConfig:sync' THEN 715210734472003594
    WHEN 'automation:automationJenkinsConfig:list' THEN 714883660246941836
    WHEN 'automation:automationJenkinsConfig:get' THEN 714883660246941836
    WHEN 'automation:automationJenkinsConfig:create' THEN 714883660246941836
    WHEN 'automation:automationJenkinsConfig:update' THEN 714883660246941836
    WHEN 'automation:automationJenkinsConfig:delete' THEN 714883660246941836
    WHEN 'automation:automationJenkinsConfig:export' THEN 714883660246941836
    ELSE `parent_id`
END
WHERE `permission` IN (
    'automation:automationProjectConfig:list', 'automation:automationProjectConfig:get',
    'automation:automationProjectConfig:create', 'automation:automationProjectConfig:update',
    'automation:automationProjectConfig:delete', 'automation:automationProjectConfig:export',
    'automation:automationEnvironmentConfig:list', 'automation:automationEnvironmentConfig:get',
    'automation:automationEnvironmentConfig:create', 'automation:automationEnvironmentConfig:update',
    'automation:automationEnvironmentConfig:delete', 'automation:automationEnvironmentConfig:export',
    'automation:automationBrowserConfig:list', 'automation:automationBrowserConfig:get',
    'automation:automationBrowserConfig:create', 'automation:automationBrowserConfig:update',
    'automation:automationBrowserConfig:delete', 'automation:automationBrowserConfig:export',
    'automation:automationNodeConfig:list', 'automation:automationNodeConfig:get',
    'automation:automationNodeConfig:create', 'automation:automationNodeConfig:update',
    'automation:automationNodeConfig:delete', 'automation:automationNodeConfig:export',
    'automation:automationNodeConfig:sync', 'automation:automationJenkinsConfig:list',
    'automation:automationJenkinsConfig:get', 'automation:automationJenkinsConfig:create',
    'automation:automationJenkinsConfig:update', 'automation:automationJenkinsConfig:delete',
    'automation:automationJenkinsConfig:export'
);

-- rollback UPDATE `sys_menu` SET `parent_id` = 1924363034915627008
-- WHERE `permission` LIKE 'automation:automationProjectConfig:%';
-- rollback UPDATE `sys_menu` SET `parent_id` = 1928023875242184704
-- WHERE `permission` LIKE 'automation:automationEnvironmentConfig:%';
-- rollback UPDATE `sys_menu` SET `parent_id` = 1927993812610310144
-- WHERE `permission` LIKE 'automation:automationBrowserConfig:%';
-- rollback UPDATE `sys_menu` SET `parent_id` = 1924666739833565184
-- WHERE `permission` LIKE 'automation:automationNodeConfig:%';
-- rollback UPDATE `sys_menu` SET `parent_id` = 1924389315099066368
-- WHERE `permission` LIKE 'automation:automationJenkinsConfig:%';

-- changeset codex:automation-node-sync-permission-20260831
-- comment 为节点配置补齐与前端一致的单独同步权限，授权后可显示并调用同步操作。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239511, '同步', 715210734472003594, 3,
     'automation:automationNodeConfig:sync', 7, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` = 1933371197522239511;

-- changeset codex:automation-operation-permission-menu-20260831
-- comment 补齐 Controller 已校验但历史菜单未登记的操作权限，授权范围仍由角色勾选决定。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239512, '复制场景', 721370537913221325, 3,
     'automation:automationUiScene:copy', 21, 1, 1, NOW()),
    (1933371197522239513, '查询用例树', 721370537913221325, 3,
     'automation:automationUiScene:getCase', 22, 1, 1, NOW()),
    (1933371197522239514, '新增场景用例', 721370537913221325, 3,
     'automation:automationUiScene:addCase', 23, 1, 1, NOW()),
    (1933371197522239515, '修改场景用例', 721370537913221325, 3,
     'automation:automationUiScene:updateCase', 24, 1, 1, NOW()),
    (1933371197522239516, '删除场景用例', 721370537913221325, 3,
     'automation:automationUiScene:deleteCase', 25, 1, 1, NOW()),
    (1933371197522239517, '拖拽场景用例', 721370537913221325, 3,
     'automation:automationUiScene:dragCase', 26, 1, 1, NOW()),
    (1933371197522239519, '修改场景步骤', 721370537913221325, 3,
     'automation:automationUiScene:updateStep', 28, 1, 1, NOW()),
    (1933371197522239520, '删除场景步骤', 721370537913221325, 3,
     'automation:automationUiScene:deleteStep', 29, 1, 1, NOW()),
    (1933371197522239521, '拖拽场景步骤', 721370537913221325, 3,
     'automation:automationUiScene:dragStep', 30, 1, 1, NOW()),
    (1933371197522239522, '删除主机文件', 721370537913221325, 3,
     'automation:automationUiScene:execute-host-file-delete', 31, 1, 1, NOW()),
    (1933371197522239523, '测试数据库配置', 711255299943567371, 3,
     'project:projectDataBaseConfig:test', 7, 1, 1, NOW()),
    (1933371197522239524, '拖拽模块', 721428764654829579, 3,
     'project:projectModuleConfig:drag', 7, 1, 1, NOW()),
    (1933371197522239525, '测试服务器配置', 707246181427707960, 3,
     'project:projectServerConfig:test', 7, 1, 1, NOW());

UPDATE `sys_menu`
SET `parent_id` = CASE `permission`
    WHEN 'automation:automationUiScene:copy' THEN 721370537913221325
    WHEN 'automation:automationUiScene:getCase' THEN 721370537913221325
    WHEN 'automation:automationUiScene:addCase' THEN 721370537913221325
    WHEN 'automation:automationUiScene:updateCase' THEN 721370537913221325
    WHEN 'automation:automationUiScene:deleteCase' THEN 721370537913221325
    WHEN 'automation:automationUiScene:dragCase' THEN 721370537913221325
    WHEN 'automation:automationUiScene:updateStep' THEN 721370537913221325
    WHEN 'automation:automationUiScene:deleteStep' THEN 721370537913221325
    WHEN 'automation:automationUiScene:dragStep' THEN 721370537913221325
    WHEN 'automation:automationUiScene:execute-host-file-delete' THEN 721370537913221325
    WHEN 'project:projectDataBaseConfig:test' THEN 711255299943567371
    WHEN 'project:projectModuleConfig:drag' THEN 721428764654829579
    WHEN 'project:projectServerConfig:test' THEN 707246181427707960
    ELSE `parent_id`
END
WHERE `permission` IN (
    'automation:automationUiScene:copy', 'automation:automationUiScene:getCase',
    'automation:automationUiScene:addCase', 'automation:automationUiScene:updateCase',
    'automation:automationUiScene:deleteCase', 'automation:automationUiScene:dragCase',
    'automation:automationUiScene:updateStep', 'automation:automationUiScene:deleteStep',
    'automation:automationUiScene:dragStep',
    'automation:automationUiScene:execute-host-file-delete', 'project:projectDataBaseConfig:test',
    'project:projectModuleConfig:drag', 'project:projectServerConfig:test'
);

-- rollback DELETE FROM `sys_menu` WHERE `id` BETWEEN 1933371197522239512 AND 1933371197522239525;
