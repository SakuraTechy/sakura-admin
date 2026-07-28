-- liquibase formatted sql

-- changeset sakura:2001
-- comment 测试管理模块表结构
CREATE TABLE IF NOT EXISTS `test_plan` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `project_id` bigint NOT NULL COMMENT '项目ID',
    `project_name` varchar(64) DEFAULT NULL COMMENT '项目名称',
    `type` varchar(32) DEFAULT NULL COMMENT '计划类型',
    `name` varchar(128) NOT NULL COMMENT '计划名称',
    `abbreviate` varchar(64) DEFAULT NULL COMMENT '计划简称',
    `description` varchar(500) DEFAULT NULL COMMENT '计划描述',
    `member_ids` json DEFAULT NULL COMMENT '成员ID集合',
    `principal_ids` json DEFAULT NULL COMMENT '负责人ID集合',
    `planned_start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
    `planned_end_time` datetime DEFAULT NULL COMMENT '计划结束时间',
    `actual_start_time` datetime DEFAULT NULL COMMENT '实际开始时间',
    `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
    `timed_tasks_config` json DEFAULT NULL COMMENT '定时任务配置',
    `project_config` json DEFAULT NULL COMMENT '项目配置',
    `automation_config` json DEFAULT NULL COMMENT '自动化配置',
    `functional_scene` json DEFAULT NULL COMMENT '功能测试场景',
    `ui_test_scene` json DEFAULT NULL COMMENT 'UI测试场景',
    `scene_count` int NOT NULL DEFAULT 0 COMMENT '场景总数',
    `executed_count` int NOT NULL DEFAULT 0 COMMENT '已执行场景数',
    `passed_count` int NOT NULL DEFAULT 0 COMMENT '通过场景数',
    `test_progress` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '测试进度',
    `run_time` bigint NOT NULL DEFAULT 0 COMMENT '运行耗时(ms)',
    `status` varchar(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态',
    `del_flag` tinyint NOT NULL DEFAULT 1 COMMENT '删除标记 1正常 4删除',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project_id` (`project_id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_user` (`create_user`),
    INDEX `idx_update_user` (`update_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试计划表';

CREATE TABLE IF NOT EXISTS `test_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `project_id` bigint NOT NULL COMMENT '项目ID',
    `project_name` varchar(64) DEFAULT NULL COMMENT '项目名称',
    `version_name` varchar(64) DEFAULT NULL COMMENT '版本名称',
    `test_plan_id` bigint DEFAULT NULL COMMENT '测试计划ID',
    `test_plan_name` varchar(128) DEFAULT NULL COMMENT '测试计划名称',
    `name` varchar(128) NOT NULL COMMENT '报告名称',
    `description` varchar(500) DEFAULT NULL COMMENT '报告描述',
    `trigger_mode` varchar(32) DEFAULT NULL COMMENT '触发方式',
    `execute_mode` varchar(32) DEFAULT NULL COMMENT '执行方式',
    `project_config` json DEFAULT NULL COMMENT '项目配置',
    `automation_config` json DEFAULT NULL COMMENT '自动化配置',
    `runtime_environment` json DEFAULT NULL COMMENT '运行环境',
    `statistic_analysis` json DEFAULT NULL COMMENT '统计分析',
    `run_time` bigint NOT NULL DEFAULT 0 COMMENT '运行耗时(ms)',
    `build_number` varchar(64) DEFAULT NULL COMMENT '构建号',
    `console_url` varchar(500) DEFAULT NULL COMMENT '控制台地址',
    `report_url` varchar(500) DEFAULT NULL COMMENT '报告地址',
    `video_url` varchar(500) DEFAULT NULL COMMENT '视频地址',
    `status` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态',
    `del_flag` tinyint NOT NULL DEFAULT 1 COMMENT '删除标记 1正常 4删除',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project_id` (`project_id`),
    INDEX `idx_test_plan_id` (`test_plan_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_user` (`create_user`),
    INDEX `idx_update_user` (`update_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试报告表';

CREATE TABLE IF NOT EXISTS `test_timed_task` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `test_plan_id` bigint NOT NULL COMMENT '测试计划ID',
    `test_plan_name` varchar(128) DEFAULT NULL COMMENT '测试计划名称',
    `schedule_job_id` bigint DEFAULT NULL COMMENT '调度任务ID',
    `type` varchar(32) DEFAULT NULL COMMENT '任务类型',
    `name` varchar(128) NOT NULL COMMENT '任务名称',
    `description` varchar(500) DEFAULT NULL COMMENT '任务描述',
    `cron_expression` varchar(128) NOT NULL COMMENT 'Cron表达式',
    `misfire_policy` varchar(32) DEFAULT NULL COMMENT 'Misfire策略',
    `allow_concurrent` tinyint NOT NULL DEFAULT 0 COMMENT '是否允许并发',
    `project_environment_id` bigint DEFAULT NULL COMMENT '项目环境ID',
    `automation_environment_id` bigint DEFAULT NULL COMMENT '自动化环境ID',
    `execute_name` varchar(64) DEFAULT NULL COMMENT '执行人',
    `execute_email` varchar(128) DEFAULT NULL COMMENT '执行邮箱',
    `next_execute_time` datetime DEFAULT NULL COMMENT '下次执行时间',
    `status` varchar(32) NOT NULL DEFAULT 'DISABLED' COMMENT '状态',
    `del_flag` tinyint NOT NULL DEFAULT 1 COMMENT '删除标记 1正常 4删除',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    INDEX `idx_test_plan_id` (`test_plan_id`),
    INDEX `idx_schedule_job_id` (`schedule_job_id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_user` (`create_user`),
    INDEX `idx_update_user` (`update_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试定时任务表';

-- changeset sakura:2002
-- comment 测试计划级多执行引擎报告
ALTER TABLE `test_report`
    ADD COLUMN `report_type` varchar(64) NOT NULL DEFAULT 'SELENIUM' COMMENT '报告类型' AFTER `execute_mode`,
    ADD INDEX `idx_report_type` (`report_type`);

ALTER TABLE `test_timed_task`
    ADD COLUMN `execution_engine` varchar(64) NOT NULL DEFAULT 'SELENIUM' COMMENT '执行引擎' AFTER `type`,
    ADD COLUMN `execution_config` json DEFAULT NULL COMMENT '执行引擎配置' AFTER `automation_environment_id`;
