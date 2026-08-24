-- liquibase formatted sql

-- changeset codex:automation-project-config-baseline-20260822
-- comment 自动化管理-项目配置基础表；新环境必须先建立配置表再加载菜单和业务数据。
CREATE TABLE IF NOT EXISTS `automation_project_config` (
    `id` bigint NOT NULL COMMENT '项目ID',
    `type` varchar(30) DEFAULT NULL COMMENT '项目类型',
    `name` varchar(30) DEFAULT NULL COMMENT '项目名称',
    `url` varchar(255) DEFAULT NULL COMMENT '项目地址',
    `description` varchar(255) DEFAULT NULL COMMENT '项目描述',
    `script_path` varchar(500) DEFAULT NULL COMMENT '脚本路径',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='自动化管理-项目配置表';

-- rollback DROP TABLE IF EXISTS `automation_project_config`;

-- changeset codex:automation-jenkins-config-baseline-20260822
-- comment 自动化管理-Jenkins配置基础表；job/node 列表使用 JSON 保存原始配置。
CREATE TABLE IF NOT EXISTS `automation_jenkins_config` (
    `id` bigint NOT NULL COMMENT 'Jenkins配置ID',
    `version` varchar(30) DEFAULT NULL COMMENT '版本',
    `ip` varchar(30) DEFAULT NULL COMMENT 'IP地址',
    `port` int DEFAULT NULL COMMENT '端口',
    `user_name` varchar(30) DEFAULT NULL COMMENT '用户名',
    `pass_word` varchar(255) DEFAULT NULL COMMENT '密码',
    `url` varchar(255) DEFAULT NULL COMMENT '访问地址',
    `job_list` json DEFAULT NULL COMMENT '关联项目列表',
    `description` varchar(255) DEFAULT NULL COMMENT '描述',
    `node_list` json DEFAULT NULL COMMENT '节点列表',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='自动化管理-Jenkins配置表';

-- rollback DROP TABLE IF EXISTS `automation_jenkins_config`;

-- changeset codex:automation-node-config-baseline-20260822
-- comment 自动化管理-节点配置基础表；description、active、config_list 保存节点原始 JSON 状态。
CREATE TABLE IF NOT EXISTS `automation_node_config` (
    `id` bigint NOT NULL COMMENT '节点ID',
    `index` int DEFAULT NULL COMMENT '节点序号',
    `jenkins_id` bigint DEFAULT NULL COMMENT '所属Jenkins ID',
    `name` varchar(30) DEFAULT NULL COMMENT '节点名称',
    `type` varchar(30) DEFAULT NULL COMMENT '节点类型',
    `json` text COMMENT '节点配置JSON',
    `xml` text COMMENT '节点配置XML',
    `url` varchar(255) DEFAULT NULL COMMENT '节点地址',
    `description` json DEFAULT NULL COMMENT '节点描述',
    `active` json DEFAULT NULL COMMENT '节点环境状态',
    `offline_status` tinyint DEFAULT NULL COMMENT '节点在线状态',
    `idle_status` tinyint DEFAULT NULL COMMENT '节点使用状态',
    `config_list` json DEFAULT NULL COMMENT '节点参数列表',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_jenkins_id` (`jenkins_id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='自动化管理-节点配置表';

-- rollback DROP TABLE IF EXISTS `automation_node_config`;

-- changeset codex:automation-browser-config-baseline-20260822
-- comment 自动化管理-浏览器配置基础表。
CREATE TABLE IF NOT EXISTS `automation_browser_config` (
    `id` bigint NOT NULL COMMENT '浏览器配置ID',
    `type` varchar(30) DEFAULT NULL COMMENT '浏览器类型',
    `version` varchar(30) DEFAULT NULL COMMENT '浏览器版本',
    `name` varchar(30) DEFAULT NULL COMMENT '浏览器名称',
    `official_download` varchar(255) DEFAULT NULL COMMENT '浏览器程序下载地址',
    `driver_download` varchar(255) DEFAULT NULL COMMENT '浏览器驱动下载地址',
    `exe_path` varchar(255) DEFAULT NULL COMMENT '浏览器程序路径',
    `driver_path` varchar(255) DEFAULT NULL COMMENT '浏览器驱动路径',
    `profile_path` varchar(255) DEFAULT NULL COMMENT '浏览器配置文件路径',
    `description` varchar(255) DEFAULT NULL COMMENT '浏览器描述',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) DEFAULT NULL COMMENT '更新人IP',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='自动化管理-浏览器配置表';

-- rollback DROP TABLE IF EXISTS `automation_browser_config`;

-- changeset codex:automation-environment-config-baseline-20260822
-- comment 自动化管理-环境配置基础表；关联配置保留为 JSON，避免初始化时丢失组合关系。
CREATE TABLE IF NOT EXISTS `automation_environment_config` (
    `id` bigint NOT NULL COMMENT '环境ID',
    `type` varchar(30) DEFAULT NULL COMMENT '环境类型',
    `name` varchar(30) DEFAULT NULL COMMENT '环境名称',
    `description` varchar(255) DEFAULT NULL COMMENT '环境描述',
    `project_config` json DEFAULT NULL COMMENT '环境项目信息',
    `jenkins_config` json DEFAULT NULL COMMENT '环境Jenkins信息',
    `node_config` json DEFAULT NULL COMMENT '环境节点信息',
    `browser_config` json DEFAULT NULL COMMENT '环境浏览器信息',
    `status` tinyint DEFAULT NULL COMMENT '状态',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `dept_id` bigint DEFAULT NULL COMMENT '创建部门',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `update_ip` varchar(255) DEFAULT NULL COMMENT '更新人IP',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `version` varchar(255) DEFAULT NULL COMMENT '版本',
    `del_flag` tinyint DEFAULT NULL COMMENT '删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_dept_id` (`dept_id`) USING BTREE,
    KEY `idx_create_user` (`create_user`) USING BTREE,
    KEY `idx_update_user` (`update_user`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='自动化管理-环境配置表';

-- rollback DROP TABLE IF EXISTS `automation_environment_config`;
