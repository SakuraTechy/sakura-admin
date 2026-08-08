-- liquibase formatted sql

-- changeset sakura:2005-test-domain-del-flag-defaults
-- comment 修正测试计划、报告和定时任务的逻辑删除默认值，与 StatusTypeEnum.NORMAL=3 保持一致。
ALTER TABLE `test_plan` ALTER COLUMN `del_flag` SET DEFAULT 3;
ALTER TABLE `test_report` ALTER COLUMN `del_flag` SET DEFAULT 3;
ALTER TABLE `test_timed_task` ALTER COLUMN `del_flag` SET DEFAULT 3;

-- changeset sakura:2005-test-domain-del-flag-backfill
-- comment 旧基线明确将 1 定义为正常数据，仅将该旧正常值迁移为当前正常值 3，保留删除值 4 不变。
UPDATE `test_plan` SET `del_flag` = 3 WHERE `del_flag` = 1;
UPDATE `test_report` SET `del_flag` = 3 WHERE `del_flag` = 1;
UPDATE `test_timed_task` SET `del_flag` = 3 WHERE `del_flag` = 1;

-- changeset sakura:2005-test-timed-task-run-task-status-start-index
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'test_timed_task_run' AND index_name = 'idx_timed_task_status_start'
CREATE INDEX `idx_timed_task_status_start`
    ON `test_timed_task_run` (`timed_task_id`, `status`, `start_time`);

-- rollback DROP INDEX `idx_timed_task_status_start` ON `test_timed_task_run`;

-- changeset sakura:2005-test-timed-task-run-status-start-index
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'test_timed_task_run' AND index_name = 'idx_timed_task_run_status_start'
CREATE INDEX `idx_timed_task_run_status_start`
    ON `test_timed_task_run` (`status`, `start_time`);

-- rollback DROP INDEX `idx_timed_task_run_status_start` ON `test_timed_task_run`;

-- changeset sakura:2005-test-timed-task-run-notification-end-index
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'test_timed_task_run' AND index_name = 'idx_timed_task_run_notification_end'
CREATE INDEX `idx_timed_task_run_notification_end`
    ON `test_timed_task_run` (`notification_status`, `end_time`);

-- rollback DROP INDEX `idx_timed_task_run_notification_end` ON `test_timed_task_run`;
