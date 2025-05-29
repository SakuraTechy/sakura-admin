SET @parentId = 1924363034915627008;
-- 自动化管理-项目配置管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '自动化管理-项目配置管理', 1000, 2, '/automation/automationProjectConfig', 'AutomationProjectConfig', 'automation/automationProjectConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 自动化管理-项目配置管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1924363034915627009, '列表', @parentId, 3, 'automation:automationProjectConfig:list', 1, 1, 1, NOW()),
    (1924363034915627010, '详情', @parentId, 3, 'automation:automationProjectConfig:get', 2, 1, 1, NOW()),
    (1924363034915627011, '新增', @parentId, 3, 'automation:automationProjectConfig:create', 3, 1, 1, NOW()),
    (1924363034915627012, '修改', @parentId, 3, 'automation:automationProjectConfig:update', 4, 1, 1, NOW()),
    (1924363034915627013, '删除', @parentId, 3, 'automation:automationProjectConfig:delete', 5, 1, 1, NOW()),
    (1924363034915627014, '导出', @parentId, 3, 'automation:automationProjectConfig:export', 6, 1, 1, NOW());

