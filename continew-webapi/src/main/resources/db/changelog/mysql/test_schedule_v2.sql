-- liquibase formatted sql

-- changeset sakura:2003
-- validCheckSum: 1:any
-- comment 测试计划定时任务业务执行记录；列变更和数据回填拆分到独立幂等 changeset。
CREATE TABLE IF NOT EXISTS `test_timed_task_run` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `timed_task_id` bigint NOT NULL COMMENT '测试定时任务ID',
    `task_name` varchar(128) NOT NULL COMMENT '任务名称快照',
    `test_plan_id` bigint NOT NULL COMMENT '测试计划ID',
    `test_plan_name` varchar(128) DEFAULT NULL COMMENT '测试计划名称快照',
    `test_report_id` bigint DEFAULT NULL COMMENT '测试报告ID',
    `trigger_mode` varchar(32) NOT NULL COMMENT '触发方式',
    `status` varchar(32) NOT NULL COMMENT '运行状态',
    `notification_emails` json DEFAULT NULL COMMENT '通知邮箱快照',
    `start_time` datetime NOT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    `run_time` bigint NOT NULL DEFAULT 0 COMMENT '运行耗时(ms)',
    `build_number` varchar(64) DEFAULT NULL COMMENT '构建号',
    `console_url` varchar(500) DEFAULT NULL COMMENT '控制台地址',
    `report_url` varchar(500) DEFAULT NULL COMMENT '报告地址',
    `failure_reason` varchar(1000) DEFAULT NULL COMMENT '失败或跳过原因',
    `notification_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '通知状态',
    `notification_error` varchar(500) DEFAULT NULL COMMENT '通知失败原因',
    `del_flag` tinyint NOT NULL DEFAULT 3 COMMENT '删除标记 3正常 4删除',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    INDEX `idx_timed_task_start_time` (`timed_task_id`, `start_time`),
    INDEX `idx_test_report_id` (`test_report_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试定时任务业务执行记录表';

-- changeset sakura:2003-test-timed-task-notification-emails-column
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'test_timed_task' AND column_name = 'notification_emails'
-- comment 通知邮箱列已由人工 SQL 或旧版本创建时直接标记完成，避免重复加列。
ALTER TABLE `test_timed_task`
    ADD COLUMN `notification_emails` json DEFAULT NULL COMMENT '执行结果通知邮箱' AFTER `execute_email`;

-- rollback ALTER TABLE `test_timed_task` DROP COLUMN `notification_emails`;

-- changeset sakura:2003-test-timed-task-notification-emails-backfill
-- comment 从旧单邮箱字段幂等回填通知邮箱数组。
UPDATE `test_timed_task`
SET `notification_emails` = JSON_ARRAY(`execute_email`)
WHERE (`notification_emails` IS NULL OR JSON_LENGTH(`notification_emails`) = 0)
  AND `execute_email` IS NOT NULL
  AND TRIM(`execute_email`) <> '';
