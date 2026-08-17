-- liquibase formatted sql

-- changeset codex:test-metric-v2-plan-version-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_plan' AND column_name = 'version_id'
-- comment 测试计划增加明确的项目版本维度。
ALTER TABLE `test_plan`
    ADD COLUMN `version_id` bigint DEFAULT NULL COMMENT '项目版本ID' AFTER `project_id`;

-- rollback ALTER TABLE `test_plan` DROP COLUMN `version_id`;

-- changeset codex:test-metric-v2-plan-scope-index-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'test_plan' AND index_name = 'idx_test_plan_metric_scope'
ALTER TABLE `test_plan`
    ADD INDEX `idx_test_plan_metric_scope` (`project_id`, `version_id`, `del_flag`, `create_time`);

-- rollback ALTER TABLE `test_plan` DROP INDEX `idx_test_plan_metric_scope`;

-- changeset codex:test-metric-v2-report-version-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_report' AND column_name = 'version_id'
-- comment 测试报告增加明确的项目版本维度。
ALTER TABLE `test_report`
    ADD COLUMN `version_id` bigint DEFAULT NULL COMMENT '项目版本ID' AFTER `project_id`;

-- rollback ALTER TABLE `test_report` DROP COLUMN `version_id`;

-- changeset codex:test-metric-v2-report-started-at-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_report' AND column_name = 'started_at'
ALTER TABLE `test_report`
    ADD COLUMN `started_at` datetime(3) DEFAULT NULL COMMENT '报告开始时间' AFTER `status`;

-- rollback ALTER TABLE `test_report` DROP COLUMN `started_at`;

-- changeset codex:test-metric-v2-report-finished-at-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_report' AND column_name = 'finished_at'
ALTER TABLE `test_report`
    ADD COLUMN `finished_at` datetime(3) DEFAULT NULL COMMENT '报告完成时间' AFTER `started_at`;

-- rollback ALTER TABLE `test_report` DROP COLUMN `finished_at`;

-- changeset codex:test-metric-v2-report-scope-index-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'test_report' AND index_name = 'idx_test_report_metric_scope'
ALTER TABLE `test_report`
    ADD INDEX `idx_test_report_metric_scope` (`project_id`, `version_id`, `del_flag`, `finished_at`);

-- rollback ALTER TABLE `test_report` DROP INDEX `idx_test_report_metric_scope`;

-- changeset codex:test-metric-v2-report-scene-20260804
-- comment 保存每份报告实际执行的场景与定义版本快照，历史范围不再依赖 JSON。
CREATE TABLE IF NOT EXISTS `test_report_scene` (
    `id`                     bigint NOT NULL AUTO_INCREMENT,
    `test_report_id`         bigint NOT NULL,
    `test_plan_id`           bigint DEFAULT NULL,
    `project_id`             bigint NOT NULL,
    `version_id`             bigint NOT NULL,
    `module_id`              bigint DEFAULT NULL,
    `scene_id`               bigint NOT NULL,
    `scene_key`              varchar(128) NOT NULL,
    `scene_name`             varchar(255) DEFAULT NULL,
    `scene_level`            varchar(32) DEFAULT NULL,
    `definition_revision_id` bigint DEFAULT NULL,
    `sort`                   int unsigned NOT NULL DEFAULT 0,
    `create_user`            bigint DEFAULT NULL,
    `create_time`            datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_test_report_scene` (`test_report_id`, `scene_id`),
    KEY `idx_test_report_scene_scope` (`project_id`, `version_id`, `test_report_id`),
    KEY `idx_test_report_scene_scene` (`scene_id`, `test_report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试报告场景执行范围快照';

-- rollback DROP TABLE `test_report_scene`;

-- changeset codex:test-metric-v2-execution-project-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'project_id'
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `project_id` bigint DEFAULT NULL COMMENT '执行时项目ID快照' AFTER `scene_key`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `project_id`;

-- changeset codex:test-metric-v2-execution-version-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'version_id'
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `version_id` bigint DEFAULT NULL COMMENT '执行时项目版本ID快照' AFTER `project_id`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `version_id`;

-- changeset codex:test-metric-v2-execution-module-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'module_id'
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `module_id` bigint DEFAULT NULL COMMENT '执行时模块ID快照' AFTER `version_id`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `module_id`;

-- changeset codex:test-metric-v2-execution-level-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'scene_level'
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `scene_level` varchar(32) DEFAULT NULL COMMENT '执行时场景等级快照' AFTER `module_id`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `scene_level`;

-- changeset codex:test-metric-v2-execution-run-key-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'run_key'
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `run_key` varchar(192) DEFAULT NULL COMMENT '跨场景运行分组键' AFTER `batch_id`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `run_key`;

-- changeset codex:test-metric-v2-execution-dimension-quality-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'dimension_quality'
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `dimension_quality` varchar(32) NOT NULL DEFAULT 'MISSING' COMMENT '维度快照质量' AFTER `run_key`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `dimension_quality`;

-- changeset codex:test-metric-v2-execution-scope-index-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND index_name = 'idx_ui_execution_metric_scope'
ALTER TABLE `automation_ui_execution`
    ADD INDEX `idx_ui_execution_metric_scope` (`project_id`, `version_id`, `finished_at`, `status`, `result`);

-- rollback ALTER TABLE `automation_ui_execution` DROP INDEX `idx_ui_execution_metric_scope`;

-- changeset codex:test-metric-v2-execution-dimension-index-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND index_name = 'idx_ui_execution_metric_dimension'
ALTER TABLE `automation_ui_execution`
    ADD INDEX `idx_ui_execution_metric_dimension` (`project_id`, `version_id`, `execution_engine`, `trigger_type`, `finished_at`);

-- rollback ALTER TABLE `automation_ui_execution` DROP INDEX `idx_ui_execution_metric_dimension`;

-- changeset codex:test-metric-v2-execution-run-index-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND index_name = 'idx_ui_execution_run_scene'
ALTER TABLE `automation_ui_execution`
    ADD INDEX `idx_ui_execution_run_scene` (`run_key`, `scene_id`);

-- rollback ALTER TABLE `automation_ui_execution` DROP INDEX `idx_ui_execution_run_scene`;

-- changeset codex:test-metric-v2-daily-20260804
CREATE TABLE IF NOT EXISTS `test_metric_daily` (
    `metric_date`              date NOT NULL,
    `project_id`               bigint NOT NULL,
    `version_id`               bigint NOT NULL DEFAULT 0,
    `execution_engine`         varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    `trigger_type`             varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    `environment_id`           bigint NOT NULL DEFAULT 0,
    `run_started_count`        bigint unsigned NOT NULL DEFAULT 0,
    `run_completed_count`      bigint unsigned NOT NULL DEFAULT 0,
    `scene_execution_count`    bigint unsigned NOT NULL DEFAULT 0,
    `scene_pass_count`         bigint unsigned NOT NULL DEFAULT 0,
    `scene_fail_count`         bigint unsigned NOT NULL DEFAULT 0,
    `scene_skip_count`         bigint unsigned NOT NULL DEFAULT 0,
    `scene_cancel_count`       bigint unsigned NOT NULL DEFAULT 0,
    `scene_infra_fail_count`   bigint unsigned NOT NULL DEFAULT 0,
    `case_total`               bigint unsigned NOT NULL DEFAULT 0,
    `case_pass`                bigint unsigned NOT NULL DEFAULT 0,
    `case_fail`                bigint unsigned NOT NULL DEFAULT 0,
    `case_skip`                bigint unsigned NOT NULL DEFAULT 0,
    `step_total`               bigint unsigned NOT NULL DEFAULT 0,
    `step_pass`                bigint unsigned NOT NULL DEFAULT 0,
    `step_fail`                bigint unsigned NOT NULL DEFAULT 0,
    `step_skip`                bigint unsigned NOT NULL DEFAULT 0,
    `duration_total_ms`        decimal(20,0) NOT NULL DEFAULT 0,
    `duration_sample_count`    bigint unsigned NOT NULL DEFAULT 0,
    `duration_histogram`       json DEFAULT NULL,
    `histogram_version`        smallint unsigned NOT NULL DEFAULT 1,
    `source_max_execution_id`  bigint DEFAULT NULL,
    `aggregation_time`         datetime(3) NOT NULL,
    PRIMARY KEY (`metric_date`, `project_id`, `version_id`, `execution_engine`, `trigger_type`, `environment_id`),
    KEY `idx_test_metric_daily_scope` (`project_id`, `version_id`, `metric_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试度量日汇总';

-- rollback DROP TABLE `test_metric_daily`;

-- changeset codex:test-metric-v2-scene-daily-20260804
CREATE TABLE IF NOT EXISTS `test_metric_scene_daily` (
    `metric_date`              date NOT NULL,
    `project_id`               bigint NOT NULL,
    `version_id`               bigint NOT NULL DEFAULT 0,
    `module_id`                bigint NOT NULL DEFAULT 0,
    `scene_id`                 bigint NOT NULL,
    `scene_level`              varchar(32) NOT NULL DEFAULT 'UNSPECIFIED',
    `execution_engine`         varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    `trigger_type`             varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    `environment_id`           bigint NOT NULL DEFAULT 0,
    `execution_count`          int unsigned NOT NULL DEFAULT 0,
    `pass_count`               int unsigned NOT NULL DEFAULT 0,
    `fail_count`               int unsigned NOT NULL DEFAULT 0,
    `skip_count`               int unsigned NOT NULL DEFAULT 0,
    `cancel_count`             int unsigned NOT NULL DEFAULT 0,
    `infra_fail_count`         int unsigned NOT NULL DEFAULT 0,
    `duration_total_ms`        decimal(20,0) NOT NULL DEFAULT 0,
    `last_result`              varchar(32) DEFAULT NULL,
    `source_max_execution_id`  bigint DEFAULT NULL,
    `aggregation_time`         datetime(3) NOT NULL,
    PRIMARY KEY (`metric_date`, `project_id`, `version_id`, `module_id`, `scene_id`, `scene_level`, `execution_engine`, `trigger_type`, `environment_id`),
    KEY `idx_test_metric_scene_scope` (`project_id`, `version_id`, `metric_date`, `module_id`),
    KEY `idx_test_metric_scene_dimension` (`project_id`, `version_id`, `execution_engine`, `trigger_type`, `metric_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试场景日度量快照';

-- rollback DROP TABLE `test_metric_scene_daily`;

-- changeset codex:test-metric-v2-inventory-daily-20260804
CREATE TABLE IF NOT EXISTS `test_metric_inventory_daily` (
    `metric_date`          date NOT NULL,
    `project_id`           bigint NOT NULL,
    `version_id`           bigint NOT NULL DEFAULT 0,
    `module_id`            bigint NOT NULL DEFAULT 0,
    `scene_level`          varchar(32) NOT NULL DEFAULT 'UNSPECIFIED',
    `eligible_scene_count` bigint unsigned NOT NULL DEFAULT 0,
    `aggregation_time`     datetime(3) NOT NULL,
    PRIMARY KEY (`metric_date`, `project_id`, `version_id`, `module_id`, `scene_level`),
    KEY `idx_test_metric_inventory_scope` (`project_id`, `version_id`, `metric_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试度量库存日快照';

-- rollback DROP TABLE `test_metric_inventory_daily`;

-- changeset codex:test-metric-v2-aggregation-state-20260804
CREATE TABLE IF NOT EXISTS `test_metric_aggregation_state` (
    `metric_date`             date NOT NULL,
    `project_id`              bigint NOT NULL,
    `version_id`              bigint NOT NULL DEFAULT 0,
    `source_max_execution_id` bigint DEFAULT NULL,
    `source_execution_count`  bigint unsigned NOT NULL DEFAULT 0,
    `aggregated_execution_count` bigint unsigned NOT NULL DEFAULT 0,
    `status`                  varchar(32) NOT NULL,
    `error_message`           varchar(1000) DEFAULT NULL,
    `aggregation_time`        datetime(3) NOT NULL,
    PRIMARY KEY (`metric_date`, `project_id`, `version_id`),
    KEY `idx_test_metric_aggregation_status` (`status`, `aggregation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试度量汇总水位与对账状态';

-- rollback DROP TABLE `test_metric_aggregation_state`;

-- changeset codex:test-metric-v2-execution-metric-time-20260811
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'metric_time'
-- comment 统一执行归属时间并为在线度量范围查询提供可索引列。
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `metric_time` datetime(3)
        GENERATED ALWAYS AS (COALESCE(`finished_at`, `started_at`, `create_time`)) STORED
        COMMENT '度量归属时间' AFTER `finished_at`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `metric_time`;

-- changeset codex:test-metric-v2-execution-project-time-index-20260811
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND index_name = 'idx_ui_execution_metric_project_time'
ALTER TABLE `automation_ui_execution`
    ADD INDEX `idx_ui_execution_metric_project_time` (`project_id`, `metric_time`);

-- rollback ALTER TABLE `automation_ui_execution` DROP INDEX `idx_ui_execution_metric_project_time`;

-- changeset codex:test-metric-v2-execution-version-time-index-20260811
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND index_name = 'idx_ui_execution_metric_version_time'
ALTER TABLE `automation_ui_execution`
    ADD INDEX `idx_ui_execution_metric_version_time` (`project_id`, `version_id`, `metric_time`);

-- rollback ALTER TABLE `automation_ui_execution` DROP INDEX `idx_ui_execution_metric_version_time`;

-- changeset codex:test-metric-v2-daily-other-count-20260811
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_metric_daily' AND column_name = 'scene_other_count'
ALTER TABLE `test_metric_daily`
    ADD COLUMN `scene_other_count` bigint unsigned NOT NULL DEFAULT 0 AFTER `scene_infra_fail_count`;

-- rollback ALTER TABLE `test_metric_daily` DROP COLUMN `scene_other_count`;

-- changeset codex:test-metric-v2-scene-daily-other-count-20260811
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_metric_scene_daily' AND column_name = 'other_count'
ALTER TABLE `test_metric_scene_daily`
    ADD COLUMN `other_count` int unsigned NOT NULL DEFAULT 0 AFTER `infra_fail_count`;

-- rollback ALTER TABLE `test_metric_scene_daily` DROP COLUMN `other_count`;
