SET @parentId = 1928023875242184704;
-- 自动化管理-环境配置管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '自动化管理-环境配置管理', 1000, 2, '/automation/automationEnvironmentConfig', 'AutomationEnvironmentConfig', 'automation/automationEnvironmentConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 自动化管理-环境配置管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1928023875242184705, '列表', @parentId, 3, 'automation:automationEnvironmentConfig:list', 1, 1, 1, NOW()),
    (1928023875242184706, '详情', @parentId, 3, 'automation:automationEnvironmentConfig:get', 2, 1, 1, NOW()),
    (1928023875242184707, '新增', @parentId, 3, 'automation:automationEnvironmentConfig:create', 3, 1, 1, NOW()),
    (1928023875242184708, '修改', @parentId, 3, 'automation:automationEnvironmentConfig:update', 4, 1, 1, NOW()),
    (1928023875242184709, '删除', @parentId, 3, 'automation:automationEnvironmentConfig:delete', 5, 1, 1, NOW()),
    (1928023875242184710, '导出', @parentId, 3, 'automation:automationEnvironmentConfig:export', 6, 1, 1, NOW());

