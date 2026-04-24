-- liquibase formatted sql

-- changeset sakura:2002
-- comment 测试管理菜单和字典
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(9300, '测试管理', 0, 1, '/test', 'Test', 'Layout', '/test/testPlan', 'bug', b'0', b'0', b'0', NULL, 7, 1, 1, NOW()),
(9310, '测试计划', 9300, 2, '/test/testPlan', 'TestTestPlan', 'test/testPlan/index', NULL, 'calendar', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
(9311, '列表', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:list', 1, 1, 1, NOW()),
(9312, '详情', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:get', 2, 1, 1, NOW()),
(9313, '新增', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:create', 3, 1, 1, NOW()),
(9314, '修改', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:update', 4, 1, 1, NOW()),
(9315, '删除', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:delete', 5, 1, 1, NOW()),
(9316, '导出', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:export', 6, 1, 1, NOW()),
(9317, '关联场景', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:relateScene', 7, 1, 1, NOW()),
(9318, '执行', 9310, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testPlan:execute', 8, 1, 1, NOW()),
(9320, '测试报告', 9300, 2, '/test/testReport', 'TestTestReport', 'test/testReport/index', NULL, 'file', b'0', b'0', b'0', NULL, 2, 1, 1, NOW()),
(9321, '列表', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:list', 1, 1, 1, NOW()),
(9322, '详情', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:get', 2, 1, 1, NOW()),
(9323, '新增', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:create', 3, 1, 1, NOW()),
(9324, '修改', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:update', 4, 1, 1, NOW()),
(9325, '删除', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:delete', 5, 1, 1, NOW()),
(9326, '导出', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:export', 6, 1, 1, NOW()),
(9327, '上传结果', 9320, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testReport:uploadResult', 7, 1, 1, NOW()),
(9330, '测试度量', 9300, 2, '/test/testMetric', 'TestTestMetric', 'test/testMetric/index', NULL, 'bar-chart', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
(9331, '查看', 9330, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:testMetric:list', 1, 1, 1, NOW()),
(9340, '定时任务', 9300, 2, '/test/timedTask', 'TestTimedTask', 'test/timedTask/index', NULL, 'clock-circle', b'0', b'0', b'0', NULL, 4, 1, 1, NOW()),
(9341, '列表', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:list', 1, 1, 1, NOW()),
(9342, '详情', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:get', 2, 1, 1, NOW()),
(9343, '新增', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:create', 3, 1, 1, NOW()),
(9344, '修改', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:update', 4, 1, 1, NOW()),
(9345, '删除', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:delete', 5, 1, 1, NOW()),
(9346, '导出', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:export', 6, 1, 1, NOW()),
(9347, '启停', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:updateStatus', 7, 1, 1, NOW()),
(9348, '执行', 9340, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0', 'test:timedTask:execute', 8, 1, 1, NOW());

INSERT IGNORE INTO `sys_dict`
(`id`, `name`, `code`, `description`, `is_system`, `create_user`, `create_time`)
VALUES
(50, '测试计划状态', 'test_plan_status', NULL, b'1', 1, NOW()),
(51, '测试报告状态', 'test_report_status', NULL, b'1', 1, NOW()),
(52, '测试报告触发方式', 'test_report_trigger_mode', NULL, b'1', 1, NOW()),
(53, '测试报告执行方式', 'test_report_execute_mode', NULL, b'1', 1, NOW()),
(54, '定时任务状态', 'test_timed_task_status', NULL, b'1', 1, NOW()),
(55, '定时任务类型', 'test_timed_task_type', NULL, b'1', 1, NOW());

INSERT IGNORE INTO `sys_dict_item`
(`id`, `label`, `value`, `color`, `sort`, `description`, `status`, `dict_id`, `create_user`, `create_time`)
VALUES
(5001, '未开始', 'NOT_STARTED', 'default', 1, NULL, 1, 50, 1, NOW()),
(5002, '进行中', 'RUNNING', 'primary', 2, NULL, 1, 50, 1, NOW()),
(5003, '已完成', 'COMPLETED', 'success', 3, NULL, 1, 50, 1, NOW()),
(5004, '已归档', 'ARCHIVED', 'warning', 4, NULL, 1, 50, 1, NOW()),
(5101, '生成中', 'RUNNING', 'primary', 1, NULL, 1, 51, 1, NOW()),
(5102, '通过', 'PASSED', 'success', 2, NULL, 1, 51, 1, NOW()),
(5103, '未通过', 'FAILED', 'error', 3, NULL, 1, 51, 1, NOW()),
(5201, '手动', 'MANUAL', 'primary', 1, NULL, 1, 52, 1, NOW()),
(5202, '定时', 'SCHEDULE', 'warning', 2, NULL, 1, 52, 1, NOW()),
(5301, '调试执行', 'DEBUG', 'primary', 1, NULL, 1, 53, 1, NOW()),
(5302, '计划执行', 'PLAN', 'success', 2, NULL, 1, 53, 1, NOW()),
(5401, '禁用', 'DISABLED', 'default', 1, NULL, 1, 54, 1, NOW()),
(5402, '启用', 'ENABLED', 'success', 2, NULL, 1, 54, 1, NOW()),
(5501, '测试计划', 'PLAN', 'primary', 1, NULL, 1, 55, 1, NOW());

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 9300),
(3, 9310),
(3, 9311),
(3, 9312),
(3, 9313),
(3, 9314),
(3, 9315),
(3, 9316),
(3, 9317),
(3, 9318),
(3, 9320),
(3, 9321),
(3, 9322),
(3, 9323),
(3, 9324),
(3, 9325),
(3, 9326),
(3, 9327),
(3, 9330),
(3, 9331),
(3, 9340),
(3, 9341),
(3, 9342),
(3, 9343),
(3, 9344),
(3, 9345),
(3, 9346),
(3, 9347),
(3, 9348);
