-- changeset codex:automation-operation-catalog-v2-20260801
ALTER TABLE `project_config`
    ADD COLUMN `automation_operation_catalog_v2` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'UI 自动化操作目录 v2 灰度开关';

-- rollback ALTER TABLE `project_config` DROP COLUMN `automation_operation_catalog_v2`;
