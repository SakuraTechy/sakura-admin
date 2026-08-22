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

package top.continew.admin.automation.service.impl;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.service.AutomationUiRecordSourceMigrationService;

/** record_source 加法迁移实现；不负责提前执行 NOT NULL 或 CHECK 收口。 */
@Service
@RequiredArgsConstructor
public class AutomationUiRecordSourceMigrationServiceImpl implements AutomationUiRecordSourceMigrationService {

    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_SAMPLE_SIZE = 200;
    private static final String NORMALIZED_RECORD_TYPE = "LOWER(TRIM(record_type))";
    private static final String NORMALIZED_TRIGGER_TYPE = "LOWER(TRIM(trigger_type))";
    private static final String EXPECTED_SOURCE = "CASE WHEN " + NORMALIZED_RECORD_TYPE + " IN ('internal-interactive-context'," + " 'interactive-execution-context') THEN 'internal'" + " WHEN test_plan_id IS NOT NULL OR test_report_id IS NOT NULL" + " OR " + NORMALIZED_TRIGGER_TYPE + " IN ('test-plan','schedule') THEN 'test' ELSE 'debug' END";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public AuditResult audit(int sampleLimit) {
        int boundedLimit = Math.max(1, Math.min(sampleLimit, MAX_SAMPLE_SIZE));
        List<Long> counts = jdbcTemplate
            .query("SELECT" + " SUM(CASE WHEN " + NORMALIZED_RECORD_TYPE + " IN ('internal-interactive-context','interactive-execution-context')" + " AND (test_plan_id IS NOT NULL OR test_report_id IS NOT NULL) THEN 1 ELSE 0 END)," + " SUM(CASE WHEN " + NORMALIZED_TRIGGER_TYPE + " IN ('test-plan','schedule')" + " AND test_plan_id IS NULL AND test_report_id IS NULL THEN 1 ELSE 0 END)," + " SUM(CASE WHEN (trigger_type IS NULL OR " + NORMALIZED_TRIGGER_TYPE + " NOT IN ('test-plan','schedule'))" + " AND test_report_id IS NOT NULL THEN 1 ELSE 0 END)" + " FROM automation_ui_execution", (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            rowNum) -> List
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                .of(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    .getLong(1), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        .getLong(2), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            .getLong(3)))
            .stream()
            .findFirst()
            .orElse(List.of(0L, 0L, 0L));
        Long splitCount = jdbcTemplate
            .queryForObject("SELECT COUNT(*) FROM automation_ui_execution e" + " JOIN (SELECT " + NORMALIZED_RECORD_TYPE + " AS normalized_record_type FROM automation_ui_execution" + " WHERE record_type IS NULL OR " + NORMALIZED_RECORD_TYPE + " NOT IN ('internal-interactive-context','interactive-execution-context')" + " GROUP BY " + NORMALIZED_RECORD_TYPE + " HAVING COUNT(DISTINCT CASE" + " WHEN test_plan_id IS NOT NULL OR test_report_id IS NOT NULL" + " OR " + NORMALIZED_TRIGGER_TYPE + " IN ('test-plan','schedule') THEN 'test' ELSE 'debug' END) > 1) split" + " ON split.normalized_record_type <=> LOWER(TRIM(e.record_type))", Long.class);
        List<ConflictSample> samples = jdbcTemplate
            .query("SELECT execution_id, conflict_type FROM (" + " SELECT id AS execution_id, 'internal_with_plan_or_report' AS conflict_type" + " FROM automation_ui_execution WHERE " + NORMALIZED_RECORD_TYPE + " IN ('internal-interactive-context','interactive-execution-context')" + " AND (test_plan_id IS NOT NULL OR test_report_id IS NOT NULL)" + " UNION ALL SELECT id, 'plan_trigger_without_plan_or_report' FROM automation_ui_execution" + " WHERE " + NORMALIZED_TRIGGER_TYPE + " IN ('test-plan','schedule') AND test_plan_id IS NULL AND test_report_id IS NULL" + " UNION ALL SELECT id, 'non_plan_trigger_with_report' FROM automation_ui_execution" + " WHERE (trigger_type IS NULL OR " + NORMALIZED_TRIGGER_TYPE + " NOT IN ('test-plan','schedule')) AND test_report_id IS NOT NULL" + " UNION ALL SELECT e.id, 'split_legacy_record_type' FROM automation_ui_execution e" + " JOIN (SELECT " + NORMALIZED_RECORD_TYPE + " AS normalized_record_type FROM automation_ui_execution" + " WHERE record_type IS NULL OR " + NORMALIZED_RECORD_TYPE + " NOT IN ('internal-interactive-context','interactive-execution-context')" + " GROUP BY " + NORMALIZED_RECORD_TYPE + " HAVING COUNT(DISTINCT CASE" + " WHEN test_plan_id IS NOT NULL OR test_report_id IS NOT NULL" + " OR " + NORMALIZED_TRIGGER_TYPE + " IN ('test-plan','schedule') THEN 'test' ELSE 'debug' END) > 1) split" + " ON split.normalized_record_type <=> LOWER(TRIM(e.record_type))) conflicts" + " ORDER BY execution_id LIMIT ?", (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               rowNum) -> new ConflictSample(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   .getLong("execution_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       .getString("conflict_type")), boundedLimit);
        return new AuditResult(counts.get(0), counts.get(1), counts.get(2), splitCount == null ? 0 : splitCount, List
            .copyOf(samples));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BackfillResult backfillBatch(long afterId, int batchSize) {
        int boundedBatchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<Long> executionIds = jdbcTemplate
            .query("SELECT id FROM automation_ui_execution" + " WHERE id > ? AND record_source IS NULL ORDER BY id LIMIT ?", (rs,
                                                                                                                              rowNum) -> rs
                                                                                                                                  .getLong(1), Math
                                                                                                                                      .max(0, afterId), boundedBatchSize);
        if (executionIds.isEmpty()) {
            return new BackfillResult(Math.max(0, afterId), 0, 0);
        }
        String placeholders = String.join(",", executionIds.stream().map(id -> "?").toList());
        List<Object> updateArguments = new ArrayList<>(executionIds);
        int updated = jdbcTemplate
            .update("UPDATE automation_ui_execution SET record_source = " + EXPECTED_SOURCE + ", update_time = update_time WHERE record_source IS NULL AND id IN (" + placeholders + ")", updateArguments
                .toArray());
        return new BackfillResult(executionIds.get(executionIds.size() - 1), executionIds.size(), updated);
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationResult verify() {
        return jdbcTemplate
            .query("SELECT" + " SUM(CASE WHEN record_source IS NULL THEN 1 ELSE 0 END)," + " SUM(CASE WHEN record_source IS NOT NULL AND record_source NOT IN ('debug','test','internal') THEN 1 ELSE 0 END)," + " SUM(CASE WHEN record_source IS NOT NULL AND record_source <> " + EXPECTED_SOURCE + " THEN 1 ELSE 0 END) FROM automation_ui_execution", (rs,
                                                                                                                                                                                                                                                                                                                                                           rowNum) -> new VerificationResult(rs
                                                                                                                                                                                                                                                                                                                                                               .getLong(1), rs
                                                                                                                                                                                                                                                                                                                                                                   .getLong(2), rs
                                                                                                                                                                                                                                                                                                                                                                       .getLong(3)))
            .stream()
            .findFirst()
            .orElse(new VerificationResult(0, 0, 0));
    }
}
