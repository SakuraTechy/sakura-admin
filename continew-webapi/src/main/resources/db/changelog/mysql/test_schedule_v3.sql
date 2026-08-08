-- liquibase formatted sql

-- changeset sakura:2004-test-timed-task-sync-columns
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_timed_task' AND column_name = 'schedule_sync_status'
-- comment 增加测试定时任务调度最终一致性状态。
ALTER TABLE `test_timed_task`
    ADD COLUMN `schedule_sync_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '调度同步状态' AFTER `status`,
    ADD COLUMN `schedule_sync_error` varchar(500) DEFAULT NULL COMMENT '最近同步错误' AFTER `schedule_sync_status`,
    ADD COLUMN `schedule_sync_time` datetime DEFAULT NULL COMMENT '最近同步时间' AFTER `schedule_sync_error`,
    ADD COLUMN `schedule_sync_version` bigint NOT NULL DEFAULT 1 COMMENT '本地配置版本' AFTER `schedule_sync_time`,
    ADD COLUMN `schedule_sync_retry_count` int NOT NULL DEFAULT 0 COMMENT '同步重试次数' AFTER `schedule_sync_version`,
    ADD COLUMN `schedule_sync_next_retry_time` datetime DEFAULT NULL COMMENT '下次同步重试时间' AFTER `schedule_sync_retry_count`;

-- changeset sakura:2004-test-timed-task-sync-backfill
-- comment 初始化存量任务的同步意图，已删除且仍有关联调度 ID 的任务进入删除对账。
UPDATE `test_timed_task`
SET `schedule_sync_status` = CASE
        WHEN `del_flag` = 4 AND `schedule_job_id` IS NOT NULL THEN 'DELETING'
        WHEN `del_flag` = 4 THEN 'SYNCED'
        ELSE 'PENDING'
    END,
    `schedule_sync_version` = 1,
    `schedule_sync_retry_count` = 0,
    `schedule_sync_next_retry_time` = CASE WHEN `del_flag` = 4 AND `schedule_job_id` IS NULL THEN NULL ELSE NOW() END;

-- changeset sakura:2004-test-timed-task-sync-index
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'test_timed_task' AND index_name = 'idx_timed_task_sync_retry'
CREATE INDEX `idx_timed_task_sync_retry`
    ON `test_timed_task` (`schedule_sync_status`, `schedule_sync_next_retry_time`);
