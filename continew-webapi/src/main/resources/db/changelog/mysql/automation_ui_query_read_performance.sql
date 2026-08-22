-- liquibase formatted sql

-- changeset codex:automation-ui-execution-record-source-20260818
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'automation_ui_execution' AND column_name = 'record_source'
-- comment 先发布可空来源列并由兼容版本双写；存量回填、核验和回滚窗口结束前禁止提前收紧为 NOT NULL。
ALTER TABLE `automation_ui_execution`
    ADD COLUMN `record_source` varchar(16) DEFAULT NULL COMMENT '服务端判定的执行来源：debug/test/internal' AFTER `record_type`;

-- 加法迁移回滚不得删列，避免兼容版本继续写入时失败。
-- rollback SELECT 1;
