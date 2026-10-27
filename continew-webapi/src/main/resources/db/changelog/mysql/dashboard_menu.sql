-- liquibase formatted sql

-- changeset codex:dashboard-menu-20261027
-- 增加可由菜单管理维护的仪表盘菜单，并为默认角色补充访问权限。
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(900, '仪表盘', 0, 1, '/', 'Dashboard', 'Layout', '/dashboard/workplace', 'dashboard', b'0', b'0', b'0', NULL, 0, 1, 1, NOW()),
(910, '工作台', 900, 2, '/dashboard/workplace', 'Workplace', 'dashboard/workplace/index', NULL, 'desktop', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
(920, '分析页', 900, 2, '/dashboard/analysis', 'Analysis', 'dashboard/analysis/index', NULL, 'insert-chart', b'0', b'0', b'0', NULL, 2, 1, 1, NOW());

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES
(2, 900),
(2, 910),
(2, 920),
(3, 900),
(3, 910),
(3, 920);

-- rollback DELETE FROM `sys_role_menu` WHERE `menu_id` IN (900, 910, 920);
-- rollback DELETE FROM `sys_menu` WHERE `id` IN (900, 910, 920);
