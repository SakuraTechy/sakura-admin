-- liquibase formatted sql

-- changeset sakura:project-menu-20260822
-- comment 按现网菜单主数据补齐项目管理目录；模块资源目录中的菜单 SQL 不会被 Liquibase 主 changelog 自动执行。
UPDATE `sys_menu`
SET `title` = '版本配置',
    `path` = '/project/versionConfig',
    `name` = 'ProjectVersionConfig',
    `component` = 'project/projectVersionConfig/index',
    `icon` = 'old-version',
    `sort` = 2
WHERE `id` = 1280;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(721428764654829579, '模块配置', 1260, 2, '/project/ModuleConfig', 'ProjectModuleConfig', 'project/projectModuleConfig/index', NULL, 'branch', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
(707246181427707960, '服务器配置', 1260, 2, '/project/serverConfig', 'ProjectServerConfig', 'project/projectServerConfig/index', NULL, 'bulb', b'0', b'0', b'0', NULL, 4, 1, 1, NOW()),
(711255299943567371, '数据库配置', 1260, 2, '/project/dataBaseConfig', 'ProjectDataBaseConfig', 'project/projectDataBaseConfig/index', NULL, 'archive', b'0', b'0', b'0', NULL, 5, 1, 1, NOW()),
(713345400139943940, '环境配置', 1260, 2, '/project/environmentConfig', 'ProjectEnvironmentConfig', 'project/projectEnvironmentConfig/index', NULL, 'bookmark', b'0', b'0', b'0', NULL, 6, 1, 1, NOW());

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(1916757710306131969, '列表', 1280, 3, 'project:projectVersionConfig:list', 1, 1, 1, NOW()),
(1916757710306131970, '详情', 1280, 3, 'project:projectVersionConfig:get', 2, 1, 1, NOW()),
(1916757710306131971, '新增', 1280, 3, 'project:projectVersionConfig:create', 3, 1, 1, NOW()),
(1916757710306131972, '修改', 1280, 3, 'project:projectVersionConfig:update', 4, 1, 1, NOW()),
(1916757710306131973, '删除', 1280, 3, 'project:projectVersionConfig:delete', 5, 1, 1, NOW()),
(1916757710306131974, '导出', 1280, 3, 'project:projectVersionConfig:export', 6, 1, 1, NOW()),
(1930923819557027841, '列表', 721428764654829579, 3, 'project:projectModuleConfig:list', 1, 1, 1, NOW()),
(1930923819557027842, '详情', 721428764654829579, 3, 'project:projectModuleConfig:get', 2, 1, 1, NOW()),
(1930923819557027843, '新增', 721428764654829579, 3, 'project:projectModuleConfig:create', 3, 1, 1, NOW()),
(1930923819557027844, '修改', 721428764654829579, 3, 'project:projectModuleConfig:update', 4, 1, 1, NOW()),
(1930923819557027845, '删除', 721428764654829579, 3, 'project:projectModuleConfig:delete', 5, 1, 1, NOW()),
(1930923819557027846, '导出', 721428764654829579, 3, 'project:projectModuleConfig:export', 6, 1, 1, NOW()),
(1919650710623358977, '列表', 707246181427707960, 3, 'project:projectServerConfig:list', 1, 1, 1, NOW()),
(1919650710623358978, '详情', 707246181427707960, 3, 'project:projectServerConfig:get', 2, 1, 1, NOW()),
(1919650710623358979, '新增', 707246181427707960, 3, 'project:projectServerConfig:create', 3, 1, 1, NOW()),
(1919650710623358980, '修改', 707246181427707960, 3, 'project:projectServerConfig:update', 4, 1, 1, NOW()),
(1919650710623358981, '删除', 707246181427707960, 3, 'project:projectServerConfig:delete', 5, 1, 1, NOW()),
(1919650710623358982, '导出', 707246181427707960, 3, 'project:projectServerConfig:export', 6, 1, 1, NOW()),
(1920418555246538753, '列表', 711255299943567371, 3, 'project:projectDataBaseConfig:list', 1, 1, 1, NOW()),
(1920418555246538754, '详情', 711255299943567371, 3, 'project:projectDataBaseConfig:get', 2, 1, 1, NOW()),
(1920418555246538755, '新增', 711255299943567371, 3, 'project:projectDataBaseConfig:create', 3, 1, 1, NOW()),
(1920418555246538756, '修改', 711255299943567371, 3, 'project:projectDataBaseConfig:update', 4, 1, 1, NOW()),
(1920418555246538757, '删除', 711255299943567371, 3, 'project:projectDataBaseConfig:delete', 5, 1, 1, NOW()),
(1920418555246538758, '导出', 711255299943567371, 3, 'project:projectDataBaseConfig:export', 6, 1, 1, NOW()),
(1922831076226027521, '列表', 713345400139943940, 3, 'project:projectEnvironmentConfig:list', 1, 1, 1, NOW()),
(1922831076226027522, '详情', 713345400139943940, 3, 'project:projectEnvironmentConfig:get', 2, 1, 1, NOW()),
(1922831076226027523, '新增', 713345400139943940, 3, 'project:projectEnvironmentConfig:create', 3, 1, 1, NOW()),
(1922831076226027524, '修改', 713345400139943940, 3, 'project:projectEnvironmentConfig:update', 4, 1, 1, NOW()),
(1922831076226027525, '删除', 713345400139943940, 3, 'project:projectEnvironmentConfig:delete', 5, 1, 1, NOW()),
(1922831076226027526, '导出', 713345400139943940, 3, 'project:projectEnvironmentConfig:export', 6, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` IN (721428764654829579, 707246181427707960, 711255299943567371, 713345400139943940);

-- changeset codex:project-menu-permission-contract-20260831
-- comment 统一项目管理列表权限码，避免角色授权后项目页面仍校验 automation 前缀。
UPDATE `sys_menu`
SET `permission` = CASE `permission`
    WHEN 'automation:projectVersionConfig:list' THEN 'project:projectVersionConfig:list'
    WHEN 'automation:projectServerConfig:list' THEN 'project:projectServerConfig:list'
    WHEN 'automation:projectDataBaseConfig:list' THEN 'project:projectDataBaseConfig:list'
    WHEN 'project:ProjectModuleConfig:export' THEN 'project:projectModuleConfig:export'
    ELSE `permission`
END
WHERE `permission` IN (
    'automation:projectVersionConfig:list', 'automation:projectServerConfig:list',
    'automation:projectDataBaseConfig:list', 'project:ProjectModuleConfig:export'
);

UPDATE `sys_menu`
SET `permission` = 'project:projectEnvironmentConfig:list'
WHERE `permission` = 'automation:projectEnvironmentConfig:list'
  AND `parent_id` = 713345400139943940;

-- rollback UPDATE `sys_menu` SET `permission` = 'automation:projectVersionConfig:list'
-- WHERE `permission` = 'project:projectVersionConfig:list';
-- rollback UPDATE `sys_menu` SET `permission` = 'automation:projectServerConfig:list'
-- WHERE `permission` = 'project:projectServerConfig:list';
-- rollback UPDATE `sys_menu` SET `permission` = 'automation:projectDataBaseConfig:list'
-- WHERE `permission` = 'project:projectDataBaseConfig:list';
-- rollback UPDATE `sys_menu` SET `permission` = 'project:ProjectModuleConfig:export'
-- WHERE `permission` = 'project:projectModuleConfig:export';
