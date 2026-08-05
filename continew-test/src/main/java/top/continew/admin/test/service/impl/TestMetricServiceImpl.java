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

package top.continew.admin.test.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.test.model.query.TestMetricQuery;
import top.continew.admin.test.model.query.TestMetricScopeQuery;
import top.continew.admin.test.model.resp.TestMetricResp;
import top.continew.admin.test.model.resp.TestMetricSummaryResp;
import top.continew.admin.test.service.TestMetricQueryService;
import top.continew.admin.test.service.TestMetricService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 旧版测试度量接口适配器，兼容一个发布周期。
 */
@Service
@RequiredArgsConstructor
public class TestMetricServiceImpl implements TestMetricService {

    private final TestMetricQueryService testMetricQueryService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public TestMetricResp getOverview(TestMetricQuery query) {
        Long projectId = resolveProjectId(query);
        Long versionId = resolveVersionId(query);
        if (projectId == null) {
            return emptyResponse();
        }

        TestMetricSummaryResp summary = summary(projectId, versionId, LocalDate.now().minusDays(29));
        TestMetricSummaryResp week = summary(projectId, versionId, LocalDate.now().with(DayOfWeek.MONDAY));
        TestMetricSummaryResp month = summary(projectId, versionId, LocalDate.now().withDayOfMonth(1));
        TestMetricSummaryResp year = summary(projectId, versionId, LocalDate.now().withDayOfYear(1));
        Map<String, Object> inventory = inventory(projectId, versionId);

        TestMetricResp resp = new TestMetricResp();
        resp.setProjectId(projectId);
        resp.setVersionId(versionId);
        resp.setTestPlanCount(count("test_plan", projectId, versionId));
        resp.setTestReportCount(count("test_report", projectId, versionId));
        resp.setTimedTaskCount(countTimedTasks(projectId, versionId));
        resp.setSceneCount(summary.getEligibleSceneCount());
        resp.setPassedSceneCount(summary.getPassCount());
        resp.setAutomationPassRate(summary.getPassRate().getRate());
        resp.setModuleMetric(moduleMetric(projectId, versionId));
        resp.setSceneMetric(sceneMetric(summary, inventory));
        resp.setExecutionMetric(executionMetric(resp.getTestReportCount(), summary, week, month, year));
        return resp;
    }

    private TestMetricSummaryResp summary(Long projectId, Long versionId, LocalDate startDate) {
        TestMetricScopeQuery query = new TestMetricScopeQuery();
        query.setProjectId(projectId);
        query.setVersionId(versionId);
        query.setStartDate(startDate);
        query.setEndDate(LocalDate.now());
        return testMetricQueryService.getSummary(query);
    }

    private TestMetricResp.ModuleMetric moduleMetric(Long projectId, Long versionId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) total_count, ")
            .append("COALESCE(SUM(create_time >= ?), 0) week_added_count, ")
            .append("COALESCE(SUM(create_time >= ?), 0) month_added_count, ")
            .append("COALESCE(SUM(create_time >= ?), 0) year_added_count ")
            .append("FROM project_module_config WHERE project_id = ? AND del_flag = 3");
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Timestamp.valueOf(LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay()));
        args.add(java.sql.Timestamp.valueOf(LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        args.add(java.sql.Timestamp.valueOf(LocalDate.now().withDayOfYear(1).atStartOfDay()));
        args.add(projectId);
        if (versionId != null) {
            sql.append(" AND version_id = ?");
            args.add(versionId);
        }
        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
            TestMetricResp.ModuleMetric metric = new TestMetricResp.ModuleMetric();
            metric.setTotalCount(rs.getLong("total_count"));
            metric.setWeekAddedCount(rs.getLong("week_added_count"));
            metric.setMonthAddedCount(rs.getLong("month_added_count"));
            metric.setYearAddedCount(rs.getLong("year_added_count"));
            return metric;
        }, args.toArray());
    }

    private Map<String, Object> inventory(Long projectId, Long versionId) {
        StringBuilder sql = new StringBuilder("SELECT ").append("COALESCE(SUM(UPPER(level) = 'P0'), 0) p0_count, ")
            .append("COALESCE(SUM(UPPER(level) = 'P1'), 0) p1_count, ")
            .append("COALESCE(SUM(UPPER(level) = 'P2'), 0) p2_count, ")
            .append("COALESCE(SUM(UPPER(level) = 'P3'), 0) p3_count, ")
            .append("COALESCE(SUM(create_time >= ?), 0) week_added_count, ")
            .append("COALESCE(SUM(create_time >= ?), 0) month_added_count, ")
            .append("COALESCE(SUM(create_time >= ?), 0) year_added_count ")
            .append("FROM automation_ui_scene WHERE project_id = ? AND status = 1 AND del_flag = 3");
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Timestamp.valueOf(LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay()));
        args.add(java.sql.Timestamp.valueOf(LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        args.add(java.sql.Timestamp.valueOf(LocalDate.now().withDayOfYear(1).atStartOfDay()));
        args.add(projectId);
        if (versionId != null) {
            sql.append(" AND version_id = ?");
            args.add(versionId);
        }
        return jdbcTemplate.queryForMap(sql.toString(), args.toArray());
    }

    private TestMetricResp.SceneMetric sceneMetric(TestMetricSummaryResp summary, Map<String, Object> inventory) {
        TestMetricResp.SceneMetric metric = new TestMetricResp.SceneMetric();
        metric.setTotalCount(summary.getEligibleSceneCount());
        metric.setP0Count(number(inventory.get("p0_count")));
        metric.setP1Count(number(inventory.get("p1_count")));
        metric.setP2Count(number(inventory.get("p2_count")));
        metric.setP3Count(number(inventory.get("p3_count")));
        metric.setWeekAddedCount(number(inventory.get("week_added_count")));
        metric.setMonthAddedCount(number(inventory.get("month_added_count")));
        metric.setYearAddedCount(number(inventory.get("year_added_count")));
        metric.setExecutedCount(summary.getExecutedSceneCount());
        metric.setPassedCount(summary.getPassCount());
        metric.setFailedCount(summary.getFailCount());
        metric.setSkippedCount(summary.getSkipCount());
        return metric;
    }

    private TestMetricResp.ExecutionMetric executionMetric(Long reportCount,
                                                           TestMetricSummaryResp summary,
                                                           TestMetricSummaryResp week,
                                                           TestMetricSummaryResp month,
                                                           TestMetricSummaryResp year) {
        TestMetricResp.ExecutionMetric metric = new TestMetricResp.ExecutionMetric();
        metric.setTotalReportCount(reportCount);
        metric.setWeekRunCount(week.getRunCount());
        metric.setMonthRunCount(month.getRunCount());
        metric.setYearRunCount(year.getRunCount());
        metric.setTotalRunSceneCount(summary.getSceneExecutionCount());
        metric.setDiscoveredDefectCount(0L);
        metric.setSavedManHours(zeroRate());
        metric.setAutomationCoverageRate(summary.getExecutionCoverage().getRate());
        metric.setAutomationExecuteRate(summary.getExecutionCoverage().getRate());
        metric.setAutomationPassRate(summary.getPassRate().getRate());
        metric.setDefectRate(zeroRate());
        return metric;
    }

    private Long count(String table, Long projectId, Long versionId) {
        if (!"test_plan".equals(table) && !"test_report".equals(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table)
            .append(" WHERE project_id = ? AND del_flag <> 4");
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (versionId != null) {
            sql.append(" AND version_id = ?");
            args.add(versionId);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private Long countTimedTasks(Long projectId, Long versionId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM test_timed_task t ")
            .append("JOIN test_plan p ON p.id = t.test_plan_id ")
            .append("WHERE p.project_id = ? AND p.del_flag <> 4 AND t.del_flag <> 4");
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (versionId != null) {
            sql.append(" AND p.version_id = ?");
            args.add(versionId);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private TestMetricResp emptyResponse() {
        TestMetricResp resp = new TestMetricResp();
        resp.setTestPlanCount(0L);
        resp.setTestReportCount(0L);
        resp.setTimedTaskCount(0L);
        resp.setSceneCount(0L);
        resp.setPassedSceneCount(0L);
        resp.setAutomationPassRate(zeroRate());

        TestMetricResp.ModuleMetric moduleMetric = new TestMetricResp.ModuleMetric();
        moduleMetric.setTotalCount(0L);
        moduleMetric.setWeekAddedCount(0L);
        moduleMetric.setMonthAddedCount(0L);
        moduleMetric.setYearAddedCount(0L);
        resp.setModuleMetric(moduleMetric);

        TestMetricResp.SceneMetric sceneMetric = new TestMetricResp.SceneMetric();
        sceneMetric.setTotalCount(0L);
        sceneMetric.setP0Count(0L);
        sceneMetric.setP1Count(0L);
        sceneMetric.setP2Count(0L);
        sceneMetric.setP3Count(0L);
        sceneMetric.setWeekAddedCount(0L);
        sceneMetric.setMonthAddedCount(0L);
        sceneMetric.setYearAddedCount(0L);
        sceneMetric.setExecutedCount(0L);
        sceneMetric.setPassedCount(0L);
        sceneMetric.setFailedCount(0L);
        sceneMetric.setSkippedCount(0L);
        resp.setSceneMetric(sceneMetric);

        TestMetricResp.ExecutionMetric executionMetric = new TestMetricResp.ExecutionMetric();
        executionMetric.setTotalReportCount(0L);
        executionMetric.setWeekRunCount(0L);
        executionMetric.setMonthRunCount(0L);
        executionMetric.setYearRunCount(0L);
        executionMetric.setTotalRunSceneCount(0L);
        executionMetric.setDiscoveredDefectCount(0L);
        executionMetric.setSavedManHours(zeroRate());
        executionMetric.setAutomationCoverageRate(zeroRate());
        executionMetric.setAutomationExecuteRate(zeroRate());
        executionMetric.setAutomationPassRate(zeroRate());
        executionMetric.setDefectRate(zeroRate());
        resp.setExecutionMetric(executionMetric);
        return resp;
    }

    private Long resolveProjectId(TestMetricQuery query) {
        if (query == null) {
            return null;
        }
        return query.getProjectId() != null
            ? query.getProjectId()
            : query.getUi() == null ? null : query.getUi().getProjectId();
    }

    private Long resolveVersionId(TestMetricQuery query) {
        if (query == null) {
            return null;
        }
        return query.getVersionId() != null
            ? query.getVersionId()
            : query.getUi() == null ? null : query.getUi().getVersionId();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal zeroRate() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
