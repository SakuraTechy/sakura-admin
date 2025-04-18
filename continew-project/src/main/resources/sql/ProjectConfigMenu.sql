SET @parentId = 1911991957660893184;
-- 项目配置管理菜单
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '项目配置管理', 1000, 2, '/project/projectConfig', 'ProjectConfig', 'project/projectConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 项目配置管理按钮
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1911991957660893185, '列表', @parentId, 3, 'project:projectConfig:list', 1, 1, 1, NOW()),
    (1911991957660893186, '详情', @parentId, 3, 'project:projectConfig:get', 2, 1, 1, NOW()),
    (1911991957660893187, '新增', @parentId, 3, 'project:projectConfig:create', 3, 1, 1, NOW()),
    (1911991957660893188, '修改', @parentId, 3, 'project:projectConfig:update', 4, 1, 1, NOW()),
    (1911991957660893189, '删除', @parentId, 3, 'project:projectConfig:delete', 5, 1, 1, NOW()),
    (1911991957665087488, '导出', @parentId, 3, 'project:projectConfig:export', 6, 1, 1, NOW());

