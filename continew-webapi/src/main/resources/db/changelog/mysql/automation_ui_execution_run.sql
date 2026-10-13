-- liquibase formatted sql

-- changeset codex:automation-ui-execution-run-20261008
-- comment 整次调试任务与真实场景子批次分离，不复用测试报告或 Runner batchId
CREATE TABLE `automation_ui_execution_run` (
    `id` bigint NOT NULL,
    `project_environment_id` bigint NOT NULL,
    `execution_engine` varchar(32) NOT NULL,
    `execute_user_id` bigint NOT NULL,
    `execute_name` varchar(128) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `finished_at` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化整次调试任务';

CREATE TABLE `automation_ui_execution_run_scene` (
    `run_id` bigint NOT NULL,
    `scene_id` bigint NOT NULL,
    `scene_order` int NOT NULL,
    `batch_id` varchar(128) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'queued',
    PRIMARY KEY (`run_id`, `scene_id`),
    UNIQUE KEY `uk_execution_run_scene_batch` (`scene_id`, `batch_id`),
    KEY `idx_execution_run_scene_order` (`run_id`, `scene_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调试任务有序场景清单，未启动和取消项也保留';
