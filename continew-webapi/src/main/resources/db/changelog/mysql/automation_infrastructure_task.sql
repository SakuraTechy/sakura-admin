-- liquibase formatted sql

-- changeset codex:project-server-config-binding-key-column-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'project_server_config' AND column_name = 'binding_key'
-- comment 服务器配置稳定绑定键；字段已存在时跳过。
ALTER TABLE `project_server_config`
    ADD COLUMN `binding_key` varchar(128) DEFAULT NULL COMMENT '基础设施步骤稳定绑定键' AFTER `project_id`;

-- rollback ALTER TABLE `project_server_config` DROP COLUMN `binding_key`;

-- changeset codex:project-database-config-binding-key-column-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'project_data_base_config' AND column_name = 'binding_key'
-- comment 数据库配置稳定绑定键；字段已存在时跳过。
ALTER TABLE `project_data_base_config`
    ADD COLUMN `binding_key` varchar(128) DEFAULT NULL COMMENT '基础设施步骤稳定绑定键' AFTER `project_id`;

-- rollback ALTER TABLE `project_data_base_config` DROP COLUMN `binding_key`;

-- changeset codex:project-server-config-binding-key-index-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'project_server_config' AND index_name = 'uk_project_server_config_binding_key'
-- comment 服务器配置绑定键唯一索引；索引已存在时跳过。
CREATE UNIQUE INDEX `uk_project_server_config_binding_key` ON `project_server_config` (`project_id`, `binding_key`);

-- rollback ALTER TABLE `project_server_config` DROP INDEX `uk_project_server_config_binding_key`;

-- changeset codex:project-database-config-binding-key-index-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'project_data_base_config' AND index_name = 'uk_project_data_base_config_binding_key'
-- comment 数据库配置绑定键唯一索引；索引已存在时跳过。
CREATE UNIQUE INDEX `uk_project_data_base_config_binding_key` ON `project_data_base_config` (`project_id`, `binding_key`);

-- rollback ALTER TABLE `project_data_base_config` DROP INDEX `uk_project_data_base_config_binding_key`;

-- changeset codex:automation-infrastructure-task-20260728
-- validCheckSum: 1:any
-- comment 基础设施步骤异步任务和脱敏日志；不保存命令、SQL、连接串和凭据。

CREATE TABLE IF NOT EXISTS `automation_infrastructure_task` (
    `id` bigint(20) NOT NULL COMMENT 'ID',
    `task_id` varchar(80) NOT NULL COMMENT '任务 ID',
    `case_key` varchar(255) NOT NULL COMMENT '场景业务 ID:用例 ID',
    `step_id` varchar(128) NOT NULL COMMENT '步骤业务 ID',
    `action_type` varchar(64) NOT NULL COMMENT 'server_command/database_sql/database_native',
    `execution_id` varchar(128) NOT NULL COMMENT '上层执行 ID',
    `project_environment_id` bigint(20) NOT NULL COMMENT '产品环境 ID',
    `scene_id` bigint(20) NOT NULL COMMENT '场景数据库 ID',
    `definition_version` bigint(20) DEFAULT NULL COMMENT '创建时定义版本',
    `target_kind` varchar(32) NOT NULL COMMENT 'server/database',
    `target_binding_key` varchar(128) NOT NULL COMMENT '环境内目标逻辑绑定',
    `attempt` int NOT NULL DEFAULT 0 COMMENT '执行尝试序号',
    `idempotency_key` varchar(384) NOT NULL COMMENT 'executionId:stepId:attempt',
    `executor_node` varchar(255) DEFAULT NULL COMMENT '实际执行节点',
    `status` varchar(32) NOT NULL COMMENT 'queued/running/passed/failed/cancelled',
    `error_code` varchar(64) DEFAULT NULL COMMENT '稳定错误码',
    `error_message` text DEFAULT NULL COMMENT '脱敏错误信息',
    `exit_code` int DEFAULT NULL COMMENT '命令退出码',
    `affected_rows` bigint(20) DEFAULT NULL COMMENT 'SQL 影响行数',
    `result_summary` text DEFAULT NULL COMMENT '脱敏结果摘要',
    `started_at` datetime DEFAULT NULL COMMENT '开始时间',
    `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
    `cancel_requested_at` datetime DEFAULT NULL COMMENT '请求取消时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_automation_infra_task_task_id` (`task_id`),
    UNIQUE INDEX `uk_automation_infra_task_idempotency` (`idempotency_key`),
    INDEX `idx_automation_infra_task_status` (`status`, `create_time`),
    INDEX `idx_automation_infra_task_execution` (`execution_id`, `step_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基础设施步骤异步任务';

CREATE TABLE IF NOT EXISTS `automation_infrastructure_task_log` (
    `id` bigint(20) NOT NULL COMMENT 'ID',
    `task_id` varchar(80) NOT NULL COMMENT '任务 ID',
    `sequence` bigint(20) NOT NULL COMMENT '任务内递增日志序号',
    `level` varchar(16) DEFAULT NULL COMMENT 'INFO/WARN/ERROR',
    `message` text NOT NULL COMMENT '脱敏且截断的日志内容',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_automation_infra_task_log_sequence` (`task_id`, `sequence`),
    INDEX `idx_automation_infra_task_log_task_sequence` (`task_id`, `sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基础设施步骤任务日志';

-- changeset codex:automation-infrastructure-task-target-config-id-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_infrastructure_task' AND column_name = 'target_config_id'
-- comment 新步骤按项目服务器或数据库配置 ID 定位执行目标，历史 binding_key 步骤继续兼容。
ALTER TABLE `automation_infrastructure_task`
    ADD COLUMN `target_config_id` bigint(20) DEFAULT NULL COMMENT '项目服务器或数据库配置 ID' AFTER `target_kind`;

-- rollback ALTER TABLE `automation_infrastructure_task` DROP COLUMN `target_config_id`;

-- changeset codex:automation-infrastructure-task-legacy-binding-key-nullable-20260729
-- comment target_binding_key 仅保留给历史步骤审计；新 ID 步骤不再写入该字段。
ALTER TABLE `automation_infrastructure_task`
    MODIFY COLUMN `target_binding_key` varchar(128) DEFAULT NULL COMMENT '历史目标逻辑绑定';

-- rollback ALTER TABLE `automation_infrastructure_task` MODIFY COLUMN `target_binding_key` varchar(128) NOT NULL COMMENT '环境内目标逻辑绑定';

-- changeset codex:automation-infrastructure-task-audit-time-default-20260729
-- comment 兼容 BaseDO 的更新审计填充策略，保证任务及其日志在新增时始终具有 update_time。
ALTER TABLE `automation_infrastructure_task`
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间';

ALTER TABLE `automation_infrastructure_task_log`
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间';

-- rollback ALTER TABLE `automation_infrastructure_task` MODIFY COLUMN `update_time` datetime NOT NULL COMMENT '修改时间';
-- rollback ALTER TABLE `automation_infrastructure_task_log` MODIFY COLUMN `update_time` datetime NOT NULL COMMENT '修改时间';

-- 仅授予明确授权的角色，避免普通场景回放权限直接获得服务器和数据库操作能力。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239495, '执行基础设施步骤', 1933371197518045184, 3,
     'automation:automationUiScene:execute-infrastructure', 8, 1, 1, NOW()),
    (1933371197522239496, '执行危险 SQL', 1933371197518045184, 3,
     'automation:automationUiScene:execute-dangerous-sql', 9, 1, 1, NOW()),
    (1933371197522239497, '执行危险服务器命令', 1933371197518045184, 3,
     'automation:automationUiScene:execute-dangerous-command', 10, 1, 1, NOW());

-- rollback DROP TABLE `automation_infrastructure_task_log`;
-- rollback DROP TABLE `automation_infrastructure_task`;

-- changeset codex:automation-operation-catalog-capability-permission-20260801
-- 操作目录能力上报与手工步骤新增使用独立权限，避免普通场景查看权限意外开放执行器握手。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239498, '新增自动化手工步骤', 1933371197518045184, 3,
     'automation:automationUiScene:addStep', 11, 1, 1, NOW()),
    (1933371197522239499, '上报执行器能力', 1933371197518045184, 3,
     'automation:executor:capability:report', 12, 1, 1, NOW());

-- rollback DELETE FROM `sys_menu` WHERE `id` IN (1933371197522239498, 1933371197522239499);
