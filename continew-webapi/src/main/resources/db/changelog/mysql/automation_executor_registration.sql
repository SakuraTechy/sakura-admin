-- liquibase formatted sql

-- changeset codex:automation-executor-registration-20260807
-- 执行器身份、节点部署位置和能力版本解耦；node_config_id 仅作为可选部署关联。
CREATE TABLE IF NOT EXISTS `automation_executor_registration` (
    `id`                       bigint(20)  NOT NULL COMMENT 'ID',
    `executor_type`            varchar(32) NOT NULL COMMENT '执行器类型：playwright/cuecast',
    `executor_instance_id`     varchar(128) NOT NULL COMMENT '稳定执行器实例标识',
    `application_access_key`   varchar(255) DEFAULT NULL COMMENT '绑定的开放应用 Access Key',
    `node_config_id`            bigint(20)  DEFAULT NULL COMMENT '可选 Jenkins 节点配置 ID',
    `project_environment_id`   bigint(20)  DEFAULT NULL COMMENT '为空表示允许所有产品环境',
    `description`              varchar(255) DEFAULT NULL COMMENT '执行器描述',
    `status`                   tinyint(1)  NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    `last_executor_version`    varchar(64) DEFAULT NULL COMMENT '最近一次上报的执行器版本',
    `last_catalog_version`     varchar(64) DEFAULT NULL COMMENT '最近一次上报的目录版本',
    `last_actions`             text DEFAULT NULL COMMENT '最近一次上报的 action JSON 清单',
    `last_features`            text DEFAULT NULL COMMENT '最近一次上报的运行特性 JSON 清单',
    `last_reported_at`         datetime(3) DEFAULT NULL COMMENT '最近一次能力上报时间',
    `create_user`              bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`              bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_automation_executor_registration_identity` (`executor_type`, `executor_instance_id`),
    INDEX `idx_automation_executor_registration_scope` (`project_environment_id`, `status`),
    INDEX `idx_automation_executor_registration_app` (`application_access_key`),
    INDEX `idx_automation_executor_registration_node` (`node_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化执行器独立注册信息';

-- 只有具有执行器管理权限的管理员可以变更注册表，Runner 仅保留能力上报权限。
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239509, '管理执行器注册', 1933371197518045184, 3,
     'automation:executor:registration:manage', 13, 1, 1, NOW());

-- 兼容已执行旧版建表 changeset 的数据库；新建表时前置检查会将补列 changeset 标记为已执行。
-- changeset codex:automation-executor-registration-app-binding-20260807
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_executor_registration' AND column_name = 'application_access_key'
ALTER TABLE `automation_executor_registration`
    ADD COLUMN `application_access_key` varchar(255) DEFAULT NULL COMMENT '绑定的开放应用 Access Key' AFTER `executor_instance_id`;

-- changeset codex:automation-executor-registration-actions-20260807
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_executor_registration' AND column_name = 'last_actions'
ALTER TABLE `automation_executor_registration`
    ADD COLUMN `last_actions` text DEFAULT NULL COMMENT '最近一次上报的 action JSON 清单' AFTER `last_catalog_version`;

-- changeset codex:automation-executor-registration-features-20260807
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_executor_registration' AND column_name = 'last_features'
ALTER TABLE `automation_executor_registration`
    ADD COLUMN `last_features` text DEFAULT NULL COMMENT '最近一次上报的运行特性 JSON 清单' AFTER `last_actions`;

-- rollback DELETE FROM `sys_menu` WHERE `id` = 1933371197522239509;
-- rollback DROP TABLE `automation_executor_registration`;
