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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.test.service.TestMetricAggregationService;

/**
 * 测试度量日汇总。每次按天全量替换，天然幂等并可修复迟到回调。
 */
@Service
@RequiredArgsConstructor
public class TestMetricAggregationServiceImpl implements TestMetricAggregationService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void aggregateDay(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("聚合日期不能为空");
        }
        transactionTemplate.executeWithoutResult(status -> replaceDay(date));
    }

    private void replaceDay(LocalDate date) {
        Timestamp start = Timestamp.valueOf(date.atStartOfDay());
        Timestamp end = Timestamp.valueOf(date.plusDays(1).atStartOfDay());
        jdbcTemplate.update("DELETE FROM test_metric_daily WHERE metric_date = ?", date);
        jdbcTemplate.update("DELETE FROM test_metric_scene_daily WHERE metric_date = ?", date);
        jdbcTemplate.update("DELETE FROM test_metric_inventory_daily WHERE metric_date = ?", date);
        jdbcTemplate.update("DELETE FROM test_metric_aggregation_state WHERE metric_date = ?", date);

        insertDaily(date, start, end);
        insertSceneDaily(date, start, end);
        insertInventory(date);
        insertAggregationState(date, start, end);
    }

    @Override
    public void backfill(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("回填日期范围无效");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= 730) {
            throw new IllegalArgumentException("单次回填不能超过 730 天");
        }
        transactionTemplate.executeWithoutResult(status -> backfillDimensions());
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            aggregateDay(date);
        }
    }

    private void backfillDimensions() {
        jdbcTemplate
            .update("UPDATE test_plan p SET p.version_id = (SELECT MIN(s.version_id) FROM automation_ui_scene s " + "WHERE s.project_id = p.project_id AND s.version_id IS NOT NULL AND (" + "JSON_CONTAINS(COALESCE(p.ui_test_scene, JSON_ARRAY()), CAST(s.id AS JSON), '$') OR " + "JSON_CONTAINS(COALESCE(p.ui_test_scene, JSON_ARRAY()), JSON_QUOTE(CAST(s.id AS CHAR)), '$'))) " + "WHERE p.version_id IS NULL AND (SELECT COUNT(DISTINCT s.version_id) FROM automation_ui_scene s " + "WHERE s.project_id = p.project_id AND s.version_id IS NOT NULL AND (" + "JSON_CONTAINS(COALESCE(p.ui_test_scene, JSON_ARRAY()), CAST(s.id AS JSON), '$') OR " + "JSON_CONTAINS(COALESCE(p.ui_test_scene, JSON_ARRAY()), JSON_QUOTE(CAST(s.id AS CHAR)), '$'))) = 1");
        jdbcTemplate
            .update("UPDATE test_report r JOIN test_plan p ON p.id = r.test_plan_id SET r.version_id = p.version_id " + "WHERE r.version_id IS NULL AND p.version_id IS NOT NULL AND r.project_id = p.project_id");
        jdbcTemplate
            .update("UPDATE automation_ui_execution e JOIN automation_ui_scene s ON s.id = e.scene_id SET " + "e.project_id = COALESCE(e.project_id, s.project_id), e.version_id = COALESCE(e.version_id, s.version_id), " + "e.module_id = COALESCE(e.module_id, s.module_id, 0), " + "e.scene_level = COALESCE(NULLIF(e.scene_level, ''), NULLIF(s.level, ''), 'UNSPECIFIED'), " + "e.run_key = COALESCE(NULLIF(e.run_key, ''), CASE WHEN e.test_report_id IS NOT NULL THEN CONCAT('REPORT:', e.test_report_id) " + "WHEN e.batch_id IS NOT NULL THEN CONCAT(" + ENGINE_EXPR + ", ':BATCH:', e.batch_id) ELSE CONCAT('EXECUTION:', e.id) END), " + "e.dimension_quality = CASE WHEN COALESCE(e.project_id, s.project_id) IS NOT NULL " + "AND COALESCE(e.version_id, s.version_id) IS NOT NULL THEN 'INFERRED' ELSE 'MISSING' END " + "WHERE e.project_id IS NULL OR e.version_id IS NULL OR e.module_id IS NULL OR e.scene_level IS NULL " + "OR e.scene_level = '' OR e.run_key IS NULL OR e.run_key = '' " + "OR UPPER(COALESCE(e.dimension_quality, '')) NOT IN ('EXACT', 'INFERRED')");
        jdbcTemplate
            .update("UPDATE automation_ui_execution e SET e.execution_engine = " + ENGINE_EXPR + ", e.trigger_type = " + TRIGGER_EXPR + " WHERE e.execution_engine IS NULL OR e.execution_engine = '' OR e.execution_engine <> " + ENGINE_EXPR + " OR e.trigger_type IS NULL OR e.trigger_type = '' OR e.trigger_type <> " + TRIGGER_EXPR);
    }

    private void insertDaily(LocalDate date, Timestamp start, Timestamp end) {
        String sql = "INSERT INTO test_metric_daily (metric_date, project_id, version_id, execution_engine, trigger_type, environment_id, " + "run_started_count, run_completed_count, scene_execution_count, scene_pass_count, scene_fail_count, scene_skip_count, " + "scene_cancel_count, scene_infra_fail_count, scene_other_count, case_total, case_pass, case_fail, case_skip, " + "step_total, step_pass, step_fail, step_skip, duration_total_ms, duration_sample_count, histogram_version, " + "source_max_execution_id, aggregation_time) SELECT ?, e.project_id, COALESCE(e.version_id, 0), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0), " + "COUNT(DISTINCT COALESCE(NULLIF(e.run_key, ''), e.execution_key)), " + "COUNT(DISTINCT CASE WHEN " + TERMINAL_EXPR + " THEN COALESCE(NULLIF(e.run_key, ''), e.execution_key) END), " + sumTerminal("scene_execution_count") + ", " + sumCategory("PASSED", "scene_pass_count") + ", " + sumCategory("FAILED", "scene_fail_count") + ", " + sumCategory("SKIPPED", "scene_skip_count") + ", " + sumCategory("CANCELLED", "scene_cancel_count") + ", " + sumCategory("INFRA_FAILED", "scene_infra_fail_count") + ", " + sumCategory("OTHER", "scene_other_count") + ", SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_total ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_pass ELSE 0 END), SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_fail ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_skip ELSE 0 END), SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_total ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_pass ELSE 0 END), SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_fail ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_skip ELSE 0 END), COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.duration_ms ELSE 0 END), 0), " + "COUNT(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN 1 END), 1, MAX(e.id), CURRENT_TIMESTAMP(3) " + "FROM automation_ui_execution e WHERE e.project_id IS NOT NULL AND " + TIME_EXPR + " >= ? AND " + TIME_EXPR + " < ? " + "GROUP BY e.project_id, COALESCE(e.version_id, 0), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0)";
        jdbcTemplate.update(sql, date, start, end);
    }

    private void insertSceneDaily(LocalDate date, Timestamp start, Timestamp end) {
        String sql = "INSERT INTO test_metric_scene_daily (metric_date, project_id, version_id, module_id, scene_id, scene_level, execution_engine, trigger_type, environment_id, " + "execution_count, pass_count, fail_count, skip_count, cancel_count, infra_fail_count, other_count, duration_total_ms, last_result, source_max_execution_id, aggregation_time) " + "SELECT ?, e.project_id, COALESCE(e.version_id, 0), COALESCE(e.module_id, 0), e.scene_id, COALESCE(NULLIF(e.scene_level, ''), 'UNSPECIFIED'), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0), " + sumTerminal("execution_count") + ", " + sumCategory("PASSED", "pass_count") + ", " + sumCategory("FAILED", "fail_count") + ", " + sumCategory("SKIPPED", "skip_count") + ", " + sumCategory("CANCELLED", "cancel_count") + ", " + sumCategory("INFRA_FAILED", "infra_fail_count") + ", " + sumCategory("OTHER", "other_count") + ", " + "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN COALESCE(e.duration_ms, 0) ELSE 0 END), 0), " + "SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN " + TERMINAL_EXPR + " THEN " + CATEGORY_EXPR + " END ORDER BY " + TIME_EXPR + " DESC SEPARATOR '||'), '||', 1), MAX(e.id), CURRENT_TIMESTAMP(3) " + "FROM automation_ui_execution e WHERE e.project_id IS NOT NULL AND " + TIME_EXPR + " >= ? AND " + TIME_EXPR + " < ? " + "GROUP BY e.project_id, COALESCE(e.version_id, 0), COALESCE(e.module_id, 0), e.scene_id, COALESCE(NULLIF(e.scene_level, ''), 'UNSPECIFIED'), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0)";
        jdbcTemplate.update(sql, date, start, end);
    }

    private void insertInventory(LocalDate date) {
        jdbcTemplate
            .update("INSERT INTO test_metric_inventory_daily (metric_date, project_id, version_id, module_id, scene_level, eligible_scene_count, aggregation_time) " + "SELECT ?, s.project_id, COALESCE(s.version_id, 0), COALESCE(s.module_id, 0), COALESCE(NULLIF(s.level, ''), 'UNSPECIFIED'), COUNT(*), CURRENT_TIMESTAMP(3) " + "FROM automation_ui_scene s WHERE s.status = 1 AND s.del_flag = 3 GROUP BY s.project_id, COALESCE(s.version_id, 0), COALESCE(s.module_id, 0), COALESCE(NULLIF(s.level, ''), 'UNSPECIFIED')", date);
    }

    private void insertAggregationState(LocalDate date, Timestamp start, Timestamp end) {
        String sql = "INSERT INTO test_metric_aggregation_state (metric_date, project_id, version_id, source_max_execution_id, source_execution_count, aggregated_execution_count, status, error_message, aggregation_time) " + "SELECT ?, src.project_id, src.version_id, src.source_max_execution_id, src.source_execution_count, COALESCE(agg.aggregated_execution_count, 0), " + "CASE WHEN src.source_execution_count = COALESCE(agg.aggregated_execution_count, 0) AND COALESCE(agg.aggregated_execution_count, 0) = COALESCE(agg.category_execution_count, 0) THEN 'SUCCESS' ELSE 'FAILED' END, " + "CASE WHEN src.source_execution_count = COALESCE(agg.aggregated_execution_count, 0) AND COALESCE(agg.aggregated_execution_count, 0) = COALESCE(agg.category_execution_count, 0) THEN NULL ELSE CONCAT('source=', src.source_execution_count, ', aggregate=', COALESCE(agg.aggregated_execution_count, 0), ', categories=', COALESCE(agg.category_execution_count, 0)) END, CURRENT_TIMESTAMP(3) " + "FROM (SELECT e.project_id, COALESCE(e.version_id, 0) version_id, MAX(e.id) source_max_execution_id, COUNT(*) source_execution_count FROM automation_ui_execution e " + "WHERE e.project_id IS NOT NULL AND " + TERMINAL_EXPR + " AND " + TIME_EXPR + " >= ? AND " + TIME_EXPR + " < ? GROUP BY e.project_id, COALESCE(e.version_id, 0)) src " + "LEFT JOIN (SELECT project_id, version_id, SUM(scene_execution_count) aggregated_execution_count, SUM(scene_pass_count + scene_fail_count + scene_skip_count + scene_cancel_count + scene_infra_fail_count + scene_other_count) category_execution_count " + "FROM test_metric_daily WHERE metric_date = ? GROUP BY project_id, version_id) agg ON agg.project_id = src.project_id AND agg.version_id = src.version_id";
        jdbcTemplate.update(sql, date, start, end, date);
    }
}
