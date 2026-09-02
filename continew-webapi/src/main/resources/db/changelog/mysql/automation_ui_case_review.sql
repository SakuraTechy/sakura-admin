-- liquibase formatted sql

-- changeset codex:automation-ui-case-review-core-20260817
-- comment 用例评审与执行定义分离；评审绑定不可变场景 revision 和版本化用例内容指纹。
CREATE TABLE IF NOT EXISTS `automation_ui_case_review` (
    `id`                     bigint(20) NOT NULL,
    `scene_id`               bigint(20) NOT NULL,
    `case_id`                varchar(128) NOT NULL,
    `definition_revision_id` bigint(20) NOT NULL,
    `definition_version`     bigint(20) unsigned NOT NULL,
    `case_content_hash`      char(64) NOT NULL,
    `hash_schema_version`    varchar(64) NOT NULL,
    `round_no`               int unsigned NOT NULL,
    `status`                 varchar(32) NOT NULL,
    `submitter_id`           bigint(20) NOT NULL,
    `submitted_at`           datetime(3) NOT NULL,
    `required_approvals`     smallint unsigned NOT NULL DEFAULT 1,
    `summary`                varchar(2000) DEFAULT NULL,
    `completed_at`           datetime(3) DEFAULT NULL,
    `version`                bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_user`            bigint(20) DEFAULT NULL,
    `create_time`            datetime(3) NOT NULL,
    `update_user`            bigint(20) DEFAULT NULL,
    `update_time`            datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_case_review_round` (`scene_id`, `case_id`, `round_no`),
    KEY `idx_automation_ui_case_review_current` (`scene_id`, `case_id`, `status`, `submitted_at`),
    KEY `idx_automation_ui_case_review_hash` (`scene_id`, `case_id`, `case_content_hash`),
    KEY `idx_automation_ui_case_review_revision` (`definition_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审单';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_reviewer` (
    `id`               bigint(20) NOT NULL,
    `review_id`        bigint(20) NOT NULL,
    `reviewer_id`      bigint(20) NOT NULL,
    `reviewer_role`    varchar(64) DEFAULT NULL,
    `decision`         varchar(32) NOT NULL DEFAULT 'PENDING',
    `decision_summary` varchar(2000) DEFAULT NULL,
    `decision_at`      datetime(3) DEFAULT NULL,
    `version`          bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_user`      bigint(20) DEFAULT NULL,
    `create_time`      datetime(3) NOT NULL,
    `update_user`      bigint(20) DEFAULT NULL,
    `update_time`      datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_case_review_reviewer` (`review_id`, `reviewer_id`),
    KEY `idx_automation_ui_case_reviewer_queue` (`reviewer_id`, `decision`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审人当前投影';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_comment` (
    `id`                bigint(20) NOT NULL,
    `review_id`         bigint(20) NOT NULL,
    `thread_id`         bigint(20) NOT NULL,
    `parent_id`         bigint(20) DEFAULT NULL,
    `node_type`         varchar(16) NOT NULL DEFAULT 'CASE',
    `step_id`           varchar(128) DEFAULT NULL,
    `field_path`        varchar(255) DEFAULT NULL,
    `severity`          varchar(32) DEFAULT NULL,
    `resolution`        varchar(32) NOT NULL DEFAULT 'OPEN',
    `resolution_type`   varchar(32) DEFAULT NULL,
    `content`           varchar(4000) NOT NULL,
    `resolved_by`       bigint(20) DEFAULT NULL,
    `resolved_at`       datetime(3) DEFAULT NULL,
    `resolution_reason` varchar(1000) DEFAULT NULL,
    `create_user`       bigint(20) NOT NULL,
    `create_time`       datetime(3) NOT NULL,
    `update_user`       bigint(20) DEFAULT NULL,
    `update_time`       datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_automation_ui_case_review_thread` (`review_id`, `thread_id`, `create_time`),
    KEY `idx_automation_ui_case_review_open_comment` (`review_id`, `resolution`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审评论线程';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_check_run` (
    `id`              bigint(20) NOT NULL,
    `review_id`       bigint(20) NOT NULL,
    `trigger_type`    varchar(32) NOT NULL,
    `checker_version` varchar(64) NOT NULL,
    `policy_version`  varchar(64) NOT NULL,
    `status`          varchar(32) NOT NULL,
    `started_at`      datetime(3) NOT NULL,
    `finished_at`     datetime(3) DEFAULT NULL,
    `error_message`   varchar(1000) DEFAULT NULL,
    `create_user`     bigint(20) DEFAULT NULL,
    `create_time`     datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_automation_ui_case_check_run` (`review_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审检查批次';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_check` (
    `id`                 bigint(20) NOT NULL,
    `run_id`             bigint(20) NOT NULL,
    `review_id`          bigint(20) NOT NULL,
    `rule_code`          varchar(128) NOT NULL,
    `result`             varchar(32) NOT NULL,
    `severity`           varchar(32) NOT NULL,
    `effective_severity` varchar(32) NOT NULL,
    `message`            varchar(1000) NOT NULL,
    `anchors_json`       json DEFAULT NULL,
    `evidence_json`      json DEFAULT NULL,
    `waived_by`          bigint(20) DEFAULT NULL,
    `waived_at`          datetime(3) DEFAULT NULL,
    `waiver_reason`      varchar(1000) DEFAULT NULL,
    `checked_at`         datetime(3) NOT NULL,
    `create_user`        bigint(20) DEFAULT NULL,
    `create_time`        datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_case_review_check` (`run_id`, `rule_code`),
    KEY `idx_automation_ui_case_review_check_result` (`review_id`, `result`, `effective_severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审检查结果';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_event` (
    `id`          bigint(20) NOT NULL,
    `review_id`   bigint(20) NOT NULL,
    `event_type`  varchar(64) NOT NULL,
    `actor_id`    bigint(20) NOT NULL,
    `payload_json` json DEFAULT NULL,
    `create_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_automation_ui_case_review_event` (`review_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审追加式审计事件';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_checklist_response` (
    `id`          bigint(20) NOT NULL,
    `review_id`   bigint(20) NOT NULL,
    `reviewer_id` bigint(20) NOT NULL,
    `item_code`   varchar(64) NOT NULL,
    `checked`     tinyint(1) NOT NULL DEFAULT 0,
    `checked_at`  datetime(3) DEFAULT NULL,
    `update_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_case_review_checklist` (`review_id`, `reviewer_id`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例人工评审清单';

CREATE TABLE IF NOT EXISTS `automation_ui_case_definition_audit` (
    `id`                  bigint(20) NOT NULL,
    `scene_id`            bigint(20) NOT NULL,
    `case_id`             varchar(128) NOT NULL,
    `definition_version`  bigint(20) unsigned NOT NULL,
    `case_content_hash`   char(64) DEFAULT NULL,
    `hash_schema_version` varchar(64) DEFAULT NULL,
    `change_type`         varchar(32) NOT NULL,
    `editor_id`           bigint(20) DEFAULT NULL,
    `edited_at`           datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_automation_ui_case_definition_audit` (`scene_id`, `case_id`, `edited_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例定义变更归属审计';

CREATE TABLE IF NOT EXISTS `automation_ui_case_review_policy` (
    `project_id`                    bigint(20) NOT NULL,
    `mode`                          varchar(16) NOT NULL DEFAULT 'OBSERVE',
    `required_approvals`            smallint unsigned NOT NULL DEFAULT 1,
    `execution_evidence_required`   tinyint(1) NOT NULL DEFAULT 0,
    `execution_evidence_max_age_h`  int unsigned NOT NULL DEFAULT 168,
    `review_sla_hours`              int unsigned NOT NULL DEFAULT 48,
    `version`                       bigint(20) unsigned NOT NULL DEFAULT 0,
    `create_user`                   bigint(20) DEFAULT NULL,
    `create_time`                   datetime(3) NOT NULL,
    `update_user`                   bigint(20) DEFAULT NULL,
    `update_time`                   datetime(3) NOT NULL,
    PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审项目策略';

-- rollback DROP TABLE `automation_ui_case_review_policy`;
-- rollback DROP TABLE `automation_ui_case_definition_audit`;
-- rollback DROP TABLE `automation_ui_case_review_checklist_response`;
-- rollback DROP TABLE `automation_ui_case_review_event`;
-- rollback DROP TABLE `automation_ui_case_review_check`;
-- rollback DROP TABLE `automation_ui_case_review_check_run`;
-- rollback DROP TABLE `automation_ui_case_review_comment`;
-- rollback DROP TABLE `automation_ui_case_review_reviewer`;
-- rollback DROP TABLE `automation_ui_case_review`;

-- changeset codex:automation-ui-execution-case-content-hash-column-20260817
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution_case' AND column_name = 'case_content_hash'
-- comment 执行用例直接冻结内容指纹，避免证据查询逐条解析完整场景 revision JSON。
ALTER TABLE `automation_ui_execution_case`
    ADD COLUMN `case_content_hash` char(64) DEFAULT NULL AFTER `case_id`;

-- rollback ALTER TABLE `automation_ui_execution_case` DROP COLUMN `case_content_hash`;

-- changeset codex:automation-ui-execution-case-hash-schema-column-20260817
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution_case' AND column_name = 'hash_schema_version'
ALTER TABLE `automation_ui_execution_case`
    ADD COLUMN `hash_schema_version` varchar(64) DEFAULT NULL AFTER `case_content_hash`;

-- rollback ALTER TABLE `automation_ui_execution_case` DROP COLUMN `hash_schema_version`;

-- changeset codex:automation-ui-execution-case-hash-index-20260817
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution_case' AND index_name = 'idx_automation_ui_execution_case_hash'
CREATE INDEX `idx_automation_ui_execution_case_hash`
    ON `automation_ui_execution_case` (`case_content_hash`, `status`, `finished_at`);

-- rollback ALTER TABLE `automation_ui_execution_case` DROP INDEX `idx_automation_ui_execution_case_hash`;

-- changeset codex:automation-ui-case-review-permissions-20260817
-- comment 评审读写、批准和管理能力独立授权，默认门禁仍为 OBSERVE。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239503, '查看 UI 用例评审', 1933371197518045184, 3,
     'automation:automationUiScene:review:view', 16, 1, 1, NOW()),
    (1933371197522239504, '提交 UI 用例评审', 1933371197518045184, 3,
     'automation:automationUiScene:review:submit', 17, 1, 1, NOW()),
    (1933371197522239505, '评论 UI 用例评审', 1933371197518045184, 3,
     'automation:automationUiScene:review:comment', 18, 1, 1, NOW()),
    (1933371197522239506, '批准 UI 用例评审', 1933371197518045184, 3,
     'automation:automationUiScene:review:approve', 19, 1, 1, NOW()),
    (1933371197522239507, '管理 UI 用例评审', 1933371197518045184, 3,
     'automation:automationUiScene:review:admin', 20, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` BETWEEN 1933371197522239503 AND 1933371197522239507;

-- changeset codex:automation-ui-case-review-sla-column-20260817
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_case_review_policy' AND column_name = 'review_sla_hours'
-- comment 评审 SLA 用于个人队列的临近超时计算，既有项目沿用 48 小时默认值。
ALTER TABLE `automation_ui_case_review_policy`
    ADD COLUMN `review_sla_hours` int unsigned NOT NULL DEFAULT 48 AFTER `execution_evidence_max_age_h`;

-- rollback ALTER TABLE `automation_ui_case_review_policy` DROP COLUMN `review_sla_hours`;

-- changeset codex:automation-ui-case-review-gate-bypass-20260817
-- comment 管理员放行在独立事务中追加审计，保留当时被门禁阻断的精确用例哈希。
CREATE TABLE IF NOT EXISTS `automation_ui_case_review_gate_bypass` (
    `id`                 bigint(20) NOT NULL,
    `project_id`         bigint(20) NOT NULL,
    `scene_id`           bigint(20) NOT NULL,
    `trigger_type`       varchar(64) NOT NULL,
    `reason`             varchar(1000) NOT NULL,
    `actor_id`           bigint(20) DEFAULT NULL,
    `blocked_cases_json` json NOT NULL,
    `create_time`        datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_automation_ui_case_review_bypass_project` (`project_id`, `create_time`),
    KEY `idx_automation_ui_case_review_bypass_scene` (`scene_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 自动化用例评审门禁放行审计';

-- rollback DROP TABLE `automation_ui_case_review_gate_bypass`;

-- changeset codex:automation-ui-case-review-governance-menu-20260817
-- comment 独立评审队列与度量页面和 UI 场景管理并列；父目录从现有场景菜单动态继承。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_cache`, `is_hidden`,
     `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT
    1933371197522239508, '用例评审', COALESCE((SELECT parent_id FROM `sys_menu` WHERE id = 1933371197518045184), 0),
    2, '/automation/automationCaseReview', 'AutomationAutomationCaseReview',
    'automation/automationCaseReview/index', 'check-square', b'1', b'0',
    'automation:automationUiScene:review:view',
    COALESCE((SELECT sort + 1 FROM `sys_menu` WHERE id = 1933371197518045184), 999), 1, 1, NOW();

-- rollback DELETE FROM `sys_menu` WHERE `id` = 1933371197522239508;
