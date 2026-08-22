-- liquibase formatted sql

-- changeset codex:automation-ui-definition-projection-size-metric-20260818
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene' AND column_name = 'definition_size_bytes'
-- comment 定义热查询只读取有界度量；存量值由独立小批任务回填，禁止在周期扫描中解析全表 case_list。
ALTER TABLE `automation_ui_scene`
    ADD COLUMN `definition_size_bytes` bigint unsigned DEFAULT NULL COMMENT 'case_list UTF-8 字节数' AFTER `definition_version`;

-- rollback SELECT 1;

-- changeset codex:automation-ui-definition-projection-step-metric-20260818
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene' AND column_name = 'definition_step_count'
-- comment 独立 changeset 兼容只成功添加一个度量列的部分迁移环境。
ALTER TABLE `automation_ui_scene`
    ADD COLUMN `definition_step_count` int unsigned DEFAULT NULL COMMENT '后端解析得到的步骤数' AFTER `definition_size_bytes`;

-- rollback SELECT 1;

-- changeset codex:automation-ui-definition-read-state-20260818
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_read_state'
CREATE TABLE `automation_ui_scene_definition_read_state` (
    `id` bigint(20) NOT NULL,
    `scene_id` bigint(20) NOT NULL,
    `definition_version` bigint unsigned NOT NULL,
    `source_sha256` char(64) NOT NULL,
    `status` varchar(16) NOT NULL COMMENT 'queued/building/ready/failed/stale',
    `building_projection_id` bigint(20) DEFAULT NULL COMMENT '当前构建尝试的隔离 ID',
    `published_projection_id` bigint(20) DEFAULT NULL COMMENT '最后一次原子发布的可读投影 ID',
    `case_count` int unsigned NOT NULL DEFAULT 0,
    `step_count` int unsigned NOT NULL DEFAULT 0,
    `build_token` char(36) DEFAULT NULL,
    `lease_owner` varchar(128) DEFAULT NULL,
    `lease_until` datetime(3) DEFAULT NULL,
    `retry_count` int unsigned NOT NULL DEFAULT 0,
    `next_retry_at` datetime(3) DEFAULT NULL,
    `last_error` varchar(1000) DEFAULT NULL,
    `published_at` datetime(3) DEFAULT NULL,
    `create_time` datetime(3) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_scene_definition_read_state` (`scene_id`, `definition_version`),
    UNIQUE KEY `uk_automation_ui_definition_building_projection` (`building_projection_id`),
    UNIQUE KEY `uk_automation_ui_definition_published_projection` (`published_projection_id`),
    KEY `idx_automation_ui_definition_build` (`status`, `next_retry_at`, `lease_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景定义只读投影构建状态兼持久化队列';

-- 投影已经发布后属于可恢复读模型，应用回滚不得删除表或 ready 数据。
-- rollback SELECT 1;

-- changeset codex:automation-ui-definition-case-read-20260818
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_case_read'
CREATE TABLE `automation_ui_scene_definition_case_read` (
    `id` bigint(20) NOT NULL,
    `projection_id` bigint(20) NOT NULL,
    `scene_id` bigint(20) NOT NULL,
    `definition_version` bigint unsigned NOT NULL,
    `case_id` varchar(128) NOT NULL,
    `case_key` varchar(255) DEFAULT NULL,
    `case_index` int unsigned NOT NULL,
    `case_name` varchar(255) DEFAULT NULL,
    `step_count` int unsigned NOT NULL DEFAULT 0,
    `case_json` json NOT NULL COMMENT '不含 stepList 的完整 CaseDO 节点',
    `node_sha256` char(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_definition_case_id` (`projection_id`, `case_id`),
    UNIQUE KEY `uk_automation_ui_definition_case_order` (`projection_id`, `case_index`),
    KEY `idx_automation_ui_definition_case_scene` (`scene_id`, `definition_version`, `case_index`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景定义用例只读节点';

-- rollback SELECT 1;

-- changeset codex:automation-ui-definition-step-read-20260818
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene_definition_step_read'
CREATE TABLE `automation_ui_scene_definition_step_read` (
    `id` bigint(20) NOT NULL,
    `projection_id` bigint(20) NOT NULL,
    `case_read_id` bigint(20) NOT NULL,
    `scene_id` bigint(20) NOT NULL,
    `definition_version` bigint unsigned NOT NULL,
    `case_id` varchar(128) NOT NULL,
    `step_id` varchar(128) NOT NULL,
    `step_index` int unsigned NOT NULL,
    `step_json` json NOT NULL COMMENT '完整 StepDO，保留 playwright_step/locator_meta',
    `node_sha256` char(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_automation_ui_definition_step_order` (`projection_id`, `case_id`, `step_index`),
    UNIQUE KEY `uk_automation_ui_definition_step_id` (`projection_id`, `case_id`, `step_id`),
    KEY `idx_automation_ui_definition_step_case` (`case_read_id`, `step_index`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景定义步骤只读节点';

-- rollback SELECT 1;
