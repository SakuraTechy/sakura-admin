/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.automation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper;
import top.continew.admin.automation.model.query.AutomationUiExecutionHistoryQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionRunCreateReq;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;

/** 执行真实 Mapper SQL 和任务写入；内存库替代 MySQL，不连接用户环境。 */
public class AutomationUiExecutionRunServiceTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private AutomationUiExecutionQueryMapper mapper;
    private AutomationUiExecutionRunService service;
    private AutomationUiSceneAccessScopeResolver resolver;
    private AutomationUiExecutionHistoryQuery query;
    private AutomationUiExecutionScopeReq scope;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:run-" + UUID
            .randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE ALIAS JSON_CONTAINS FOR 'top.continew.admin.automation.service.AutomationUiExecutionRunServiceTest.jsonContains'");
        jdbc.execute("CREATE ALIAS JSON_QUOTE FOR 'top.continew.admin.automation.service.AutomationUiExecutionRunServiceTest.jsonQuote'");
        jdbc.execute("CREATE TABLE project_config(id bigint PRIMARY KEY, del_flag int, create_user bigint, member varchar(200))");
        jdbc.execute("CREATE TABLE project_version_config(id bigint PRIMARY KEY, project_id bigint, del_flag int)");
        jdbc.execute("CREATE TABLE project_module_config(id bigint PRIMARY KEY, project_id bigint, version_id bigint, del_flag int)");
        jdbc.execute("CREATE TABLE project_environment_config(id bigint PRIMARY KEY, project_id bigint)");
        jdbc.execute("CREATE TABLE automation_ui_scene(id bigint PRIMARY KEY, scene_id varchar(128), name varchar(128), project_id bigint, version_id bigint, module_id bigint, create_user bigint, del_flag int)");
        jdbc.execute("CREATE TABLE automation_ui_scene_execution_state(scene_id bigint PRIMARY KEY, execution_revision bigint)");
        jdbc.execute("CREATE TABLE automation_ui_execution_case(id bigint PRIMARY KEY, execution_id bigint, case_id varchar(128), attempt_no int, status varchar(32), result varchar(32), execute_status varchar(32), execute_result varchar(32))");
        jdbc.execute("CREATE TABLE automation_ui_execution(id bigint PRIMARY KEY, execution_key varchar(128), scene_id bigint, scene_key varchar(128), batch_id varchar(128), record_source varchar(32), record_type varchar(64), test_plan_id bigint, test_report_id bigint, trigger_type varchar(32), execution_engine varchar(32), status varchar(32), result varchar(32), execute_user_id bigint, execute_name varchar(128), project_environment_id bigint, project_environment_name varchar(128), case_total int, case_pass int, case_fail int, case_skip int, case_cancelled int, step_total int, step_pass int, step_fail int, step_skip int, started_at timestamp, finished_at timestamp, duration_ms bigint, create_time timestamp, update_time timestamp)");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path
                .of("../continew-webapi/src/main/resources/db/changelog/mysql/automation_ui_execution_run.sql")));
        }
        jdbc.update("INSERT INTO project_config VALUES (1, 3, 9, '[]')");
        jdbc.update("INSERT INTO project_version_config VALUES (1, 1, 3)");
        jdbc.update("INSERT INTO project_module_config VALUES (1, 1, 1, 3), (2, 1, 1, 3)");
        jdbc.update("INSERT INTO project_environment_config VALUES (1, 1), (2, 2)");
        for (long id = 1; id <= 26; id++) {
            jdbc.update("INSERT INTO automation_ui_scene VALUES (?, ?, ?, 1, 1, 1, 9, 3)", id, "SCENE_" + id, "场景" + id);
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        for (String file : List.of("AutomationUiExecutionQueryMapper.xml", "AutomationUiExecutionRunQueryMapper.xml")) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/" + file)) {
                new XMLMapperBuilder(input, config, file, config.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        mapper = session.getMapper(AutomationUiExecutionQueryMapper.class);
        resolver = mock(AutomationUiSceneAccessScopeResolver.class);
        when(resolver.currentScope()).thenReturn(new AutomationUiSceneAccessScopeResolver.AccessScope(9L, false, Set
            .of(), Set.of()));
        UserContext user = new UserContext();
        user.setId(9L);
        user.setUsername("测试用户");
        UserContextHolder.setContext(user, false);
        service = new AutomationUiExecutionRunService(jdbc, mapper, resolver);
        query = new AutomationUiExecutionHistoryQuery();
        query.setProjectId(1L);
        scope = new AutomationUiExecutionScopeReq();
        scope.setRecordSource("debug");
    }

    @AfterEach
    void close() {
        UserContextHolder.clearContext();
        if (session != null)
            session.close();
        if (jdbc != null)
            jdbc.execute("SHUTDOWN");
    }

    public static boolean jsonContains(String json, String value) {
        return json != null && json.contains(value);
    }

    public static String jsonQuote(String value) {
        return "\"" + value + "\"";
    }

    private String create(long count) {
        var req = new AutomationUiExecutionRunCreateReq();
        req.setSceneIds(LongStream.rangeClosed(1, count).boxed().toList());
        req.setProjectEnvironmentId(1L);
        req.setExecutionType("playwright-runner");
        return service.create(req);
    }

    private AutomationPlaywrightBatchCreateReq batch(String runId, long sceneId) {
        var req = new AutomationPlaywrightBatchCreateReq();
        req.setRunId(runId);
        req.setSceneKey(String.valueOf(sceneId));
        req.setProjectEnvironmentId(1L);
        req.setExecutionType("playwright-runner");
        return req;
    }

    private void execute(String runId, long sceneId, long executionId, String status, String result) {
        String batchId = "batch-" + executionId;
        if (runId != null)
            service.validateBatch(batch(runId, sceneId), sceneId);
        jdbc.update("INSERT INTO automation_ui_execution (id, execution_key, scene_id, scene_key, batch_id, record_source, execution_engine, status, result, execute_user_id, execute_name, case_total, case_pass, case_fail, case_skip, case_cancelled, started_at, finished_at, duration_ms, create_time, update_time) VALUES (?, ?, ?, ?, ?, 'debug', 'playwright-runner', ?, ?, 9, '测试用户', 2, 2, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", executionId, batchId, sceneId, "SCENE_" + sceneId, batchId, status, result);
        if (runId != null)
            service.attachBatch(runId, sceneId, batchId);
    }

    @Test
    void all26ScenesRemainOnePersistentHistoryRowAndChildrenArePagedInOrder() {
        String runId = create(26);
        for (long scene = 1; scene <= 26; scene++)
            execute(runId, scene, scene, "completed", "passed");
        service.finish(runId, null);
        assertThat(mapper.countRunHistory(query, scope, 9L, false)).isEqualTo(1);
        var row = mapper.selectRunHistoryPage(query, scope, 9L, false, 0, 10).get(0);
        assertThat(row.getRunId()).isEqualTo(runId);
        assertThat(row.getExecutionDbId()).isNull();
        assertThat(row.getSceneTotal()).isEqualTo(26);
        assertThat(row.getSceneCompleted()).isEqualTo(26);
        assertThat(row.getScenePass()).isEqualTo(26);
        assertThat(row.getCaseTotal()).isEqualTo(52);
        assertThat(row.getDurationMs()).isEqualTo(26000);
        assertThat(mapper.countRunScenes(runId, query, scope, 9L, false)).isEqualTo(26);
        assertThat(mapper.selectRunScenes(runId, query, scope, 9L, false, 20, 20)).extracting(item -> item
            .getSceneDbId()).containsExactly(21L, 22L, 23L, 24L, 25L, 26L);
    }

    @Test
    void cancellationKeepsUnstartedScenesAndPreventsLateCreationOrFailureOverwrite() {
        String runId = create(3);
        execute(runId, 1, 101, "running", "pending");
        assertThat(service.cancel(runId)).hasSize(1);
        service.finish(runId, 2L);
        var row = mapper.selectRunHistoryPage(query, scope, 9L, false, 0, 10).get(0);
        assertThat(row.getSceneTotal()).isEqualTo(3);
        assertThat(row.getSceneCancelled()).isEqualTo(2);
        assertThat(row.getSceneCompleted()).isEqualTo(2);
        assertThat(row.getStatus()).isEqualTo("running");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_ui_execution", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.validateBatch(batch(runId, 2), 2L)).hasMessageContaining("已结束或取消");
    }

    @Test
    void legacyRowsStaySeparateAndPlanRowsNeverEnterGroupedDebugHistory() {
        String first = create(2);
        execute(first, 1, 101, "completed", "passed");
        execute(first, 2, 102, "completed", "passed");
        service.finish(first, null);
        execute(null, 1, 201, "completed", "passed");
        execute(null, 2, 202, "completed", "passed");
        jdbc.update("UPDATE automation_ui_execution SET record_source = 'test', test_plan_id = 7, test_report_id = 8 WHERE id = 202");
        String second = create(2);
        jdbc.update("UPDATE automation_ui_execution_run SET create_time = '2026-10-08 20:00:00' WHERE id = ?", second);
        assertThat(mapper.countRunHistory(query, scope, 9L, false)).isEqualTo(3);
        assertThat(mapper.selectRunHistoryPage(query, scope, 9L, false, 0, 1)).hasSize(1);
        assertThat(mapper.selectRunHistoryPage(query, scope, 9L, false, 1, 1)).hasSize(1);
        query.setSceneDbId(2L);
        assertThat(mapper.countRunHistory(query, scope, 9L, false)).isEqualTo(2);
        assertThat(mapper.selectRunHistoryPage(query, scope, 9L, false, 0, 10)).allSatisfy(row -> assertThat(row
            .getSceneTotal()).isEqualTo(1));
    }

    @Test
    void rejectsForeignOwnerEnvironmentEnginePlanAndDuplicateChild() {
        String runId = create(2);
        var req = batch(runId, 1);
        req.setProjectEnvironmentId(2L);
        assertThatThrownBy(() -> service.validateBatch(req, 1L)).hasMessageContaining("环境或引擎");
        req.setProjectEnvironmentId(1L);
        req.setExecutionType("extension-cdp");
        assertThatThrownBy(() -> service.validateBatch(req, 1L)).hasMessageContaining("环境或引擎");
        req.setExecutionType("playwright-runner");
        req.setTestPlanId("9");
        assertThatThrownBy(() -> service.validateBatch(req, 1L)).hasMessageContaining("测试计划");
        assertThatThrownBy(() -> service.validateBatch(batch(runId, 3), 3L)).hasMessageContaining("不属于当前任务");
        execute(runId, 1, 301, "completed", "passed");
        assertThatThrownBy(() -> service.validateBatch(batch(runId, 1), 1L)).hasMessageContaining("已创建子批次");
        when(resolver.currentScope()).thenReturn(new AutomationUiSceneAccessScopeResolver.AccessScope(8L, false, Set
            .of(), Set.of()));
        assertThatThrownBy(() -> service.cancel(runId)).hasMessageContaining("无权操作");
        assertThat(mapper.countRunHistory(query, scope, 8L, false)).isZero();
        assertThat(mapper.countRunScenes(runId, query, scope, 8L, false)).isZero();
    }

    @Test
    void failedStartupHasOneFailedSceneAndRemainingCancelledWithoutInventingCaseResults() {
        String runId = create(3);
        service.finish(runId, 1L);
        var row = mapper.selectRunHistoryPage(query, scope, 9L, false, 0, 10).get(0);
        assertThat(row.getSceneFail()).isEqualTo(1);
        assertThat(row.getSceneCancelled()).isEqualTo(2);
        assertThat(row.getSceneCompleted()).isEqualTo(3);
        assertThat(row.getCaseTotal()).isZero();
    }

    @Test
    void cancellationWaitsForCreatingChildTransactionAndIncludesThatChild() throws Exception {
        String runId = create(2);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc
            .getDataSource()));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var cancelling = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var creation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                service.validateBatch(batch(runId, 1), 1L);
                entered.countDown();
                try {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("测试创建事务等待超时");
                } catch (InterruptedException error) {
                    throw new IllegalStateException(error);
                }
                execute(runId, 1, 501, "running", "pending");
            }));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var cancellation = executor.submit(() -> {
                cancelling.countDown();
                return transaction.execute(status -> service.cancel(runId));
            });
            assertThat(cancelling.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            creation.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(cancellation.get(5, java.util.concurrent.TimeUnit.SECONDS)).hasSize(1);
            assertThatThrownBy(() -> service.validateBatch(batch(runId, 2), 2L)).hasMessageContaining("已结束或取消");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedChildTransactionRollsBackExecutionAndItsMembershipTogether() {
        String runId = create(2);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc
            .getDataSource()));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            execute(runId, 1, 601, "running", "pending");
            throw new IllegalStateException("模拟提交前失败");
        })).hasMessageContaining("模拟提交前失败");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_ui_execution WHERE id = 601", Integer.class))
            .isZero();
        service.validateBatch(batch(runId, 1), 1L);
    }

    @Test
    void singleSceneSummaryUsesLatestCaseAcrossBatchesAndRetriesInsteadOfSceneCount() {
        execute(null, 1, 701, "completed", "failed");
        execute(null, 1, 702, "completed", "passed");
        jdbc.update("INSERT INTO automation_ui_execution_case VALUES (1, 701, 'CASE_1', 1, 'failed', 'failed', NULL, NULL), (2, 701, 'CASE_2', 1, 'passed', 'passed', NULL, NULL), (3, 702, 'CASE_1', 1, 'failed', 'failed', NULL, NULL), (4, 702, 'CASE_1', 2, 'passed', 'passed', NULL, NULL)");
        query.setSceneDbId(1L);
        var summary = mapper.selectHistoryCaseSummary(query, scope, 9L, false);
        assertThat(summary.getDimension()).isEqualTo("case");
        assertThat(summary.getTotal()).isEqualTo(2);
        assertThat(summary.getCompleted()).isEqualTo(2);
        assertThat(summary.getPassed()).isEqualTo(2);
        assertThat(summary.getFailed()).isZero();
    }

    @Test
    void singleSceneCaseSummaryKeepsStatusesDistinctAndIgnoresOtherScopes() {
        execute(null, 1, 801, "completed", "passed");
        execute(null, 2, 802, "completed", "failed");
        execute(null, 1, 803, "completed", "failed");
        jdbc.update("UPDATE automation_ui_execution SET record_source = 'test', test_plan_id = 7 WHERE id = 803");
        var statuses = List.of("passed", "failed", "blocked", "cancelled", "skipped", "running");
        for (int i = 0; i < statuses.size(); i++) {
            jdbc.update("INSERT INTO automation_ui_execution_case VALUES (?, 801, ?, 1, ?, ?, NULL, NULL)", i + 1, "CASE_" + i, statuses
                .get(i), statuses.get(i));
        }
        jdbc.update("INSERT INTO automation_ui_execution_case VALUES (20, 802, 'CASE_0', 1, 'failed', 'failed', NULL, NULL), (21, 803, 'CASE_0', 1, 'failed', 'failed', NULL, NULL)");
        query.setSceneDbId(1L);
        var summary = mapper.selectHistoryCaseSummary(query, scope, 9L, false);
        assertThat(summary.getTotal()).isEqualTo(6);
        assertThat(summary.getCompleted()).isEqualTo(5);
        assertThat(summary.getPassed()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getBlocked()).isEqualTo(1);
        assertThat(summary.getCancelled()).isEqualTo(1);
        assertThat(summary.getSkipped()).isEqualTo(1);
        assertThat(mapper.selectHistoryCaseSummary(query, scope, 8L, false).getTotal()).isZero();
        query.setSceneDbId(26L);
        var empty = mapper.selectHistoryCaseSummary(query, scope, 9L, false);
        assertThat(empty.getTotal()).isZero();
        assertThat(empty.getDimension()).isEqualTo("case");
    }
}
