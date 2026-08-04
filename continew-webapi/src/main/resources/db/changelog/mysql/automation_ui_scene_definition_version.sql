-- liquibase formatted sql

-- changeset codex:automation-ui-scene-definition-version-20260727
-- validCheckSum: 1:any
-- comment 兼容已执行或部分执行的旧版聚合 changeset，列变更拆分到下方幂等 changeset。
SELECT 1;

-- changeset codex:automation-ui-scene-definition-version-column-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_scene' AND column_name = 'definition_version'
-- comment 场景定义版本列；生产大表请在低峰期执行，字段已存在时跳过。
ALTER TABLE `automation_ui_scene`
    ADD COLUMN `definition_version` bigint(20) NOT NULL DEFAULT 0 COMMENT '场景定义版本' AFTER `case_list`;

-- rollback ALTER TABLE `automation_ui_scene` DROP COLUMN `definition_version`;

-- changeset codex:automation-playwright-job-definition-version-column-20260729
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_playwright_job' AND column_name = 'definition_version'
-- comment Playwright 任务定义版本列；字段已存在时跳过。
ALTER TABLE `automation_playwright_job`
    ADD COLUMN `definition_version` bigint(20) DEFAULT NULL COMMENT '任务创建时场景定义版本' AFTER `case_key`;

-- rollback ALTER TABLE `automation_playwright_job` DROP COLUMN `definition_version`;

-- changeset codex:automation-ui-scene-node-id-sequence-20260728
-- validCheckSum: 1:any
-- comment 持久化用例和步骤 ID 高水位，避免删除最大编号后复用旧业务 ID。
CREATE TABLE IF NOT EXISTS `automation_ui_scene_node_id_sequence` (
    `scene_id` bigint(20) NOT NULL COMMENT '场景数据库 ID',
    `scope_key` varchar(96) NOT NULL COMMENT 'CASE 或 STEP:caseId',
    `id_prefix` varchar(96) NOT NULL COMMENT '业务 ID 前缀',
    `last_value` bigint(20) NOT NULL DEFAULT 0 COMMENT '已分配的最大数字后缀',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`scene_id`, `scope_key`, `id_prefix`),
    CONSTRAINT `fk_ui_scene_node_id_sequence_scene`
        FOREIGN KEY (`scene_id`) REFERENCES `automation_ui_scene` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UI 场景节点 ID 高水位';

-- rollback DROP TABLE `automation_ui_scene_node_id_sequence`;
