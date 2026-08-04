-- liquibase formatted sql

-- changeset codex:automation-ui-execution-v2-20260726
-- validCheckSum: 1:any
-- comment UI 自动化执行事实与场景最新状态拆分；执行回调不得再更新 automation_ui_scene 大 JSON

CREATE TABLE IF NOT EXISTS `automation_ui_scene_definition_revision` (
    `id`                    bigint(20) NOT NULL COMMENT '定义版本 ID',
    `scene_id`              bigint(20) NOT NULL COMMENT '场景数据库 ID',
    `revision_no`           bigint(20) unsigned NOT NULL COMMENT '场景内单调定义版本',
    `content_hash`          char(64) NOT NULL COMMENT '定义 JSON SHA-256',
    `definition_json`       longtext NOT NULL COMMENT '不可变 CaseDO -> StepDO 定义快照，不包含截图 base64',
    `create_user`           bigint(20) DEFAULT NULL,
    `create_time`           datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_scene_definition_revision` (`scene_id`, `revision_no`),
    UNIQUE KEY `uk_automation_ui_scene_definition_hash` (`scene_id`, `content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化场景不可变定义版本';

CREATE TABLE IF NOT EXISTS `automation_ui_scene_execution_state` (
    `scene_id`              bigint(20) NOT NULL COMMENT '场景数据库 ID',
    `latest_execution_id`   bigint(20) DEFAULT NULL COMMENT '最近一次执行记录 ID',
    `execution_revision`    bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '单调执行版本号',
    `execute_status`        varchar(32) DEFAULT NULL COMMENT '最新执行状态',
    `execute_result`        varchar(32) DEFAULT NULL COMMENT '最新执行结果',
    `case_total`            int unsigned DEFAULT NULL,
    `case_pass`             int unsigned DEFAULT NULL,
    `case_fail`             int unsigned DEFAULT NULL,
    `case_skip`             int unsigned DEFAULT NULL,
    `pass_rate`             varchar(32) DEFAULT NULL,
    `step_total`            int unsigned DEFAULT NULL,
    `step_pass`             int unsigned DEFAULT NULL,
    `step_fail`             int unsigned DEFAULT NULL,
    `step_skip`             int unsigned DEFAULT NULL,
    `last_result`           varchar(32) DEFAULT NULL,
    `version`               bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_time`           datetime(3) NOT NULL,
    `update_time`           datetime(3) NOT NULL,
    PRIMARY KEY (`scene_id`),
    KEY `idx_automation_ui_scene_execution_state_revision` (`scene_id`, `execution_revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化场景最新执行状态（窄表）';

CREATE TABLE IF NOT EXISTS `automation_ui_execution` (
    `id`                    bigint(20) NOT NULL COMMENT '执行记录 ID',
    `execution_key`         varchar(128) NOT NULL COMMENT '跨请求幂等键',
    `scene_id`              bigint(20) NOT NULL,
    `scene_key`             varchar(128) NOT NULL,
    `definition_revision_id` bigint(20) DEFAULT NULL COMMENT '不可变场景定义版本 ID',
    `batch_id`              varchar(128) DEFAULT NULL,
    `test_plan_id`          bigint(20) DEFAULT NULL,
    `test_report_id`        bigint(20) DEFAULT NULL,
    `record_type`           varchar(64) NOT NULL DEFAULT 'playwright-batch',
    `trigger_type`          varchar(32) NOT NULL COMMENT 'manual/test-plan/schedule/jenkins',
    `execution_engine`      varchar(32) NOT NULL COMMENT 'playwright-runner/extension-cdp/selenium',
    `status`                varchar(32) NOT NULL,
    `result`                varchar(32) DEFAULT NULL,
    `execute_user_id`       bigint(20) DEFAULT NULL,
    `execute_username`      varchar(128) DEFAULT NULL,
    `execute_name`          varchar(128) DEFAULT NULL,
    `execute_email`         varchar(255) DEFAULT NULL,
    `project_environment_id` bigint(20) DEFAULT NULL,
    `project_environment_name` varchar(255) DEFAULT NULL,
    `execution_config`      json DEFAULT NULL COMMENT '受控执行配置，不保存凭据',
    `build_number`          int DEFAULT NULL,
    `console_url`           varchar(1000) DEFAULT NULL,
    `test_report_url`       varchar(1000) DEFAULT NULL,
    `case_total`            int unsigned NOT NULL DEFAULT 0,
    `case_pass`             int unsigned NOT NULL DEFAULT 0,
    `case_fail`             int unsigned NOT NULL DEFAULT 0,
    `case_skip`             int unsigned NOT NULL DEFAULT 0,
    `case_cancelled`        int unsigned NOT NULL DEFAULT 0,
    `step_total`            int unsigned NOT NULL DEFAULT 0,
    `step_pass`             int unsigned NOT NULL DEFAULT 0,
    `step_fail`             int unsigned NOT NULL DEFAULT 0,
    `step_skip`             int unsigned NOT NULL DEFAULT 0,
    `executor_node`         varchar(255) DEFAULT NULL,
    `heartbeat_at`          datetime(3) DEFAULT NULL,
    `lease_until`           datetime(3) DEFAULT NULL,
    `cancel_requested`      tinyint(1) NOT NULL DEFAULT 0,
    `retention_hold`        tinyint(1) NOT NULL DEFAULT 0 COMMENT '合规或人工保留，清理任务不得删除',
    `started_at`            datetime(3) DEFAULT NULL,
    `finished_at`           datetime(3) DEFAULT NULL,
    `duration_ms`           bigint(20) unsigned DEFAULT NULL,
    `error_code`            varchar(64) DEFAULT NULL,
    `error_message`         varchar(2000) DEFAULT NULL,
    `summary_json`          json DEFAULT NULL COMMENT '不含 caseResults/steps 的有界兼容摘要',
    `version`               bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_user`           bigint(20) DEFAULT NULL,
    `create_time`           datetime(3) NOT NULL,
    `update_user`           bigint(20) DEFAULT NULL,
    `update_time`           datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_execution_key` (`execution_key`),
    KEY `idx_automation_ui_execution_scene_time` (`scene_id`, `create_time`),
    KEY `idx_automation_ui_execution_scene_batch` (`scene_id`, `batch_id`),
    KEY `idx_automation_ui_execution_report` (`test_report_id`),
    KEY `idx_automation_ui_execution_status_time` (`retention_hold`, `status`, `finished_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化场景执行事实';

CREATE TABLE IF NOT EXISTS `automation_ui_execution_case` (
    `id`                    bigint(20) NOT NULL,
    `execution_id`          bigint(20) NOT NULL,
    `case_id`               varchar(128) NOT NULL,
    `case_key`              varchar(255) DEFAULT NULL,
    `case_execution_key`    varchar(128) DEFAULT NULL,
    `case_name`             varchar(255) DEFAULT NULL,
    `case_index`            int unsigned NOT NULL DEFAULT 0,
    `attempt_no`            smallint unsigned NOT NULL DEFAULT 1,
    `job_id`                varchar(64) DEFAULT NULL,
    `status`                varchar(32) NOT NULL,
    `result`                varchar(32) DEFAULT NULL,
    `execute_status`        varchar(32) DEFAULT NULL,
    `execute_result`        varchar(32) DEFAULT NULL,
    `step_total`            int unsigned NOT NULL DEFAULT 0,
    `step_pass`             int unsigned NOT NULL DEFAULT 0,
    `step_fail`             int unsigned NOT NULL DEFAULT 0,
    `step_skip`             int unsigned NOT NULL DEFAULT 0,
    `started_at`            datetime(3) DEFAULT NULL,
    `finished_at`           datetime(3) DEFAULT NULL,
    `duration_ms`           bigint(20) unsigned DEFAULT NULL,
    `step_duration_ms`      bigint(20) unsigned DEFAULT NULL,
    `wall_clock_duration_ms` bigint(20) unsigned DEFAULT NULL,
    `error_code`            varchar(64) DEFAULT NULL,
    `error_message`         varchar(2000) DEFAULT NULL,
    `summary_json`          json DEFAULT NULL COMMENT '不含 steps 的有界兼容摘要',
    `event_sequence`        bigint(20) unsigned NOT NULL DEFAULT 0,
    `version`               bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_time`           datetime(3) NOT NULL,
    `update_time`           datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_execution_case_attempt` (`execution_id`, `case_id`, `attempt_no`),
    KEY `idx_automation_ui_execution_case_job` (`job_id`),
    KEY `idx_automation_ui_execution_case_status` (`execution_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例执行事实';

CREATE TABLE IF NOT EXISTS `automation_ui_execution_step` (
    `id`                    bigint(20) NOT NULL,
    `execution_case_id`     bigint(20) NOT NULL,
    `step_id`               varchar(128) DEFAULT NULL,
    `source_step_id`        varchar(128) DEFAULT NULL,
    `step_index`            int unsigned NOT NULL,
    `attempt_no`            smallint unsigned NOT NULL DEFAULT 1,
    `action_type`           varchar(64) DEFAULT NULL,
    `step_name`             varchar(255) DEFAULT NULL,
    `description`           varchar(1000) DEFAULT NULL,
    `status`                varchar(32) NOT NULL,
    `duration_ms`           bigint(20) unsigned DEFAULT NULL,
    `locator_source`        varchar(64) DEFAULT NULL,
    `locator_type`          varchar(64) DEFAULT NULL,
    `locator_value`         varchar(2000) DEFAULT NULL,
    `error_code`            varchar(64) DEFAULT NULL,
    `error_message`         varchar(2000) DEFAULT NULL,
    `diagnostics`            json DEFAULT NULL,
    `event_sequence`        bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_time`           datetime(3) NOT NULL,
    `update_time`           datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_execution_step_attempt` (`execution_case_id`, `step_index`, `attempt_no`),
    KEY `idx_automation_ui_execution_step_status` (`execution_case_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化步骤执行事实';

CREATE TABLE IF NOT EXISTS `automation_ui_execution_artifact` (
    `id`                    bigint(20) NOT NULL,
    `execution_id`          bigint(20) NOT NULL,
    `execution_case_id`     bigint(20) NOT NULL DEFAULT 0,
    `execution_step_id`     bigint(20) NOT NULL DEFAULT 0,
    `artifact_type`         varchar(64) NOT NULL,
    `file_id`               bigint(20) DEFAULT NULL,
    `storage_status`        varchar(32) NOT NULL,
    `size_bytes`            bigint(20) unsigned DEFAULT NULL,
    `sha256`                char(64) DEFAULT NULL,
    `expires_at`            datetime(3) DEFAULT NULL,
    `create_time`           datetime(3) NOT NULL,
    `update_time`           datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_execution_artifact` (`execution_id`, `execution_case_id`, `execution_step_id`, `artifact_type`),
    KEY `idx_automation_ui_execution_artifact_expire` (`expires_at`, `storage_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化执行产物引用';

-- changeset codex:automation-playwright-job-retention-index-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_playwright_job' AND index_name = 'idx_automation_playwright_job_retention'
-- comment 仅增加清理扫描索引，不在 changeset 中执行大批量删除或 OPTIMIZE；索引已存在时跳过。
ALTER TABLE `automation_playwright_job`
    ADD INDEX `idx_automation_playwright_job_retention` (`status`, `finished_at`);

-- rollback ALTER TABLE `automation_playwright_job` DROP INDEX `idx_automation_playwright_job_retention`;
