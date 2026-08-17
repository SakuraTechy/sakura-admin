-- liquibase formatted sql

-- changeset codex:automation-ui-definition-revision-version-column-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_revision' AND column_name = 'definition_version'
-- comment definitionVersion 是写入并发版本，definitionRevisionId 是不可变执行读取键；两者必须建立唯一绑定。
ALTER TABLE `automation_ui_scene_definition_revision`
    ADD COLUMN `definition_version` bigint(20) unsigned DEFAULT NULL COMMENT '绑定的场景定义版本' AFTER `revision_no`;

UPDATE `automation_ui_scene_definition_revision`
SET `definition_version` = `revision_no`
WHERE `definition_version` IS NULL;

ALTER TABLE `automation_ui_scene_definition_revision`
    MODIFY COLUMN `definition_version` bigint(20) unsigned NOT NULL COMMENT '绑定的场景定义版本';

-- rollback ALTER TABLE `automation_ui_scene_definition_revision` DROP COLUMN `definition_version`;

-- changeset codex:automation-ui-definition-revision-version-index-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_revision' AND index_name = 'uk_automation_ui_scene_definition_version'
CREATE UNIQUE INDEX `uk_automation_ui_scene_definition_version`
    ON `automation_ui_scene_definition_revision` (`scene_id`, `definition_version`);

-- rollback ALTER TABLE `automation_ui_scene_definition_revision` DROP INDEX `uk_automation_ui_scene_definition_version`;

-- changeset codex:automation-ui-definition-revision-content-hash-index-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_revision' AND index_name = 'uk_automation_ui_scene_definition_hash' AND non_unique = 0
-- comment 相同内容允许对应不同 definitionVersion；hash 只用于校验，不再作为 revision 身份。
ALTER TABLE `automation_ui_scene_definition_revision`
    DROP INDEX `uk_automation_ui_scene_definition_hash`,
    ADD INDEX `idx_automation_ui_scene_definition_hash` (`scene_id`, `content_hash`);

-- rollback ALTER TABLE `automation_ui_scene_definition_revision` DROP INDEX `idx_automation_ui_scene_definition_hash`, ADD UNIQUE INDEX `uk_automation_ui_scene_definition_hash` (`scene_id`, `content_hash`);

-- changeset codex:automation-ui-definition-revision-content-hash-drop-unique-repair-20260810
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:1 SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_revision' AND index_name = 'uk_automation_ui_scene_definition_hash' AND non_unique = 0
-- comment 修复旧 changeset 因复合索引统计为两行而被误标 MARK_RAN 的环境；相同快照必须允许绑定不同 definitionVersion。
ALTER TABLE `automation_ui_scene_definition_revision`
    DROP INDEX `uk_automation_ui_scene_definition_hash`;

-- rollback CREATE UNIQUE INDEX `uk_automation_ui_scene_definition_hash` ON `automation_ui_scene_definition_revision` (`scene_id`, `content_hash`);

-- changeset codex:automation-ui-definition-revision-content-hash-add-normal-repair-20260810
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_revision' AND index_name = 'idx_automation_ui_scene_definition_hash'
-- comment content_hash 只用于内容校验和检索，不作为 revision 唯一身份。
CREATE INDEX `idx_automation_ui_scene_definition_hash`
    ON `automation_ui_scene_definition_revision` (`scene_id`, `content_hash`);

-- rollback ALTER TABLE `automation_ui_scene_definition_revision` DROP INDEX `idx_automation_ui_scene_definition_hash`;

-- changeset codex:automation-infrastructure-task-owner-digest-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'owner_user_id'
-- comment 任务对象级授权和 payload 冲突检查不能只依赖通用权限或不可预测 taskId。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `owner_user_id` bigint(20) DEFAULT NULL COMMENT '任务所属执行主体' AFTER `execution_id`,
    ADD COLUMN `payload_digest` char(64) DEFAULT NULL COMMENT '规范化任务输入 SHA-256' AFTER `idempotency_key`;

CREATE INDEX `idx_automation_infra_task_owner_execution`
    ON `automation_infrastructure_task` (`owner_user_id`, `execution_id`);

-- 历史任务没有可证明的主体归属，保持 NULL 并由服务端拒绝访问，不能猜测回填为当前用户。
-- rollback ALTER TABLE `automation_infrastructure_task` DROP INDEX `idx_automation_infra_task_owner_execution`, DROP COLUMN `payload_digest`, DROP COLUMN `owner_user_id`;

-- changeset codex:automation-infrastructure-task-result-preview-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'result_json'
-- comment 只保存受限结果预览，完整输出和大结果必须通过受鉴权附件提供。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `result_json` JSON DEFAULT NULL COMMENT '基础设施结果预览 JSON' AFTER `result_summary`;

-- rollback ALTER TABLE `automation_infrastructure_task` DROP COLUMN `result_json`;

-- changeset codex:automation-ui-execution-capability-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'execution_capability_digest'
-- comment execution capability 只保存摘要和短时过期时间，不能作为长期登录凭据。
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `execution_capability_digest` char(64) DEFAULT NULL COMMENT '短时 execution capability SHA-256' AFTER `definition_revision_id`,
    ADD COLUMN `execution_capability_expires_at` datetime(3) DEFAULT NULL COMMENT '短时 capability 过期时间' AFTER `execution_capability_digest`;
  
  -- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `execution_capability_expires_at`, DROP COLUMN `execution_capability_digest`;

  --changeset codex:automation-ui-execution-capability-index-20260803
  --preconditions onFail:MARK_RAN onError:HALT
  --precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND index_name = 'idx_automation_ui_execution_capability_expire'
  CREATE INDEX `idx_automation_ui_execution_capability_expire`
      ON `automation_ui_execution` (`execution_capability_expires_at`);

  -- rollback ALTER TABLE `automation_ui_execution` DROP INDEX `idx_automation_ui_execution_capability_expire`;

-- changeset codex:automation-infrastructure-task-execution-identity-20260803
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'step_execution_id'
-- comment 任务只绑定服务端生成的 StepExecution 和不可变 revision，不再把客户端 stepId/version 当执行事实。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `definition_revision_id` bigint(20) DEFAULT NULL COMMENT '不可变定义 revision ID' AFTER `definition_version`,
    ADD COLUMN `step_execution_id` bigint(20) DEFAULT NULL COMMENT '服务端步骤执行实例 ID' AFTER `definition_revision_id`;

CREATE UNIQUE INDEX `uk_automation_infra_task_step_attempt`
    ON `automation_infrastructure_task` (`step_execution_id`, `attempt`);

CREATE INDEX `idx_automation_infra_task_revision`
    ON `automation_infrastructure_task` (`definition_revision_id`);

-- 历史任务保持 NULL，只读审计，不允许重新派发。
-- rollback ALTER TABLE `automation_infrastructure_task` DROP INDEX `idx_automation_infra_task_revision`, DROP INDEX `uk_automation_infra_task_step_attempt`, DROP COLUMN `step_execution_id`, DROP COLUMN `definition_revision_id`;

-- changeset codex:automation-infrastructure-task-unknown-outcome-disposition-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'disposition'
-- comment UNKNOWN_OUTCOME 只能经独立权限人工核验；原始核验说明不入库，只保存摘要和审计主体。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `disposition` varchar(32) DEFAULT NULL COMMENT 'confirmed_succeeded/confirmed_failed' AFTER `cancel_requested_at`,
    ADD COLUMN `disposition_user_id` bigint(20) DEFAULT NULL COMMENT '人工核验主体' AFTER `disposition`,
    ADD COLUMN `disposition_at` datetime(3) DEFAULT NULL COMMENT '人工核验时间' AFTER `disposition_user_id`,
    ADD COLUMN `disposition_note_digest` char(64) DEFAULT NULL COMMENT '核验说明 SHA-256' AFTER `disposition_at`;

INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239500, '处置结果不确定任务', 1933371197518045184, 3,
     'automation:automationUiScene:dispose-infrastructure-unknown-outcome', 13, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` = 1933371197522239500;
-- rollback ALTER TABLE `automation_infrastructure_task` DROP COLUMN `disposition_note_digest`, DROP COLUMN `disposition_at`, DROP COLUMN `disposition_user_id`, DROP COLUMN `disposition`;

-- changeset codex:automation-infrastructure-task-risk-approval-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'risk_level'
-- comment 风险和审批摘要绑定 execution/revision/step/payload；任务表仍不保存命令、SQL 或审批原文。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `risk_level` varchar(32) DEFAULT NULL COMMENT 'read/write/destructive/host-privileged' AFTER `payload_digest`,
    ADD COLUMN `approval_digest` char(64) DEFAULT NULL COMMENT '执行审批事实 SHA-256' AFTER `risk_level`,
    ADD COLUMN `command_template_id` varchar(128) DEFAULT NULL COMMENT '部署侧批准命令模板 ID' AFTER `approval_digest`,
    ADD COLUMN `read_only_transaction` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Agent 强制只读事务' AFTER `command_template_id`;

INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239501, '执行基础设施写操作', 1933371197518045184, 3,
     'automation:automationUiScene:execute-infrastructure-write', 14, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` = 1933371197522239501;
-- rollback ALTER TABLE `automation_infrastructure_task` DROP COLUMN `read_only_transaction`, DROP COLUMN `command_template_id`, DROP COLUMN `approval_digest`, DROP COLUMN `risk_level`;

-- changeset codex:automation-infrastructure-task-approval-time-20260804
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'approval_at'
-- comment 危险基础设施审批摘要绑定审批发生时间，避免同一摘要跨时段复用。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `approval_at` datetime(3) DEFAULT NULL COMMENT '审批发生时间' AFTER `approval_digest`;

-- rollback ALTER TABLE `automation_infrastructure_task` DROP COLUMN `approval_at`;

-- changeset codex:automation-infrastructure-artifact-download-permission-20260804
-- comment 基础设施完整结果附件使用独立权限，Controller 仍会继续校验任务主体与 execution capability。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239502, '下载基础设施结果附件', 1933371197518045184, 3,
     'automation:automationUiScene:download-infrastructure-artifact', 15, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` = 1933371197522239502;

-- changeset codex:automation-ui-execution-diagnostic-switch-20260807
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'operation_diagnostic_v1'
-- comment 将统一执行详情灰度开关冻结到 execution，避免一次执行中途切换项目配置导致详情格式不一致。
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `operation_diagnostic_v1` tinyint(1) NOT NULL DEFAULT 1 COMMENT '该执行是否写入统一执行详情 v1' AFTER `result`;

-- rollback ALTER TABLE `automation_ui_execution` DROP COLUMN `operation_diagnostic_v1`;
