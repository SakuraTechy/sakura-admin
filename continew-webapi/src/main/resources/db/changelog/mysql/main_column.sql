-- liquibase formatted sql

-- changeset liuzhi:20260421-automation-ui-scene-build-number
ALTER TABLE `automation_ui_scene`
    MODIFY COLUMN `build_number` INT NULL COMMENT 'Jenkins构建编号';

-- changeset liuzhi:20260424-automation-project-config-script-path
ALTER TABLE `automation_project_config`
    ADD COLUMN `script_path` varchar(500) DEFAULT NULL COMMENT '脚本根路径' AFTER `description`;

