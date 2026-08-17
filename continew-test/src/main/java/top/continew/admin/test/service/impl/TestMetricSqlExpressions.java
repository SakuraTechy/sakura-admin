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

/**
 * Shared SQL expressions for test metric classification.
 *
 * Keeping the category expression in one place prevents the online API and
 * daily aggregation from drifting into different result totals.
 */
final class TestMetricSqlExpressions {

    private TestMetricSqlExpressions() {
    }

    static final String TIME_EXPR = "e.metric_time";
    static final String PASS_EXPR = "LOWER(COALESCE(e.result, '')) IN ('passed', '14', '全部通过')";
    static final String SKIP_EXPR = "LOWER(COALESCE(e.result, '')) IN ('skipped', '16', '跳过')";
    static final String CANCEL_EXPR = "(LOWER(COALESCE(e.result, '')) IN ('cancelled', 'canceled', '17') " + "OR LOWER(COALESCE(e.status, '')) IN ('cancelled', 'canceled'))";
    static final String INFRA_EXPR = "(LOWER(COALESCE(e.status, '')) IN ('blocked', 'interrupted') " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'infra%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'executor%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'browser%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'environment%' " + "OR LOWER(COALESCE(e.error_code, '')) LIKE 'network%')";
    private static final String FUNCTION_FAIL_EXPR = "LOWER(COALESCE(e.result, '')) IN ('failed', '15', '不通过')";
    static final String FAIL_EXPR = "(" + FUNCTION_FAIL_EXPR + " AND NOT " + INFRA_EXPR + ")";

    /**
     * A terminal result has one and only one category. Cancellation wins over
     * infrastructure diagnostics because it is an explicit user outcome;
     * infrastructure failure wins over result text, then functional result.
     */
    static final String CATEGORY_EXPR = "CASE WHEN " + CANCEL_EXPR + " THEN 'CANCELLED' " + "WHEN " + INFRA_EXPR + " THEN 'INFRA_FAILED' " + "WHEN " + PASS_EXPR + " THEN 'PASSED' " + "WHEN " + FAIL_EXPR + " THEN 'FAILED' " + "WHEN " + SKIP_EXPR + " THEN 'SKIPPED' ELSE 'OTHER' END";

    static final String TERMINAL_EXPR = "(LOWER(COALESCE(e.status, '')) IN " + "('completed', 'passed', 'failed', 'cancelled', 'canceled', 'interrupted', 'blocked', 'skipped') " + "OR " + CATEGORY_EXPR + " <> 'OTHER')";

    static final String ENGINE_EXPR = "CASE WHEN LOWER(REPLACE(COALESCE(e.execution_engine, ''), '_', '-')) " + "IN ('playwright', 'runner', 'playwright-runner') THEN 'playwright-runner' " + "WHEN LOWER(REPLACE(COALESCE(e.execution_engine, ''), '_', '-')) " + "IN ('chrome-devtools-protocol', 'cdp', 'extension-cdp') THEN 'extension-cdp' " + "ELSE COALESCE(NULLIF(LOWER(REPLACE(e.execution_engine, '_', '-')), ''), 'unknown') END";
    static final String TRIGGER_EXPR = "COALESCE(NULLIF(LOWER(REPLACE(e.trigger_type, '_', '-')), ''), 'unknown')";

    static String sumCategory(String category, String alias) {
        return "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " AND " + CATEGORY_EXPR + " = '" + category + "' THEN 1 ELSE 0 END), 0) " + alias;
    }

    static String sumTerminal(String alias) {
        return "COALESCE(SUM(CASE WHEN " + TERMINAL_EXPR + " THEN 1 ELSE 0 END), 0) " + alias;
    }
}
