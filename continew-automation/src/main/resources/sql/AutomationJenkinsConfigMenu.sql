SET @parentId = 1924389315099066368;
-- 自动化管理-Jenkins配置管理菜单
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '自动化管理-Jenkins配置管理', 1000, 2, '/automation/automationJenkinsConfig', 'AutomationJenkinsConfig', 'automation/automationJenkinsConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 自动化管理-Jenkins配置管理按钮
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1924389315099066369, '列表', @parentId, 3, 'automation:automationJenkinsConfig:list', 1, 1, 1, NOW()),
    (1924389315099066370, '详情', @parentId, 3, 'automation:automationJenkinsConfig:get', 2, 1, 1, NOW()),
    (1924389315099066371, '新增', @parentId, 3, 'automation:automationJenkinsConfig:create', 3, 1, 1, NOW()),
    (1924389315099066372, '修改', @parentId, 3, 'automation:automationJenkinsConfig:update', 4, 1, 1, NOW()),
    (1924389315099066373, '删除', @parentId, 3, 'automation:automationJenkinsConfig:delete', 5, 1, 1, NOW()),
    (1924389315099066374, '导出', @parentId, 3, 'automation:automationJenkinsConfig:export', 6, 1, 1, NOW());

