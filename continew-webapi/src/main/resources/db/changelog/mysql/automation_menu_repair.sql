-- liquibase formatted sql

-- changeset sakura:automation-menu-repair-20260822
-- comment 将历史自动化权限从已废弃的菜单 ID 迁移到生产环境实际使用的 UI 自动化菜单。
UPDATE `sys_menu`
SET `parent_id` = 721370537913221325
WHERE `parent_id` = 1933371197518045184;
