SET @parentId = 1924666739833565184;
-- 自动化管理-节点配置管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '自动化管理-节点配置管理', 1000, 2, '/automation/automationNodeConfig', 'AutomationNodeConfig', 'automation/automationNodeConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 自动化管理-节点配置管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1924666739833565185, '列表', @parentId, 3, 'automation:automationNodeConfig:list', 1, 1, 1, NOW()),
    (1924666739833565186, '详情', @parentId, 3, 'automation:automationNodeConfig:get', 2, 1, 1, NOW()),
    (1924666739833565187, '新增', @parentId, 3, 'automation:automationNodeConfig:create', 3, 1, 1, NOW()),
    (1924666739833565188, '修改', @parentId, 3, 'automation:automationNodeConfig:update', 4, 1, 1, NOW()),
    (1924666739833565189, '删除', @parentId, 3, 'automation:automationNodeConfig:delete', 5, 1, 1, NOW()),
    (1924666739833565190, '导出', @parentId, 3, 'automation:automationNodeConfig:export', 6, 1, 1, NOW());

