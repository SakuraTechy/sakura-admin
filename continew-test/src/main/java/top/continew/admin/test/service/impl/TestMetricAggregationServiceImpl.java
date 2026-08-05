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
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.test.service.TestMetricAggregationService;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 测试度量日汇总。每次按天全量替换，天然幂等并可修复迟到回调。
 */
@Service
@RequiredArgsConstructor
public class TestMetricAggregationServiceImpl implements TestMetricAggregationService {

    private static final String TIME_EXPR = "COALESCE(e.finished_at, e.started_at, e.create_time)";
    private static final String PASS_EXPR = "LOWER(COALESCE(e.result, '')) IN ('passed', '14', '全部通过')";
    private static final String SKIP_EXPR = "LOWER(COALESCE(e.result, '')) IN ('skipped', '16', '跳过')";
    private static final String CANCEL_EXPR = "(LOWER(COALESCE(e.result, '')) IN ('cancelled', 'canceled', '17') " + "OR LOWER(COALESCE(e.status, '')) IN ('cancelled', 'canceled'))";
    private static final String INFRA_EXPR = "(LOWER(COALESCE(e.status, '')) IN ('blocked', 'interrupted') " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'infra%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'executor%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'browser%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'environment%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'network%')";
    private static final String FAIL_EXPR = "(LOWER(COALESCE(e.result, '')) IN ('failed', '15', '不通过') AND NOT " + INFRA_EXPR + ")";
    private static final String TERMINAL_EXPR = "(LOWER(COALESCE(e.status, '')) IN " + "('completed', 'passed', 'failed', 'cancelled', 'canceled', 'interrupted', 'blocked', 'skipped') " + "OR " + PASS_EXPR + " OR " + FAIL_EXPR + " OR " + SKIP_EXPR + " OR " + CANCEL_EXPR + " OR " + INFRA_EXPR + ")";
    private static final String ENGINE_EXPR = "CASE WHEN LOWER(REPLACE(COALESCE(e.execution_engine, ''), '_', '-')) IN ('playwright', 'runner', 'playwright-runner') THEN 'playwright-runner' " + "WHEN LOWER(REPLACE(COALESCE(e.execution_engine, ''), '_', '-')) IN ('chrome-devtools-protocol', 'cdp', 'extension-cdp') THEN 'extension-cdp' " + "ELSE COALESCE(NULLIF(LOWER(REPLACE(e.execution_engine, '_', '-')), ''), 'unknown') END";
    private static final String TRIGGER_EXPR = "COALESCE(NULLIF(LOWER(REPLACE(e.trigger_type, '_', '-')), ''), 'unknown')";

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
            .update("UPDATE automation_ui_execution e JOIN automation_ui_scene s ON s.id = e.scene_id SET " + "e.dimension_quality = CASE WHEN e.project_id IS NULL OR e.version_id IS NULL OR e.module_id IS NULL " + "OR e.scene_level IS NULL OR e.run_key IS NULL OR e.run_key = '' THEN 'INFERRED' ELSE e.dimension_quality END, " + "e.project_id = COALESCE(e.project_id, s.project_id), e.version_id = COALESCE(e.version_id, s.version_id), " + "e.module_id = COALESCE(e.module_id, s.module_id), e.scene_level = COALESCE(e.scene_level, s.level), " + "e.run_key = COALESCE(NULLIF(e.run_key, ''), CASE WHEN e.test_report_id IS NOT NULL THEN CONCAT('REPORT:', e.test_report_id) " + "WHEN e.batch_id IS NOT NULL THEN CONCAT(" + ENGINE_EXPR + ", ':BATCH:', e.batch_id) ELSE CONCAT('EXECUTION:', e.id) END) " + "WHERE e.project_id IS NULL OR e.version_id IS NULL OR e.module_id IS NULL OR e.scene_level IS NULL OR e.run_key IS NULL OR e.run_key = ''");
        jdbcTemplate
            .update("UPDATE automation_ui_execution e SET e.execution_engine = " + ENGINE_EXPR + ", e.trigger_type = " + TRIGGER_EXPR + " " + "WHERE e.execution_engine IS NULL OR e.execution_engine = '' OR e.execution_engine <> " + ENGINE_EXPR + " " + "OR e.trigger_type IS NULL OR e.trigger_type = '' OR e.trigger_type <> " + TRIGGER_EXPR);
    }

    private void insertDaily(LocalDate date, Timestamp start, Timestamp end) {
        String sql = "INSERT INTO test_metric_daily (metric_date, project_id, version_id, execution_engine, trigger_type, environment_id, " + "run_started_count, run_completed_count, scene_execution_count, scene_pass_count, scene_fail_count, scene_skip_count, " + "scene_cancel_count, scene_infra_fail_count, case_total, case_pass, case_fail, case_skip, step_total, step_pass, step_fail, step_skip, " + "duration_total_ms, duration_sample_count, histogram_version, source_max_execution_id, aggregation_time) SELECT ?, e.project_id, " + "COALESCE(e.version_id, 0), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", " + "COALESCE(e.project_environment_id, 0), COUNT(DISTINCT COALESCE(NULLIF(e.run_key, ''), e.execution_key)), " + "COUNT(DISTINCT CASE WHEN " + TERMINAL_EXPR + " THEN COALESCE(NULLIF(e.run_key, ''), e.execution_key) END), " + sum(TERMINAL_EXPR) + ", " + sum(PASS_EXPR) + ", " + sum(FAIL_EXPR) + ", " + sum(SKIP_EXPR) + ", " + sum(CANCEL_EXPR) + ", " + sum(INFRA_EXPR) + ", SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_total ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_pass ELSE 0 END), SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_fail ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.case_skip ELSE 0 END), SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_total ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_pass ELSE 0 END), SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_fail ELSE 0 END), " + "SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.step_skip ELSE 0 END), COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN e.duration_ms ELSE 0 END), 0), COUNT(CASE WHEN " + TERMINAL_EXPR + " AND e.duration_ms IS NOT NULL THEN 1 END), 1, MAX(e.id), CURRENT_TIMESTAMP(3) " + "FROM automation_ui_execution e WHERE e.project_id IS NOT NULL AND " + TIME_EXPR + " >= ? AND " + TIME_EXPR + " < ? " + "GROUP BY e.project_id, COALESCE(e.version_id, 0), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0)";
        jdbcTemplate.update(sql, date, start, end);
    }

    private void insertSceneDaily(LocalDate date, Timestamp start, Timestamp end) {
        String sql = "INSERT INTO test_metric_scene_daily (metric_date, project_id, version_id, module_id, scene_id, scene_level, execution_engine, trigger_type, environment_id, " + "execution_count, pass_count, fail_count, skip_count, cancel_count, infra_fail_count, duration_total_ms, last_result, source_max_execution_id, aggregation_time) " + "SELECT ?, e.project_id, COALESCE(e.version_id, 0), COALESCE(e.module_id, 0), e.scene_id, COALESCE(NULLIF(e.scene_level, ''), 'UNSPECIFIED'), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0), " + sum(TERMINAL_EXPR) + ", " + sum(PASS_EXPR) + ", " + sum(FAIL_EXPR) + ", " + sum(SKIP_EXPR) + ", " + sum(CANCEL_EXPR) + ", " + sum(INFRA_EXPR) + ", COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN COALESCE(e.duration_ms, 0) ELSE 0 END), 0), " + "SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN " + TERMINAL_EXPR + " THEN e.result END ORDER BY " + TIME_EXPR + " DESC SEPARATOR '||'), '||', 1), MAX(e.id), CURRENT_TIMESTAMP(3) " + "FROM automation_ui_execution e WHERE e.project_id IS NOT NULL AND " + TIME_EXPR + " >= ? AND " + TIME_EXPR + " < ? " + "GROUP BY e.project_id, COALESCE(e.version_id, 0), COALESCE(e.module_id, 0), e.scene_id, COALESCE(NULLIF(e.scene_level, ''), 'UNSPECIFIED'), " + ENGINE_EXPR + ", " + TRIGGER_EXPR + ", COALESCE(e.project_environment_id, 0)";
        jdbcTemplate.update(sql, date, start, end);
    }

    private void insertInventory(LocalDate date) {
        jdbcTemplate
            .update("INSERT INTO test_metric_inventory_daily (metric_date, project_id, version_id, module_id, scene_level, eligible_scene_count, aggregation_time) " + "SELECT ?, s.project_id, COALESCE(s.version_id, 0), COALESCE(s.module_id, 0), COALESCE(NULLIF(s.level, ''), 'UNSPECIFIED'), COUNT(*), CURRENT_TIMESTAMP(3) " + "FROM automation_ui_scene s WHERE s.status = 1 AND s.del_flag = 3 GROUP BY s.project_id, COALESCE(s.version_id, 0), COALESCE(s.module_id, 0), COALESCE(NULLIF(s.level, ''), 'UNSPECIFIED')", date);
    }

    private void insertAggregationState(LocalDate date, Timestamp start, Timestamp end) {
        jdbcTemplate
            .update("INSERT INTO test_metric_aggregation_state (metric_date, project_id, version_id, source_max_execution_id, source_execution_count, aggregated_execution_count, status, aggregation_time) " + "SELECT ?, e.project_id, COALESCE(e.version_id, 0), MAX(e.id), COUNT(*), COUNT(*), 'SUCCESS', CURRENT_TIMESTAMP(3) FROM automation_ui_execution e " + "WHERE e.project_id IS NOT NULL AND " + TIME_EXPR + " >= ? AND " + TIME_EXPR + " < ? GROUP BY e.project_id, COALESCE(e.version_id, 0)", date, start, end);
    }

    private String sum(String condition) {
        return "COALESCE(SUM(CASE WHEN " + condition + " THEN 1 ELSE 0 END), 0)";
    }
}
