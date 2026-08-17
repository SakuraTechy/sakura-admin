-- liquibase formatted sql

-- changeset codex:automation-environment-resource-binding-20260814
-- validCheckSum: 1:any
-- comment 项目资源角色、环境绑定和私有文件资产；步骤只保存角色，执行时按项目环境解析真实资源。

CREATE TABLE IF NOT EXISTS `project_resource_slot` (
    `id` bigint(20) NOT NULL COMMENT 'ID',
    `project_id` bigint(20) NOT NULL COMMENT '所属项目',
    `resource_code` varchar(64) NOT NULL COMMENT '稳定资源角色编码',
    `resource_name` varchar(64) NOT NULL COMMENT '资源角色名称',
    `resource_kind` varchar(32) NOT NULL COMMENT 'SERVER/DATABASE/CERTIFICATE',
    `required` tinyint NOT NULL DEFAULT 1 COMMENT '是否为必需资源',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用，2禁用',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_project_resource_slot_code` (`project_id`, `resource_code`),
    INDEX `idx_project_resource_slot_kind` (`project_id`, `resource_kind`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目自动化资源角色';

CREATE TABLE IF NOT EXISTS `project_environment_resource_binding` (
    `id` bigint(20) NOT NULL COMMENT 'ID',
    `environment_id` bigint(20) NOT NULL COMMENT '项目环境 ID',
    `resource_slot_id` bigint(20) NOT NULL COMMENT '资源角色 ID',
    `resource_id` bigint(20) NOT NULL COMMENT '服务器、数据库或文件资产 ID',
    `binding_version` int NOT NULL DEFAULT 1 COMMENT '绑定版本',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用，2禁用',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_project_environment_resource_slot` (`environment_id`, `resource_slot_id`),
    INDEX `idx_project_environment_resource_id` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目环境资源绑定';

CREATE TABLE IF NOT EXISTS `automation_file_asset` (
    `id` bigint(20) NOT NULL COMMENT 'ID',
    `project_id` bigint(20) NOT NULL COMMENT '所属项目',
    `asset_kind` varchar(32) NOT NULL COMMENT '文件资产类型',
    `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
    `storage_key` varchar(768) NOT NULL COMMENT '私有存储键',
    `sha256` char(64) NOT NULL COMMENT 'SHA-256',
    `size` bigint(20) NOT NULL COMMENT '文件大小',
    `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
    `status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RETIRED',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_automation_file_asset_storage_key` (`storage_key`),
    INDEX `idx_automation_file_asset_project_kind` (`project_id`, `asset_kind`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化私有文件资产';

-- rollback DROP TABLE IF EXISTS `project_environment_resource_binding`;
-- rollback DROP TABLE IF EXISTS `project_resource_slot`;
-- rollback DROP TABLE IF EXISTS `automation_file_asset`;
