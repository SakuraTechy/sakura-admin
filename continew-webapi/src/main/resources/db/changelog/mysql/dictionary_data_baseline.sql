-- liquibase formatted sql

-- changeset codex:dictionary-data-baseline-20260824
-- comment 同步生产字典基线；使用 INSERT IGNORE 只补齐缺失数据，不覆盖已有环境的人工修改。

INSERT IGNORE INTO `sys_dict` VALUES (1, '公告类型', 'notice_type', NULL, b'1', 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (2, '消息类型', 'message_type', NULL, b'1', 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (3, '终端类型', 'client_type', NULL, b'1', 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (4, '状态类型', 'status_type', NULL, b'1', 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (5, '版本类型', 'version_type', NULL, b'1', 1, '2025-05-05 15:47:38', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (6, '服务器类型', 'server_type', NULL, b'1', 1, '2025-05-06 15:47:38', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (50, '测试计划状态', 'test_plan_status', NULL, b'1', 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (51, '测试报告状态', 'test_report_status', NULL, b'1', 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (52, '测试报告触发方式', 'test_report_trigger_mode', NULL, b'1', 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (53, '测试报告执行方式', 'test_report_execute_mode', NULL, b'1', 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (54, '定时任务状态', 'test_timed_task_status', NULL, b'1', 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (55, '定时任务类型', 'test_timed_task_type', NULL, b'1', 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (710901573273784761, '数据库类型', 'database_type', NULL, b'1', 1, '2025-05-08 17:09:03', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (714858007409721383, '自动化类型', 'automation_type', NULL, b'1', 1, '2025-05-19 15:10:30', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (718486894643511321, '浏览器类型', 'browser_type', NULL, b'1', 1, '2025-05-29 15:30:25', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (726131467658203459, '场景等级', 'scene_level', NULL, b'0', 1, '2025-06-19 17:47:13', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (728316773706769592, '调试类型', 'debug_type', NULL, b'0', 1, '2025-06-25 18:30:50', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (740267021882654833, '排序类型', 'sort_type', NULL, b'0', 1, '2025-07-28 17:56:52', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (749311407635341426, '自动化操作类型', 'automation_operation_type', '自动化操作类型', b'0', 1, '2025-08-22 16:56:01', NULL, NULL);
INSERT IGNORE INTO `sys_dict` VALUES (749315493604663470, '自动化操作方法', 'automation_operation_method', NULL, b'0', 1, '2025-08-22 17:12:15', NULL, NULL);

INSERT IGNORE INTO `sys_dict_item` VALUES (1, '通知', '1', 'primary', 1, NULL, 1, 1, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (2, '活动', '2', 'success', 2, NULL, 1, 1, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (3, '安全消息', '1', 'warning', 1, NULL, 1, 2, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (4, '活动消息', '2', 'success', 2, NULL, 1, 2, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5, '桌面端', 'PC', 'primary', 1, NULL, 1, 3, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (6, '安卓', 'ANDROID', 'success', 2, NULL, 1, 3, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (7, '小程序', 'XCX', 'warning', 3, NULL, 1, 3, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (8, '启用', '1', 'success', 1, NULL, 1, 4, 1, '2025-04-27 18:23:23', 1, '2025-04-30 11:01:39');
INSERT IGNORE INTO `sys_dict_item` VALUES (9, '禁用', '2', 'error', 2, NULL, 1, 4, 1, '2025-04-27 18:23:23', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (10, 'Linux', 'Linux', 'primary', 1, NULL, 1, 6, 1, '2025-05-06 15:47:50', 1, '2025-05-10 18:51:40');
INSERT IGNORE INTO `sys_dict_item` VALUES (11, 'Windows', 'Windows', 'success', 2, NULL, 1, 6, 1, '2025-05-06 15:46:01', 1, '2025-05-10 18:51:46');
INSERT IGNORE INTO `sys_dict_item` VALUES (5001, '未开始', 'NOT_STARTED', 'default', 1, NULL, 1, 50, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5002, '进行中', 'RUNNING', 'primary', 2, NULL, 1, 50, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5003, '已完成', 'COMPLETED', 'success', 3, NULL, 1, 50, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5004, '已归档', 'ARCHIVED', 'warning', 4, NULL, 1, 50, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5101, '生成中', 'RUNNING', 'primary', 1, NULL, 1, 51, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5102, '通过', 'PASSED', 'success', 2, NULL, 1, 51, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5103, '未通过', 'FAILED', 'error', 3, NULL, 1, 51, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5104, '已取消', 'CANCELLED', 'warning', 4, NULL, 1, 51, 1, '2026-07-25 19:38:09', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5201, '手动', 'MANUAL', 'primary', 1, NULL, 1, 52, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5202, '定时', 'SCHEDULE', 'warning', 2, NULL, 1, 52, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5301, '调试执行', 'DEBUG', 'primary', 1, NULL, 1, 53, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5302, '计划执行', 'PLAN', 'success', 2, NULL, 1, 53, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5401, '禁用', 'DISABLED', 'default', 1, NULL, 1, 54, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5402, '启用', 'ENABLED', 'success', 2, NULL, 1, 54, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (5501, '测试计划', 'PLAN', 'primary', 1, NULL, 1, 55, 1, '2026-04-24 10:34:22', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710903583033266627, 'MongoDB', 'com.dbschema.MongoJdbcDriver', 'primary', 23, 'mongodb://userName:passWord@localhost:port/mydb?authSource=admin', 1, 710901573273784761, 1, '2025-05-08 17:17:02', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710903833697456586, 'DM', 'dm.jdbc.driver.DmDriver', 'primary', 22, 'jdbc:dm://localhost:port/schema=mydb', 1, 710901573273784761, 1, '2025-05-08 17:18:02', 1, '2025-05-08 17:18:07');
INSERT IGNORE INTO `sys_dict_item` VALUES (710903956678644176, 'Hbase', 'org.apache.phoenix.jdbc.PhoenixDriver', 'primary', 21, 'jdbc:phoenix:localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:18:31', 1, '2025-05-12 16:07:11');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904022038483411, 'TDengine', 'com.taosdata.jdbc.rs.RestfulDriver', 'primary', 20, 'jdbc:TAOS-RS://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:18:47', 1, '2025-05-12 16:07:03');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904112010498518, 'Gbase8s', 'com.gbasedbt.jdbc.Driver', 'primary', 19, 'jdbc:gbasedbt-sqli://localhost:port/mydb:GBASEDBTSERVER=gbaseserver;CLIENT_LOCALE=zh_cn.utf8;SQLMODE=GBase;NEWCODESET=UTF8,zh_cn.UTF8,57372;DB_LOCALE=zh_CN.57372;', 1, 710901573273784761, 1, '2025-05-08 17:19:08', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904179559764441, 'Gbase8a', 'com.gbase.jdbc.Driver', 'primary', 18, 'jdbc:gbase://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:19:24', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904250237981148, 'GaussDB', 'org.postgresql.Driver', 'primary', 6, 'jdbc:postgresql://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:25:35', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904312091382239, 'Cache', 'com.intersys.jdbc.CacheDriver', 'primary', 17, 'jdbc:Cache://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:19:56', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904370295738850, 'DB2', 'com.ibm.db2.jcc.DB2Driver', 'primary', 16, 'jdbc:db2://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:20:10', 1, '2025-05-08 17:20:14');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904451858174440, 'Informix', 'com.informix.jdbc.IfxDriver', 'primary', 15, 'jdbc:informix-sqli://localhost:port/mydb:informixserver=informix', 1, 710901573273784761, 1, '2025-05-08 17:20:29', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904508028293611, 'IRIS', 'com.intersystems.jdbc.IRISDriver', 'primary', 14, 'jdbc:IRIS://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:20:43', 1, '2025-05-12 16:06:49');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904569302880750, 'KingBase', 'com.kingbase8.Driver', 'primary', 13, 'jdbc:kingbase8://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:20:57', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904649497973235, 'MariaDB', 'org.mariadb.jdbc.Driver', 'primary', 12, 'jdbc:mysql://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:21:16', 1, '2025-05-08 17:21:21');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904735170826745, 'Teradata', 'com.teradata.jdbc.TeraDriver', 'primary', 11, 'jdbc:teradata://localhost/DATABASE=mydb,DBS_PORT=port', 1, 710901573273784761, 1, '2025-05-08 17:21:37', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904794352456188, 'OceanBase', 'com.mysql.jdbc.Driver', 'primary', 10, 'jdbc:mysql://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:21:51', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904857325736447, 'TiDB', 'com.mysql.cj.jdbc.Driver', 'primary', 9, 'jdbc:mysql://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:22:06', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904920705864194, 'Hive', 'org.apache.hive.jdbc.HiveDriver', 'primary', 8, 'jdbc:hive2://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:22:21', 1, '2025-05-12 16:06:28');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904983125496325, 'Sybase', 'com.sybase.jdbc4.jdbc.SybDriver', 'primary', 7, 'jdbc:sybase:Tds:localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:22:36', 1, '2025-05-12 16:06:20');
INSERT IGNORE INTO `sys_dict_item` VALUES (710905748116214282, 'Greenplum', 'org.postgresql.Driver', 'primary', 5, 'jdbc:postgresql://localhost:port/mydb', 1, 710901573273784761, 1, '2025-05-08 17:25:38', 1, '2025-05-10 09:50:26');
INSERT IGNORE INTO `sys_dict_item` VALUES (710905843725373965, 'PostgreSQL', 'org.postgresql.Driver', 'primary', 4, 'jdbc:postgresql://localhost:port/mydb?encrypt=false&trustServerCertificate=false', 1, 710901573273784761, 1, '2025-05-08 17:26:01', 1, '2025-05-12 16:28:22');
INSERT IGNORE INTO `sys_dict_item` VALUES (710906015641506320, 'SQLServer', 'com.microsoft.sqlserver.jdbc.SQLServerDriver', 'primary', 3, 'jdbc:sqlserver://localhost:port;databaseName=mydb;encrypt=false;trustServerCertificate=false', 1, 710901573273784761, 1, '2025-05-08 17:26:42', 1, '2025-05-12 16:27:49');
INSERT IGNORE INTO `sys_dict_item` VALUES (710906079923409427, 'Oracle', 'oracle.jdbc.driver.OracleDriver', 'primary', 2, 'jdbc:oracle:thin:@localhost:port:mydb', 1, 710901573273784761, 1, '2025-05-08 17:26:57', 1, '2025-05-12 16:05:39');
INSERT IGNORE INTO `sys_dict_item` VALUES (710906201734386199, 'MySQL', 'com.mysql.jdbc.Driver', 'primary', 1, 'jdbc:mysql://localhost:port/mydb?useUnicode=true&characterEncoding=utf-8&useSSL=false', 1, 710901573273784761, 1, '2025-05-08 17:27:26', 1, '2025-05-12 16:28:05');
INSERT IGNORE INTO `sys_dict_item` VALUES (714858197675933739, 'API自动化', 'API', 'warning', 3, NULL, 1, 714858007409721383, 1, '2025-05-19 15:11:16', 1, '2025-05-19 15:12:58');
INSERT IGNORE INTO `sys_dict_item` VALUES (714858316374736942, 'WEB自动化', 'WEB', 'primary', 1, NULL, 1, 714858007409721383, 1, '2025-05-19 15:11:44', 1, '2025-05-19 15:12:53');
INSERT IGNORE INTO `sys_dict_item` VALUES (714858388193804337, 'APP自动化', 'APP', 'success', 2, NULL, 1, 714858007409721383, 1, '2025-05-19 15:11:43', 1, '2025-05-19 15:12:49');
INSERT IGNORE INTO `sys_dict_item` VALUES (715604363682250963, '在线', '5', 'success', 5, NULL, 1, 4, 1, '2025-05-21 16:36:16', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (715604412441034966, '离线', '6', 'default', 6, NULL, 1, 4, 1, '2025-05-21 16:36:27', 1, '2025-05-22 10:57:07');
INSERT IGNORE INTO `sys_dict_item` VALUES (715604463913533657, '空闲', '7', 'success', 7, NULL, 1, 4, 1, '2025-05-21 16:36:39', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (715604511594381531, '未使用', '9', 'default', 9, NULL, 1, 4, 1, '2025-05-21 16:36:51', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (715604511594381532, '使用中', '8', 'error', 8, NULL, 1, 4, 1, '2025-05-21 16:36:51', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (715604873319547105, '正常', '3', 'success', 3, NULL, 1, 4, 1, '2025-05-21 16:38:17', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (715604937282683108, '异常', '4', 'error', 4, NULL, 1, 4, 1, '2025-05-21 16:38:32', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (718487031965024286, '谷歌浏览器', 'Chrome', 'success', 1, NULL, 1, 718486894643511321, 1, '2025-05-29 15:30:57', 1, '2025-06-04 09:37:01');
INSERT IGNORE INTO `sys_dict_item` VALUES (718487140685578273, '火狐浏览器', 'Firefox', 'warning', 2, NULL, 1, 718486894643511321, 1, '2025-05-29 15:31:23', 1, '2025-06-04 09:37:37');
INSERT IGNORE INTO `sys_dict_item` VALUES (718487502431715364, 'IE浏览器', 'Edge', 'primary', 3, NULL, 1, 718486894643511321, 1, '2025-05-29 15:32:49', 1, '2025-06-04 09:37:41');
INSERT IGNORE INTO `sys_dict_item` VALUES (723866639581319408, '未开始', '10', 'default', 10, NULL, 1, 4, 1, '2025-06-13 11:47:36', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (723866798092456179, '进行中', '11', 'primary', 11, NULL, 1, 4, 1, '2025-06-13 11:48:13', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (723866897384214777, '已完成', '12', 'success', 12, NULL, 1, 4, 1, '2025-06-13 11:48:37', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (723961120875877027, '主线版本', '1', 'primary', 1, NULL, 1, 5, 1, '2025-06-13 18:03:02', 1, '2025-06-13 18:28:06');
INSERT IGNORE INTO `sys_dict_item` VALUES (723961334625997483, '支线版本', '2', 'default', 2, NULL, 1, 5, 1, '2025-06-13 18:03:53', 1, '2025-06-13 18:28:14');
INSERT IGNORE INTO `sys_dict_item` VALUES (726131621937287495, 'P0', 'P0', 'primary', 1, NULL, 1, 726131467658203459, 1, '2025-06-19 17:47:50', 1, '2025-06-26 21:21:24');
INSERT IGNORE INTO `sys_dict_item` VALUES (726131658138325322, 'P1', 'P1', 'success', 2, NULL, 1, 726131467658203459, 1, '2025-06-19 17:47:58', 1, '2025-06-26 21:21:29');
INSERT IGNORE INTO `sys_dict_item` VALUES (726131723212951885, 'P2', 'P2', 'warning', 3, NULL, 1, 726131467658203459, 1, '2025-06-19 17:48:14', 1, '2025-06-26 21:21:34');
INSERT IGNORE INTO `sys_dict_item` VALUES (726131902422978899, 'P3', 'P3', 'default', 4, NULL, 1, 726131467658203459, 1, '2025-06-19 17:48:56', 1, '2025-06-26 21:21:38');
INSERT IGNORE INTO `sys_dict_item` VALUES (726135552851579224, '未执行', '13', 'default', 13, NULL, 1, 4, 1, '2025-06-19 18:03:27', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (726135776420565339, '全部通过', '14', 'success', 14, NULL, 1, 4, 1, '2025-06-19 18:04:20', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (726135817315029342, '不通过', '15', 'error', 15, NULL, 1, 4, 1, '2025-06-19 18:04:30', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (728316839976772801, '本地调试', '1', 'primary', 1, NULL, 1, 728316773706769592, 1, '2025-06-25 18:31:06', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (728316937905382611, '远程调试', '2', 'success', 2, NULL, 1, 728316773706769592, 1, '2025-06-25 18:31:29', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (728317029362181340, '查看日志', '3', 'warning', 3, NULL, 1, 728316773706769592, 1, '2025-06-25 18:31:51', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (728317070562829535, '查看报告', '4', 'error', 4, NULL, 1, 728316773706769592, 1, '2025-06-25 18:32:01', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (728317108315759842, '查看回放', '5', 'default', 5, NULL, 1, 728316773706769592, 1, '2025-06-25 18:32:10', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (740267207669350518, '交换排序', '1', 'primary', 1, '两者进行位置互换，只改变两者的序号，其它不变', 1, 740267021882654833, 1, '2025-07-28 17:57:36', 1, '2025-08-06 15:36:04');
INSERT IGNORE INTO `sys_dict_item` VALUES (740267325235691641, '冒泡排序', '2', 'success', 2, '两者进行大小比较，较小的会排在前面，正序排列', 1, 740267021882654833, 1, '2025-07-28 17:58:04', 1, '2025-08-06 15:36:07');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312219199610999, '浏览器操作', '浏览器操作', 'primary', 1, NULL, 2, 749311407635341426, 1, '2025-08-22 16:59:15', 1, '2026-07-31 09:32:49');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312271502581882, '点击操作', '点击操作', 'primary', 2, NULL, 2, 749311407635341426, 1, '2025-08-22 16:59:27', 1, '2025-08-27 16:38:48');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312400498401408, '弹窗操作', '弹窗操作', 'primary', 3, NULL, 2, 749311407635341426, 1, '2025-08-22 16:59:58', 1, '2025-08-27 16:38:52');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312477270941836, '输入操作', '输入操作', 'primary', 4, NULL, 2, 749311407635341426, 1, '2025-08-22 17:00:16', 1, '2025-08-27 16:38:55');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312531104833679, '检查操作', '检查操作', 'primary', 5, '', 2, 749311407635341426, 1, '2025-08-22 17:00:29', 1, '2025-08-27 16:38:59');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312605243351189, '等待操作', '等待操作', 'primary', 6, NULL, 2, 749311407635341426, 1, '2025-08-22 17:00:47', 1, '2025-08-27 16:39:02');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312684943515800, '全局变量操作', '全局变量操作', 'primary', 7, NULL, 2, 749311407635341426, 1, '2025-08-22 17:01:06', 1, '2025-08-27 16:39:07');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312738647384219, 'Windows系统操作', 'Windows系统操作', 'primary', 8, NULL, 2, 749311407635341426, 1, '2025-08-22 17:01:18', 1, '2025-08-27 16:39:10');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312786873491614, '鼠标操作', '鼠标操作', 'primary', 9, NULL, 2, 749311407635341426, 1, '2025-08-22 17:01:30', 1, '2025-08-27 16:39:13');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312837704261793, '文件目录操作', '文件目录操作', 'primary', 10, NULL, 2, 749311407635341426, 1, '2025-08-22 17:01:42', 1, '2025-08-27 16:39:16');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312909611409572, '服务器操作', '服务器操作', 'primary', 11, NULL, 2, 749311407635341426, 1, '2025-08-22 17:01:59', 1, '2025-08-27 16:39:20');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312965668282536, '数据库操作', '数据库操作', 'primary', 12, NULL, 2, 749311407635341426, 1, '2025-08-22 17:02:13', 1, '2025-08-27 16:39:23');
INSERT IGNORE INTO `sys_dict_item` VALUES (749312999730225323, '滚动操作', '滚动操作', 'primary', 13, NULL, 2, 749311407635341426, 1, '2025-08-22 17:02:21', 1, '2025-08-27 16:39:26');
INSERT IGNORE INTO `sys_dict_item` VALUES (749315794579529908, '打开默认网页', 'web-geturl', 'primary', 1, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"打开默认网页\",\n  \"value\": \"web-geturl\",\n  \"configList\": [\n    {\n      \"paramsName\": \"value\",\n      \"paramsValue\": \"https: //172.19.5.33/login\"\n    }\n  ]\n}', 2, 749315493604663470, 1, '2025-08-22 17:13:27', 1, '2025-08-25 15:27:31');
INSERT IGNORE INTO `sys_dict_item` VALUES (749318574128013508, '打开指定网页', 'web-geturls', 'primary', 2, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"打开指定网页\",\n  \"value\": \"web-geturls\",\n  \"configList\": [\n    {\n      \"paramsName\": \"value\",\n      \"paramsValue\": \"https: //172.19.5.33/login\"\n    }\n  ]\n}', 2, 749315493604663470, 1, '2025-08-22 17:24:30', 1, '2025-08-25 15:27:34');
INSERT IGNORE INTO `sys_dict_item` VALUES (749318946267635918, '关闭当前标签页', 'web-close', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"关闭当前标签页\",\n  \"value\": \"web-close\",\n  \"configList\": []\n}', 2, 749315493604663470, 1, '2025-08-22 17:25:58', 1, '2025-08-25 15:27:38');
INSERT IGNORE INTO `sys_dict_item` VALUES (749320382783201495, '关闭全部标签页', 'web-quit', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"关闭全部标签页\",\n  \"value\": \"web-quit\",\n  \"configList\": []\n}', 2, 749315493604663470, 1, '2025-08-22 17:31:41', 1, '2025-08-25 15:27:41');
INSERT IGNORE INTO `sys_dict_item` VALUES (749320468032430298, '页面刷新', 'web-refresh', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"页面刷新\",\n  \"value\": \"web-refresh\",\n  \"configList\": []\n}', 2, 749315493604663470, 1, '2025-08-22 17:32:01', 1, '2025-08-25 15:27:43');
INSERT IGNORE INTO `sys_dict_item` VALUES (749329751956561921, '获取图片验证码', 'web-getcode', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"获取图片验证码\",\n  \"value\": \"web-getcode\",\n  \"configList\": [\n    {\n      \"paramsName\": \"locator\",\n      \"paramsValue\": \"xpath=(//input[@placeholder=\'验证码\'])[1]\"\n    },\n    {\n      \"paramsName\": \"url\",\n      \"paramsValue\": \"xpath=(//img[@title=\'点击刷新\'])[1]\"\n    },\n    {\n      \"paramsName\": \"element\",\n      \"paramsValue\": \"xpath=(//button[contains(text(),\'登录\')])[1]\"\n    },\n    {\n      \"paramsName\": \"value\",\n      \"paramsValue\": \"xpath=(//div[@class=\'body-content\'])[1]\"\n    },\n    {\n      \"paramsName\": \"expect\",\n      \"paramsValue\": \"验证码错误\"\n    },\n    {\n      \"paramsName\": \"message\",\n      \"paramsValue\": \"xpath=(//button[@type=\'button\'][contains(text(),\'确定\')])[1]\"\n    },\n    {\n      \"paramsName\": \"skip\",\n      \"paramsValue\": \"locator（默认locator，可跳过locator和expect，跳过后会标记为成功）\"\n    }\n  ]\n}', 2, 749315493604663470, 1, '2025-08-22 18:08:55', 1, '2025-08-25 15:27:48');
INSERT IGNORE INTO `sys_dict_item` VALUES (749331036214697989, '切换当前最新窗口', 'switch-window', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"切换当前最新窗口\",\n  \"value\": \"switch-window\",\n  \"configList\": []\n}', 2, 749315493604663470, 1, '2025-08-22 18:14:01', 1, '2025-08-25 15:27:51');
INSERT IGNORE INTO `sys_dict_item` VALUES (749331149737730056, '切换指定窗口', 'switch-windows', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"切换指定窗口\",\n  \"value\": \"switch-windows\",\n  \"configList\": [\n    {\n      \"paramsName\": \"value\",\n      \"paramsValue\": \"1\"\n    }\n  ]\n}', 2, 749315493604663470, 1, '2025-08-22 18:14:28', 1, '2025-08-25 15:27:53');
INSERT IGNORE INTO `sys_dict_item` VALUES (749331555255623694, '切换Iframe控件', 'switch-Iframe', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"切换Iframe控件\",\n  \"value\": \"switch-Iframe\",\n  \"configList\": [{\n    \"paramsName\": \"value\",\n    \"paramsValue\": \"1\"\n  },\n  {\n    \"paramsName\": \"locator\",\n    \"paramsValue\": \"xpath=(//iframe[@region=\'center\'])[1]\"\n  },\n  {\n    \"paramsName\": \"skip\",\n    \"paramsValue\": \"locator（默认locator，可跳过locator和expect，跳过后会标记为成功）\"\n  }]\n}', 2, 749315493604663470, 1, '2025-08-22 18:16:05', 1, '2025-08-25 15:27:55');
INSERT IGNORE INTO `sys_dict_item` VALUES (749331843110707220, '返回上一级Iframe控件', 'return-Iframe', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"返回上一级Iframe控件\",\n  \"value\": \"return-Iframe\",\n  \"configList\": []\n}', 2, 749315493604663470, 1, '2025-08-22 18:17:13', 1, '2025-08-25 15:27:58');
INSERT IGNORE INTO `sys_dict_item` VALUES (749331953882275863, '返回最上级Iframe控件', 'quit-Iframe', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"返回最上级Iframe控件\",\n  \"value\": \"quit-Iframe\",\n  \"configList\": []\n}', 2, 749315493604663470, 1, '2025-08-22 18:17:40', 1, '2025-08-25 15:28:02');
INSERT IGNORE INTO `sys_dict_item` VALUES (749332087810596891, 'Web端执行js脚本操作', 'javascript-executor', 'primary', 999, '{\n  \"type\": \"浏览器操作\",\n  \"name\": \"Web端执行js脚本操作\",\n  \"value\": \"javascript-executor\",\n  \"configList\": [\n    {\n      \"paramsName\": \"script\",\n      \"paramsValue\": \"document.getElementsByClassName(\'el-input__inner\')[0].maxLength = 64\"\n    }\n  ]\n}', 2, 749315493604663470, 1, '2025-08-22 18:18:12', 1, '2025-08-25 15:28:06');
INSERT IGNORE INTO `sys_dict_item` VALUES (846823146316111880, '跳过', '16', 'warning', 16, '', 1, 4, 1, '2026-05-18 18:52:51', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (871460801804763218, '已取消', '17', 'default', 17, '', 1, 4, 1, '2026-07-25 18:34:06', NULL, NULL);

-- changeset codex:dictionary-database-type-repair-20260824
-- comment 按字典编码重新解析 database_type 主键，修复存量库主键不一致导致的数据库类型字典项缺失。
INSERT INTO `sys_dict` (`id`, `name`, `code`, `description`, `is_system`, `create_user`, `create_time`)
VALUES (710901573273784761, '数据库类型', 'database_type', NULL, b'1', 1, '2025-05-08 17:09:03')
ON DUPLICATE KEY UPDATE `id` = `id`;

SELECT `id` INTO @database_type_dict_id
FROM `sys_dict`
WHERE `code` = 'database_type'
LIMIT 1;

UPDATE `sys_dict_item`
SET `dict_id` = @database_type_dict_id
WHERE `id` IN (
    710903583033266627, 710903833697456586, 710903956678644176,
    710904022038483411, 710904112010498518, 710904179559764441,
    710904250237981148, 710904312091382239, 710904370295738850,
    710904451858174440, 710904508028293611, 710904569302880750,
    710904649497973235, 710904735170826745, 710904794352456188,
    710904857325736447, 710904920705864194, 710904983125496325,
    710905748116214282, 710905843725373965, 710906015641506320,
    710906079923409427, 710906201734386199
);

INSERT IGNORE INTO `sys_dict_item` VALUES (710903583033266627, 'MongoDB', 'com.dbschema.MongoJdbcDriver', 'primary', 23, 'mongodb://userName:passWord@localhost:port/mydb?authSource=admin', 1, @database_type_dict_id, 1, '2025-05-08 17:17:02', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710903833697456586, 'DM', 'dm.jdbc.driver.DmDriver', 'primary', 22, 'jdbc:dm://localhost:port/schema=mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:18:02', 1, '2025-05-08 17:18:07');
INSERT IGNORE INTO `sys_dict_item` VALUES (710903956678644176, 'Hbase', 'org.apache.phoenix.jdbc.PhoenixDriver', 'primary', 21, 'jdbc:phoenix:localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:18:31', 1, '2025-05-12 16:07:11');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904022038483411, 'TDengine', 'com.taosdata.jdbc.rs.RestfulDriver', 'primary', 20, 'jdbc:TAOS-RS://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:18:47', 1, '2025-05-12 16:07:03');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904112010498518, 'Gbase8s', 'com.gbasedbt.jdbc.Driver', 'primary', 19, 'jdbc:gbasedbt-sqli://localhost:port/mydb:GBASEDBTSERVER=gbaseserver;CLIENT_LOCALE=zh_cn.utf8;SQLMODE=GBase;NEWCODESET=UTF8,zh_cn.UTF8,57372;DB_LOCALE=zh_CN.57372;', 1, @database_type_dict_id, 1, '2025-05-08 17:19:08', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904179559764441, 'Gbase8a', 'com.gbase.jdbc.Driver', 'primary', 18, 'jdbc:gbase://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:19:24', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904250237981148, 'GaussDB', 'org.postgresql.Driver', 'primary', 6, 'jdbc:postgresql://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:25:35', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904312091382239, 'Cache', 'com.intersys.jdbc.CacheDriver', 'primary', 17, 'jdbc:Cache://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:19:56', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904370295738850, 'DB2', 'com.ibm.db2.jcc.DB2Driver', 'primary', 16, 'jdbc:db2://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:20:10', 1, '2025-05-08 17:20:14');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904451858174440, 'Informix', 'com.informix.jdbc.IfxDriver', 'primary', 15, 'jdbc:informix-sqli://localhost:port/mydb:informixserver=informix', 1, @database_type_dict_id, 1, '2025-05-08 17:20:29', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904508028293611, 'IRIS', 'com.intersystems.jdbc.IRISDriver', 'primary', 14, 'jdbc:IRIS://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:20:43', 1, '2025-05-12 16:06:49');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904569302880750, 'KingBase', 'com.kingbase8.Driver', 'primary', 13, 'jdbc:kingbase8://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:20:57', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904649497973235, 'MariaDB', 'org.mariadb.jdbc.Driver', 'primary', 12, 'jdbc:mysql://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:21:16', 1, '2025-05-08 17:21:21');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904735170826745, 'Teradata', 'com.teradata.jdbc.TeraDriver', 'primary', 11, 'jdbc:teradata://localhost/DATABASE=mydb,DBS_PORT=port', 1, @database_type_dict_id, 1, '2025-05-08 17:21:37', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904794352456188, 'OceanBase', 'com.mysql.jdbc.Driver', 'primary', 10, 'jdbc:mysql://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:21:51', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904857325736447, 'TiDB', 'com.mysql.cj.jdbc.Driver', 'primary', 9, 'jdbc:mysql://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:22:06', NULL, NULL);
INSERT IGNORE INTO `sys_dict_item` VALUES (710904920705864194, 'Hive', 'org.apache.hive.jdbc.HiveDriver', 'primary', 8, 'jdbc:hive2://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:22:21', 1, '2025-05-12 16:06:28');
INSERT IGNORE INTO `sys_dict_item` VALUES (710904983125496325, 'Sybase', 'com.sybase.jdbc4.jdbc.SybDriver', 'primary', 7, 'jdbc:sybase:Tds:localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:22:36', 1, '2025-05-12 16:06:20');
INSERT IGNORE INTO `sys_dict_item` VALUES (710905748116214282, 'Greenplum', 'org.postgresql.Driver', 'primary', 5, 'jdbc:postgresql://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:25:38', 1, '2025-05-10 09:50:26');
INSERT IGNORE INTO `sys_dict_item` VALUES (710905843725373965, 'PostgreSQL', 'org.postgresql.Driver', 'primary', 4, 'jdbc:postgresql://localhost:port/mydb?encrypt=false&trustServerCertificate=false', 1, @database_type_dict_id, 1, '2025-05-08 17:26:01', 1, '2025-05-12 16:28:22');
INSERT IGNORE INTO `sys_dict_item` VALUES (710906015641506320, 'SQLServer', 'com.microsoft.sqlserver.jdbc.SQLServerDriver', 'primary', 3, 'jdbc:sqlserver://localhost:port;databaseName=mydb;encrypt=false;trustServerCertificate=false', 1, @database_type_dict_id, 1, '2025-05-08 17:26:42', 1, '2025-05-12 16:27:49');
INSERT IGNORE INTO `sys_dict_item` VALUES (710906079923409427, 'Oracle', 'oracle.jdbc.driver.OracleDriver', 'primary', 2, 'jdbc:oracle:thin:@localhost:port:mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:26:57', 1, '2025-05-12 16:05:39');
INSERT IGNORE INTO `sys_dict_item` VALUES (710906201734386199, 'MySQL', 'com.mysql.jdbc.Driver', 'primary', 1, 'jdbc:mysql://localhost:port/mydb?useUnicode=true&characterEncoding=utf-8&useSSL=false', 1, @database_type_dict_id, 1, '2025-05-08 17:27:26', 1, '2025-05-12 16:28:05');

-- changeset codex:dictionary-item-unique-key-repair-20260824
-- comment 数据库类型允许不同标签复用同一驱动值，避免 Greenplum/PostgreSQL/MySQL 被旧唯一索引静默跳过。
SELECT `id` INTO @database_type_dict_id
FROM `sys_dict`
WHERE `code` = 'database_type'
LIMIT 1;

SET @drop_dict_item_index_sql = (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE `sys_dict_item` DROP INDEX `uk_value_dict_id`',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_dict_item'
      AND index_name = 'uk_value_dict_id'
);
PREPARE drop_dict_item_index_stmt FROM @drop_dict_item_index_sql;
EXECUTE drop_dict_item_index_stmt;
DEALLOCATE PREPARE drop_dict_item_index_stmt;

SET @add_dict_item_index_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_dict_item` ADD UNIQUE INDEX `uk_label_value_dict_id` (`label`, `value`, `dict_id`)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_dict_item'
      AND index_name = 'uk_label_value_dict_id'
);
PREPARE add_dict_item_index_stmt FROM @add_dict_item_index_sql;
EXECUTE add_dict_item_index_stmt;
DEALLOCATE PREPARE add_dict_item_index_stmt;

INSERT IGNORE INTO `sys_dict_item`
(`id`, `label`, `value`, `color`, `sort`, `description`, `status`, `dict_id`, `create_user`, `create_time`, `update_user`, `update_time`)
VALUES
(710905748116214282, 'Greenplum', 'org.postgresql.Driver', 'primary', 5, 'jdbc:postgresql://localhost:port/mydb', 1, @database_type_dict_id, 1, '2025-05-08 17:25:38', 1, '2025-05-10 09:50:26'),
(710905843725373965, 'PostgreSQL', 'org.postgresql.Driver', 'primary', 4, 'jdbc:postgresql://localhost:port/mydb?encrypt=false&trustServerCertificate=false', 1, @database_type_dict_id, 1, '2025-05-08 17:26:01', 1, '2025-05-12 16:28:22'),
(710906201734386199, 'MySQL', 'com.mysql.jdbc.Driver', 'primary', 1, 'jdbc:mysql://localhost:port/mydb?useUnicode=true&characterEncoding=utf-8&useSSL=false', 1, @database_type_dict_id, 1, '2025-05-08 17:27:26', 1, '2025-05-12 16:28:05');

-- rollback ALTER TABLE `sys_dict_item` DROP INDEX `uk_label_value_dict_id`, ADD UNIQUE INDEX `uk_value_dict_id` (`value`, `dict_id`);
