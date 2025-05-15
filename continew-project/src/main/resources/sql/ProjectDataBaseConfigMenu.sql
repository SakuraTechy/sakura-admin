SET @parentId = 1920418555246538752;
-- 项目管理-数据库配置管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '项目管理-数据库配置管理', 1000, 2, '/project/projectDataBaseConfig', 'ProjectDataBaseConfig', 'project/projectDataBaseConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 项目管理-数据库配置管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1920418555246538753, '列表', @parentId, 3, 'project:projectDataBaseConfig:list', 1, 1, 1, NOW()),
    (1920418555246538754, '详情', @parentId, 3, 'project:projectDataBaseConfig:get', 2, 1, 1, NOW()),
    (1920418555246538755, '新增', @parentId, 3, 'project:projectDataBaseConfig:create', 3, 1, 1, NOW()),
    (1920418555246538756, '修改', @parentId, 3, 'project:projectDataBaseConfig:update', 4, 1, 1, NOW()),
    (1920418555246538757, '删除', @parentId, 3, 'project:projectDataBaseConfig:delete', 5, 1, 1, NOW()),
    (1920418555246538758, '导出', @parentId, 3, 'project:projectDataBaseConfig:export', 6, 1, 1, NOW());

