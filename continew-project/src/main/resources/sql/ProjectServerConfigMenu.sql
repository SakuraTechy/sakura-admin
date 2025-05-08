SET @parentId = 1919650710623358976;
-- 项目管理-服务器配置管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '项目管理-服务器配置管理', 1000, 2, '/project/projectServerConfig', 'ProjectServerConfig', 'project/projectServerConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 项目管理-服务器配置管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1919650710623358977, '列表', @parentId, 3, 'project:projectServerConfig:list', 1, 1, 1, NOW()),
    (1919650710623358978, '详情', @parentId, 3, 'project:projectServerConfig:get', 2, 1, 1, NOW()),
    (1919650710623358979, '新增', @parentId, 3, 'project:projectServerConfig:create', 3, 1, 1, NOW()),
    (1919650710623358980, '修改', @parentId, 3, 'project:projectServerConfig:update', 4, 1, 1, NOW()),
    (1919650710623358981, '删除', @parentId, 3, 'project:projectServerConfig:delete', 5, 1, 1, NOW()),
    (1919650710623358982, '导出', @parentId, 3, 'project:projectServerConfig:export', 6, 1, 1, NOW());

