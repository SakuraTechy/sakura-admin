-- liquibase formatted sql

-- changeset codex:automation-playwright-job-20260722
-- comment Playwright Runner 任务持久化记录
CREATE TABLE IF NOT EXISTS `automation_playwright_job` (
    `id`                     bigint(20)   NOT NULL COMMENT 'ID',
    `job_id`                 varchar(64)  NOT NULL COMMENT 'Runner Job ID',
    `scene_key`              varchar(128) DEFAULT NULL COMMENT '场景标识',
    `case_id`                varchar(128) DEFAULT NULL COMMENT '用例 ID',
    `case_key`               varchar(255) DEFAULT NULL COMMENT 'Runner 用例标识',
    `batch_id`               varchar(128) DEFAULT NULL COMMENT '执行批次 ID',
    `execution_id`           varchar(128) DEFAULT NULL COMMENT '执行 ID',
    `execution_type`         varchar(64)  NOT NULL COMMENT '执行类型',
    `project_environment_id` bigint(20)   DEFAULT NULL COMMENT '产品环境 ID',
    `executor_node`          varchar(255) DEFAULT NULL COMMENT '执行 admin 节点',
    `status`                 varchar(32)  NOT NULL COMMENT '任务状态',
    `exit_code`              int          DEFAULT NULL COMMENT 'Runner 退出码',
    `error_code`             varchar(64)  DEFAULT NULL COMMENT '稳定错误码',
    `error_message`          text         DEFAULT NULL COMMENT '错误信息',
    `started_at`             datetime     DEFAULT NULL COMMENT '开始时间',
    `finished_at`            datetime     DEFAULT NULL COMMENT '结束时间',
    `heartbeat_at`           datetime     DEFAULT NULL COMMENT '最后心跳时间',
    `artifact_file_ids`      text         DEFAULT NULL COMMENT 'artifact 类型到系统文件 ID 的 JSON 映射',
    `artifact_urls`          text         DEFAULT NULL COMMENT 'artifact 受保护 URL 的 JSON 映射',
    `create_user`            bigint(20)   DEFAULT NULL COMMENT '创建人',
    `create_time`            datetime     NOT NULL COMMENT '创建时间',
    `update_user`            bigint(20)   DEFAULT NULL COMMENT '修改人',
    `update_time`            datetime     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_automation_playwright_job_job_id` (`job_id`),
    INDEX `idx_automation_playwright_job_status_heartbeat` (`status`, `heartbeat_at`),
    INDEX `idx_automation_playwright_job_batch_id` (`batch_id`),
    INDEX `idx_automation_playwright_job_execution_id` (`execution_id`),
    INDEX `idx_automation_playwright_job_scene_case` (`scene_key`, `case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Playwright Runner 任务记录';
