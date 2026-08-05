-- liquibase formatted sql

-- changeset codex:project-version-config-baseline-20260805
-- comment 项目版本配置基础表；补齐历史功能缺失的空库初始化脚本。
CREATE TABLE IF NOT EXISTS `project_version_config` (
    `id` bigint NOT NULL COMMENT '版本ID',
    `project_id` bigint DEFAULT NULL COMMENT '所属项目',
    `name` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '版本名称',
    `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '版本描述',
    `type` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '版本类型',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='项目管理-版本配置表';

-- rollback DROP TABLE IF EXISTS `project_version_config`;

-- changeset codex:project-module-config-baseline-20260805
-- comment 项目模块配置基础表；补齐历史功能缺失的空库初始化脚本。
CREATE TABLE IF NOT EXISTS `project_module_config` (
    `id` bigint NOT NULL COMMENT '模块ID',
    `project_id` bigint DEFAULT NULL COMMENT '项目ID',
    `version_id` bigint DEFAULT NULL COMMENT '版本ID',
    `parent_id` bigint DEFAULT NULL COMMENT '父模块ID',
    `name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '模块名称',
    `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '模块描述',
    `sort` bigint DEFAULT NULL COMMENT '模块排序',
    `path` varchar(255) DEFAULT NULL COMMENT '模块路径',
    `count` bigint DEFAULT 0 COMMENT '模块下数据总数',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `update_ip` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint NOT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='项目管理-模块配置表';

-- rollback DROP TABLE IF EXISTS `project_module_config`;

-- changeset codex:project-environment-config-baseline-20260805
-- comment 项目环境配置基础表；补齐历史功能缺失的空库初始化脚本。
CREATE TABLE IF NOT EXISTS `project_environment_config` (
    `id` bigint NOT NULL COMMENT '环境ID',
    `project_id` bigint DEFAULT NULL COMMENT '所属项目',
    `name` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '环境名称',
    `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '环境描述',
    `version_config` json DEFAULT NULL COMMENT '环境版本信息',
    `server_config` json DEFAULT NULL COMMENT '环境服务器信息',
    `data_base_config` json DEFAULT NULL COMMENT '环境数据库信息',
    `last_version` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '主线版本',
    `last_domain` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '环境域名',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `dept_id` bigint DEFAULT NULL COMMENT '创建部门',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新IP',
    `remark` varchar(0) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
    `version` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '版本',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='项目管理-环境配置表';

-- rollback DROP TABLE IF EXISTS `project_environment_config`;

-- changeset codex:project-server-config-baseline-20260805
-- comment 项目服务器配置基础表；binding_key 由后续基础设施迁移追加。
CREATE TABLE IF NOT EXISTS `project_server_config` (
    `id` bigint NOT NULL COMMENT '服务器ID',
    `project_id` bigint DEFAULT NULL COMMENT '所属项目',
    `type` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '服务器类型',
    `version` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '服务器版本',
    `ip` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '服务器IP',
    `port` int DEFAULT NULL COMMENT '服务器端口',
    `user_name` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '服务器用户名',
    `pass_word` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '服务器密码',
    `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '服务器描述',
    `config_list` json DEFAULT NULL COMMENT '服务器参数配置',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='项目管理-服务器配置表';

-- rollback DROP TABLE IF EXISTS `project_server_config`;

-- changeset codex:project-database-config-baseline-20260805
-- comment 项目数据库配置基础表；binding_key 由后续基础设施迁移追加。
CREATE TABLE IF NOT EXISTS `project_data_base_config` (
    `id` bigint NOT NULL COMMENT '数据库ID',
    `project_id` bigint DEFAULT NULL COMMENT '所属项目',
    `type` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库类型',
    `version` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库版本',
    `driver` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库驱动',
    `ip` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库IP',
    `port` int DEFAULT NULL COMMENT '数据库端口',
    `data_base` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库/模式',
    `user_name` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库用户名',
    `pass_word` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库密码',
    `url` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库连接串',
    `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '数据库描述',
    `config_list` json DEFAULT NULL COMMENT '数据库参数配置',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='项目管理-数据库配置表';

-- rollback DROP TABLE IF EXISTS `project_data_base_config`;
