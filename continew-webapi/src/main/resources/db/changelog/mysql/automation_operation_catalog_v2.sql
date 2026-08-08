-- liquibase formatted sql

-- changeset codex:automation-operation-catalog-v2-20260801
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'project_config' AND column_name = 'automation_operation_catalog_v2'
-- comment UI 自动化操作目录 v2 灰度开关；字段已存在时跳过。
ALTER TABLE `project_config`
    ADD COLUMN `automation_operation_catalog_v2` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'UI 自动化操作目录 v2 灰度开关';

-- rollback ALTER TABLE `project_config` DROP COLUMN `automation_operation_catalog_v2`;

-- changeset codex:operation-diagnostic-v1-20260807
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'project_config' AND column_name = 'operation_diagnostic_v1'
-- comment UI 自动化统一执行详情 v1 灰度开关；字段已存在时跳过。
ALTER TABLE `project_config`
    ADD COLUMN `operation_diagnostic_v1` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'UI 自动化统一执行详情 v1 灰度开关';

-- rollback ALTER TABLE `project_config` DROP COLUMN `operation_diagnostic_v1`;
