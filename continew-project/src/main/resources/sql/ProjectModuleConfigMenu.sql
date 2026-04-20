SET @parentId = 1930923819557027840;
-- 项目管理-模块配置管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '项目管理-模块配置管理', 1000, 2, '/project/projectModuleConfig', 'ProjectModuleConfig', 'project/projectModuleConfig/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 项目管理-模块配置管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1930923819557027841, '列表', @parentId, 3, 'project:projectModuleConfig:list', 1, 1, 1, NOW()),
    (1930923819557027842, '详情', @parentId, 3, 'project:projectModuleConfig:get', 2, 1, 1, NOW()),
    (1930923819557027843, '新增', @parentId, 3, 'project:projectModuleConfig:create', 3, 1, 1, NOW()),
    (1930923819557027844, '修改', @parentId, 3, 'project:projectModuleConfig:update', 4, 1, 1, NOW()),
    (1930923819557027845, '删除', @parentId, 3, 'project:projectModuleConfig:delete', 5, 1, 1, NOW()),
    (1930923819557027846, '导出', @parentId, 3, 'project:projectModuleConfig:export', 6, 1, 1, NOW());

