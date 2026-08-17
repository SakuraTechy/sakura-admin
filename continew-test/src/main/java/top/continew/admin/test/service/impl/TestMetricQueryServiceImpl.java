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

import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.CATEGORY_EXPR;
import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.ENGINE_EXPR;
import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.TERMINAL_EXPR;
import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.TIME_EXPR;
import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.TRIGGER_EXPR;
import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.sumCategory;
import static top.continew.admin.test.service.impl.TestMetricSqlExpressions.sumTerminal;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.admin.test.model.query.TestMetricScopeQuery;
import top.continew.admin.test.model.resp.TestMetricBreakdownResp;
import top.continew.admin.test.model.resp.TestMetricFailureResp;
import top.continew.admin.test.model.resp.TestMetricSummaryResp;
import top.continew.admin.test.model.resp.TestMetricTrendResp;
import top.continew.admin.test.service.TestMetricQueryService;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 基于规范化执行事实的测试度量查询。
 */
@Service
@RequiredArgsConstructor
public class TestMetricQueryServiceImpl implements TestMetricQueryService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern
        .compile("(?i)(password|passwd|pwd|token|secret|authorization|api[-_]?key|credential)(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+\\-/]+=*");

    private final JdbcTemplate jdbcTemplate;
    private final ProjectConfigMapper projectConfigMapper;
    private final ProjectVersionConfigMapper projectVersionConfigMapper;

    @Override
    public TestMetricSummaryResp getSummary(TestMetricScopeQuery query) {
        MetricScope scope = resolveScope(query);
        Aggregate current = aggregate(scope);
        Aggregate previous = aggregate(scope.previous());
        long eligibleSceneCount = countEligibleScenes(scope);

        TestMetricSummaryResp resp = new TestMetricSummaryResp();
        resp.setProjectId(scope.projectId());
        resp.setVersionId(scope.versionId());
        resp.setStartDate(scope.startDate());
        resp.setEndDate(scope.endDate());
        resp.setRunCount(current.runCount());
        resp.setSceneExecutionCount(current.sceneExecutionCount());
        resp.setEligibleSceneCount(eligibleSceneCount);
        resp.setExecutedSceneCount(current.executedSceneCount());
        resp.setPassCount(current.passCount());
        resp.setFailCount(current.failCount());
        resp.setSkipCount(current.skipCount());
        resp.setCancelCount(current.cancelCount());
        resp.setInfraFailCount(current.infraFailCount());
        resp.setOtherCount(current.otherCount());
        resp.setCaseTotal(current.caseTotal());
        resp.setCasePass(current.casePass());
        resp.setCaseFail(current.caseFail());
        resp.setCaseSkip(current.caseSkip());
        resp.setStepTotal(current.stepTotal());
        resp.setStepPass(current.stepPass());
        resp.setStepFail(current.stepFail());
        resp.setStepSkip(current.stepSkip());
        resp.setAverageDurationMs(current.averageDurationMs());
        resp.setDurationTotalMs(current.durationTotalMs());
        resp.setDurationSampleCount(current.durationSampleCount());
        resp.setExactDimensionCount(current.exactDimensionCount());
        resp.setInferredDimensionCount(current.inferredDimensionCount());
        resp.setMissingDimensionCount(current.missingDimensionCount());
        resp.setPassRate(rateMetric(current.passCount(), current.passCount() + current.failCount(), previous
            .passCount(), previous.passCount() + previous.failCount()));
        resp.setExecutionCoverage(rateMetric(current.executedSceneCount(), eligibleSceneCount, previous
            .executedSceneCount(), eligibleSceneCount));
        return resp;
    }

    @Override
    public TestMetricTrendResp getTrends(TestMetricScopeQuery query) {
        MetricScope scope = resolveScope(query);
        String sql = "SELECT DATE(" + TIME_EXPR + ") metric_date, " + "COUNT(DISTINCT CASE WHEN " + TERMINAL_EXPR + " THEN COALESCE(NULLIF(e.run_key, ''), e.execution_key) END) run_count, " + sumTerminal("scene_execution_count") + ", " + "COUNT(DISTINCT CASE WHEN " + TERMINAL_EXPR + " THEN e.scene_id END) executed_scene_count, " + sumCategory("PASSED", "pass_count") + ", " + sumCategory("FAILED", "fail_count") + ", " + sumCategory("SKIPPED", "skip_count") + ", " + sumCategory("CANCELLED", "cancel_count") + ", " + sumCategory("INFRA_FAILED", "infra_fail_count") + ", " + sumCategory("OTHER", "other_count") + ", " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN e.duration_ms ELSE 0 END), 0) duration_total_ms, " + "COUNT(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN 1 END) duration_sample_count " + "FROM automation_ui_execution e WHERE " + scope
            .predicate() + " GROUP BY DATE(" + TIME_EXPR + ") ORDER BY metric_date";
        Map<LocalDate, TestMetricTrendResp.TrendPoint> byDate = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            TestMetricTrendResp.TrendPoint point = new TestMetricTrendResp.TrendPoint();
            point.setDate(rs.getDate("metric_date").toLocalDate());
            point.setRunCount(rs.getLong("run_count"));
            point.setSceneExecutionCount(rs.getLong("scene_execution_count"));
            point.setExecutedSceneCount(rs.getLong("executed_scene_count"));
            point.setPassCount(rs.getLong("pass_count"));
            point.setFailCount(rs.getLong("fail_count"));
            point.setSkipCount(rs.getLong("skip_count"));
            point.setCancelCount(rs.getLong("cancel_count"));
            point.setInfraFailCount(rs.getLong("infra_fail_count"));
            point.setOtherCount(rs.getLong("other_count"));
            point.setDurationTotalMs(rs.getLong("duration_total_ms"));
            point.setDurationSampleCount(rs.getLong("duration_sample_count"));
            point.setPassRate(percent(point.getPassCount(), point.getPassCount() + point.getFailCount()));
            byDate.put(point.getDate(), point);
        }, scope.args().toArray());

        TestMetricTrendResp resp = new TestMetricTrendResp();
        for (LocalDate date = scope.startDate(); !date.isAfter(scope.endDate()); date = date.plusDays(1)) {
            TestMetricTrendResp.TrendPoint point = byDate.get(date);
            if (point == null) {
                point = new TestMetricTrendResp.TrendPoint();
                point.setDate(date);
                point.setPassRate(percent(0, 0));
            }
            resp.getPoints().add(point);
        }
        return resp;
    }

    @Override
    public TestMetricBreakdownResp getBreakdown(TestMetricScopeQuery query, String dimension) {
        MetricScope scope = resolveScope(query);
        BreakdownDimension breakdown = BreakdownDimension.resolve(dimension);
        String sql = "SELECT " + breakdown.keyExpression() + " metric_key, " + breakdown
            .labelExpression() + " metric_label, COUNT(*) metric_count FROM automation_ui_execution e " + breakdown
                .joinClause() + " WHERE " + scope
                    .predicate() + " AND " + TERMINAL_EXPR + " GROUP BY metric_key, metric_label ORDER BY metric_count DESC, metric_label ASC";
        List<TestMetricBreakdownResp.BreakdownItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            TestMetricBreakdownResp.BreakdownItem item = new TestMetricBreakdownResp.BreakdownItem();
            item.setKey(StringUtils.defaultIfBlank(rs.getString("metric_key"), "UNKNOWN"));
            item.setLabel(StringUtils.defaultIfBlank(rs.getString("metric_label"), item.getKey()));
            item.setCount(rs.getLong("metric_count"));
            return item;
        }, scope.args().toArray());
        long total = items.stream().mapToLong(TestMetricBreakdownResp.BreakdownItem::getCount).sum();
        items.forEach(item -> item.setRatio(percent(item.getCount(), total)));

        TestMetricBreakdownResp resp = new TestMetricBreakdownResp();
        resp.setDimension(breakdown.apiName());
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Override
    public TestMetricFailureResp getFailures(TestMetricScopeQuery query, Integer limit) {
        MetricScope scope = resolveScope(query);
        int safeLimit = Math.max(1, Math.min(limit == null ? 10 : limit, 50));
        String latestErrorCode = "SUBSTRING_INDEX(GROUP_CONCAT(COALESCE(e.error_code, '') ORDER BY " + TIME_EXPR + " DESC SEPARATOR '||'), '||', 1)";
        String latestErrorMessage = "SUBSTRING_INDEX(GROUP_CONCAT(REPLACE(COALESCE(e.error_message, ''), '||', ' ') ORDER BY " + TIME_EXPR + " DESC SEPARATOR '||'), '||', 1)";
        String sql = "SELECT e.scene_id, MAX(e.scene_key) scene_key, MAX(s.name) scene_name, " + "e.module_id, MAX(m.name) module_name, MAX(e.scene_level) scene_level, " + sumCategory("FAILED", "fail_count") + ", " + sumCategory("INFRA_FAILED", "infra_fail_count") + ", MAX(" + TIME_EXPR + ") last_failed_at, " + latestErrorCode + " last_error_code, " + latestErrorMessage + " last_error_message FROM automation_ui_execution e " + "LEFT JOIN automation_ui_scene s ON s.id = e.scene_id " + "LEFT JOIN project_module_config m ON m.id = e.module_id WHERE " + scope
            .predicate() + " AND " + TERMINAL_EXPR + " AND " + CATEGORY_EXPR + " IN ('FAILED', 'INFRA_FAILED') GROUP BY e.scene_id, e.module_id " + "ORDER BY (fail_count + infra_fail_count) DESC, last_failed_at DESC LIMIT " + safeLimit;
        List<TestMetricFailureResp.FailureItem> items = jdbcTemplate.query(sql, this::mapFailure, scope.args()
            .toArray());
        TestMetricFailureResp resp = new TestMetricFailureResp();
        resp.setItems(items);
        return resp;
    }

    private Aggregate aggregate(MetricScope scope) {
        String sql = "SELECT " + "COUNT(DISTINCT CASE WHEN " + TERMINAL_EXPR + " THEN COALESCE(NULLIF(e.run_key, ''), e.execution_key) END) run_count, " + sumTerminal("scene_execution_count") + ", " + "COUNT(DISTINCT CASE WHEN " + TERMINAL_EXPR + " THEN e.scene_id END) executed_scene_count, " + sumCategory("PASSED", "pass_count") + ", " + sumCategory("FAILED", "fail_count") + ", " + sumCategory("SKIPPED", "skip_count") + ", " + sumCategory("CANCELLED", "cancel_count") + ", " + sumCategory("INFRA_FAILED", "infra_fail_count") + ", " + sumCategory("OTHER", "other_count") + ", " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_total ELSE 0 END), 0) case_total, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_pass ELSE 0 END), 0) case_pass, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_fail ELSE 0 END), 0) case_fail, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_skip ELSE 0 END), 0) case_skip, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_total ELSE 0 END), 0) step_total, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_pass ELSE 0 END), 0) step_pass, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_fail ELSE 0 END), 0) step_fail, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_skip ELSE 0 END), 0) step_skip, " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN e.duration_ms ELSE 0 END), 0) duration_total_ms, " + "COUNT(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN 1 END) duration_sample_count, " + "COALESCE(ROUND(AVG(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN e.duration_ms END)), 0) average_duration_ms, " + sum("(" + TERMINAL_EXPR + ") AND UPPER(COALESCE(e.dimension_quality, '')) = 'EXACT'", "exact_dimension_count") + ", " + sum("(" + TERMINAL_EXPR + ") AND UPPER(COALESCE(e.dimension_quality, '')) = 'INFERRED'", "inferred_dimension_count") + ", " + sum("(" + TERMINAL_EXPR + ") AND UPPER(COALESCE(e.dimension_quality, '')) NOT IN ('EXACT', 'INFERRED')", "missing_dimension_count") + " FROM automation_ui_execution e WHERE " + scope
            .predicate();
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> mapAggregate(rs), scope.args().toArray());
    }

    private long countEligibleScenes(MetricScope scope) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM automation_ui_scene WHERE project_id = ?")
            .append(" AND status = 1 AND del_flag = 3");
        List<Object> args = new ArrayList<>();
        args.add(scope.projectId());
        if (scope.versionId() != null) {
            sql.append(" AND version_id = ?");
            args.add(scope.versionId());
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private MetricScope resolveScope(TestMetricScopeQuery query) {
        if (query == null || query.getProjectId() == null) {
            throw new BusinessException("项目 ID 不能为空");
        }
        ProjectConfigDO project = projectConfigMapper.selectById(query.getProjectId());
        if (project == null || !StatusTypeEnum.NORMAL.equals(project.getDelFlag())) {
            throw new BusinessException("项目不存在或已删除");
        }
        validateProjectAccess(project);
        if (query.getVersionId() != null) {
            ProjectVersionConfigDO version = projectVersionConfigMapper.selectById(query.getVersionId());
            if (version == null || !Objects.equals(query.getProjectId(), version
                .getProjectId()) || !StatusTypeEnum.NORMAL.equals(version.getDelFlag())) {
                throw new BusinessException("项目版本不存在或不属于当前项目");
            }
        }
        LocalDate endDate = query.getEndDate() == null ? LocalDate.now() : query.getEndDate();
        LocalDate startDate = query.getStartDate() == null
            ? endDate.minusDays(DEFAULT_RANGE_DAYS - 1L)
            : query.getStartDate();
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new BusinessException("单次查询日期范围不能超过 366 天");
        }
        return buildScope(query.getProjectId(), query.getVersionId(), startDate, endDate, query
            .getExecutionEngine(), query.getTriggerType(), query.getEnvironmentId());
    }

    private void validateProjectAccess(ProjectConfigDO project) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户，无法访问项目测试度量");
        }
        if (UserContextHolder.getContext().isAdmin() || Objects.equals(userId, project.getCreateUser())) {
            return;
        }
        List<String> members = project.getMember();
        if (members == null || !members.contains(String.valueOf(userId))) {
            throw new BusinessException("无权访问当前项目的测试度量");
        }
    }

    private MetricScope buildScope(Long projectId,
                                   Long versionId,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   String executionEngine,
                                   String triggerType,
                                   Long environmentId) {
        StringBuilder predicate = new StringBuilder("e.project_id = ? AND ").append(TIME_EXPR)
            .append(" >= ? AND ")
            .append(TIME_EXPR)
            .append(" < ?");
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        args.add(Timestamp.valueOf(startDate.atStartOfDay()));
        args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        if (versionId != null) {
            predicate.append(" AND e.version_id = ?");
            args.add(versionId);
        }
        if (StringUtils.isNotBlank(executionEngine)) {
            predicate.append(" AND ").append(ENGINE_EXPR).append(" = ?");
            args.add(canonicalEngine(executionEngine));
        }
        if (StringUtils.isNotBlank(triggerType)) {
            predicate.append(" AND ").append(TRIGGER_EXPR).append(" = ?");
            args.add(triggerType.trim().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        if (environmentId != null) {
            predicate.append(" AND e.project_environment_id = ?");
            args.add(environmentId);
        }
        return new MetricScope(projectId, versionId, startDate, endDate, StringUtils
            .trimToNull(executionEngine), StringUtils.trimToNull(triggerType), environmentId, predicate
                .toString(), args);
    }

    private Aggregate mapAggregate(ResultSet rs) throws SQLException {
        return new Aggregate(rs.getLong("run_count"), rs.getLong("scene_execution_count"), rs
            .getLong("executed_scene_count"), rs.getLong("pass_count"), rs.getLong("fail_count"), rs
                .getLong("skip_count"), rs.getLong("cancel_count"), rs.getLong("infra_fail_count"), rs
                    .getLong("other_count"), rs.getLong("case_total"), rs.getLong("case_pass"), rs
                        .getLong("case_fail"), rs.getLong("case_skip"), rs.getLong("step_total"), rs
                            .getLong("step_pass"), rs.getLong("step_fail"), rs.getLong("step_skip"), rs
                                .getLong("average_duration_ms"), rs.getLong("duration_total_ms"), rs
                                    .getLong("duration_sample_count"), rs.getLong("exact_dimension_count"), rs
                                        .getLong("inferred_dimension_count"), rs.getLong("missing_dimension_count"));
    }

    private TestMetricFailureResp.FailureItem mapFailure(ResultSet rs, int rowNum) throws SQLException {
        TestMetricFailureResp.FailureItem item = new TestMetricFailureResp.FailureItem();
        item.setSceneId(rs.getLong("scene_id"));
        item.setSceneKey(rs.getString("scene_key"));
        item.setSceneName(StringUtils.defaultIfBlank(rs.getString("scene_name"), rs.getString("scene_key")));
        Object moduleId = rs.getObject("module_id");
        item.setModuleId(moduleId == null ? null : ((Number)moduleId).longValue());
        item.setModuleName(StringUtils.defaultIfBlank(rs.getString("module_name"), "未归类"));
        item.setSceneLevel(StringUtils.defaultIfBlank(rs.getString("scene_level"), "未指定"));
        item.setFailCount(rs.getLong("fail_count"));
        item.setInfraFailCount(rs.getLong("infra_fail_count"));
        Timestamp lastFailedAt = rs.getTimestamp("last_failed_at");
        item.setLastFailedAt(lastFailedAt == null ? null : lastFailedAt.toLocalDateTime());
        item.setLastErrorCode(StringUtils.trimToNull(rs.getString("last_error_code")));
        item.setLastErrorMessage(sanitizeErrorMessage(rs.getString("last_error_message")));
        return item;
    }

    private String sanitizeErrorMessage(String value) {
        String message = StringUtils.trimToNull(value);
        if (message == null) {
            return null;
        }
        message = SENSITIVE_ASSIGNMENT.matcher(message).replaceAll("$1$2***");
        message = BEARER_TOKEN.matcher(message).replaceAll("Bearer ***");
        return StringUtils.abbreviate(message, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private TestMetricSummaryResp.RateMetric rateMetric(long numerator,
                                                        long denominator,
                                                        long previousNumerator,
                                                        long previousDenominator) {
        BigDecimal currentRate = percent(numerator, denominator);
        BigDecimal previousRate = percent(previousNumerator, previousDenominator);
        TestMetricSummaryResp.RateMetric metric = new TestMetricSummaryResp.RateMetric();
        metric.setNumerator(numerator);
        metric.setDenominator(denominator);
        metric.setRate(currentRate);
        metric.setPreviousRate(previousRate);
        metric.setChangePoints(currentRate.subtract(previousRate).setScale(2, RoundingMode.HALF_UP));
        return metric;
    }

    static BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static String canonicalEngine(String value) {
        String engine = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (engine) {
            case "playwright", "runner", "playwright-runner" -> "playwright-runner";
            case "chrome-devtools-protocol", "cdp", "extension-cdp" -> "extension-cdp";
            default -> engine;
        };
    }

    private static String sum(String condition, String alias) {
        return "COALESCE(SUM(CASE WHEN " + condition + " THEN 1 ELSE 0 END), 0) " + alias;
    }

    private record MetricScope(Long projectId, Long versionId, LocalDate startDate, LocalDate endDate,
                               String executionEngine, String triggerType, Long environmentId, String predicate,
                               List<Object> args) {

        private MetricScope previous() {
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            LocalDate previousEnd = startDate.minusDays(1);
            LocalDate previousStart = previousEnd.minusDays(days - 1);
            return new TestMetricQueryServiceImpl.ScopeFactory().build(this, previousStart, previousEnd);
        }
    }

    /** Keeps previous-period construction inside the outer service's SQL contract. */
    private static final class ScopeFactory {
        private MetricScope build(MetricScope source, LocalDate startDate, LocalDate endDate) {
            String predicate = source.predicate();
            List<Object> args = new ArrayList<>(source.args());
            args.set(1, Timestamp.valueOf(startDate.atStartOfDay()));
            args.set(2, Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
            return new MetricScope(source.projectId(), source.versionId(), startDate, endDate, source
                .executionEngine(), source.triggerType(), source.environmentId(), predicate, args);
        }
    }

    private record Aggregate(long runCount, long sceneExecutionCount, long executedSceneCount, long passCount,
                             long failCount, long skipCount, long cancelCount, long infraFailCount, long otherCount,
                             long caseTotal, long casePass, long caseFail, long caseSkip, long stepTotal, long stepPass,
                             long stepFail, long stepSkip, long averageDurationMs, long durationTotalMs,
                             long durationSampleCount, long exactDimensionCount, long inferredDimensionCount,
                             long missingDimensionCount) {
    }

    private enum BreakdownDimension {
        RESULT("result", CATEGORY_EXPR, "CASE " + CATEGORY_EXPR + " WHEN 'PASSED' THEN '通过' WHEN 'FAILED' THEN '失败' WHEN 'SKIPPED' THEN '跳过' WHEN 'CANCELLED' THEN '取消' WHEN 'INFRA_FAILED' THEN '基础设施失败' ELSE '其他' END", ""),
        ENGINE("engine", ENGINE_EXPR, "CASE " + ENGINE_EXPR + " WHEN 'playwright-runner' THEN 'Playwright Runner' WHEN 'extension-cdp' THEN 'Chrome DevTools Protocol' WHEN 'selenium' THEN 'Selenium' WHEN 'jenkins' THEN 'Jenkins' ELSE " + ENGINE_EXPR + " END", ""),
        TRIGGER("trigger", TRIGGER_EXPR, "CASE " + TRIGGER_EXPR + " WHEN 'manual' THEN '手动' WHEN 'test-plan' THEN '测试计划' WHEN 'schedule' THEN '定时任务' WHEN 'jenkins' THEN 'Jenkins' ELSE " + TRIGGER_EXPR + " END", ""),
        LEVEL("level", "COALESCE(NULLIF(e.scene_level, ''), 'UNSPECIFIED')", "COALESCE(NULLIF(e.scene_level, ''), '未指定')", ""),
        MODULE("module", "CAST(COALESCE(e.module_id, 0) AS CHAR)", "COALESCE(NULLIF(m.name, ''), '未归类')", "LEFT JOIN project_module_config m ON m.id = e.module_id");

        private final String apiName;
        private final String keyExpression;
        private final String labelExpression;
        private final String joinClause;

        BreakdownDimension(String apiName, String keyExpression, String labelExpression, String joinClause) {
            this.apiName = apiName;
            this.keyExpression = keyExpression;
            this.labelExpression = labelExpression;
            this.joinClause = joinClause;
        }

        private static BreakdownDimension resolve(String value) {
            String normalized = StringUtils.defaultIfBlank(value, "result").trim().toLowerCase(Locale.ROOT);
            for (BreakdownDimension dimension : values()) {
                if (dimension.apiName.equals(normalized)) {
                    return dimension;
                }
            }
            throw new BusinessException("不支持的分组维度：" + value);
        }

        private String apiName() {
            return apiName;
        }

        private String keyExpression() {
            return keyExpression;
        }

        private String labelExpression() {
            return labelExpression;
        }

        private String joinClause() {
            return StringUtils.isBlank(joinClause) ? "" : joinClause + " ";
        }
    }
}
