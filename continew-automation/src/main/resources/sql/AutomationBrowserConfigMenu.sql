SET @parentId = 1927993812610310144;
-- 自动化管理-浏览器配置管理菜单
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '自动化管理-浏览器配置管理', 1000, 2, '/automation/automationBrowserConfig', 'AutomationBrowserConfig', 'automation/automationBrowserConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 自动化管理-浏览器配置管理按钮
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1927993812610310145, '列表', @parentId, 3, 'automation:automationBrowserConfig:list', 1, 1, 1, NOW()),
    (1927993812610310146, '详情', @parentId, 3, 'automation:automationBrowserConfig:get', 2, 1, 1, NOW()),
    (1927993812610310147, '新增', @parentId, 3, 'automation:automationBrowserConfig:create', 3, 1, 1, NOW()),
    (1927993812610310148, '修改', @parentId, 3, 'automation:automationBrowserConfig:update', 4, 1, 1, NOW()),
    (1927993812610310149, '删除', @parentId, 3, 'automation:automationBrowserConfig:delete', 5, 1, 1, NOW()),
    (1927993812610310150, '导出', @parentId, 3, 'automation:automationBrowserConfig:export', 6, 1, 1, NOW());

