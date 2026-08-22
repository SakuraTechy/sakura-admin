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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.continew.admin.automation.service.AutomationUiRecordSourceMigrationService;

class AutomationUiRecordSourceMigrationServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void auditShouldIncludeNullTriggerInNonPlanReportConflicts() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doReturn(List.of(List.of(0L, 0L, 0L))).when(jdbcTemplate).query(anyString(), any(RowMapper.class));
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(0L);
        doReturn(List.of()).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
        AutomationUiRecordSourceMigrationService service = new AutomationUiRecordSourceMigrationServiceImpl(jdbcTemplate);

        service.audit(50);

        List<String> sqlStatements = mockingDetails(jdbcTemplate).getInvocations()
            .stream()
            .map(invocation -> invocation.getArgument(0, String.class))
            .toList();
        assertThat(sqlStatements).allMatch(sql -> !sql.contains("trigger_type NOT IN ('test-plan','schedule')"))
            .anyMatch(sql -> sql
                .contains("(trigger_type IS NULL OR LOWER(TRIM(trigger_type)) NOT IN ('test-plan','schedule'))"))
            .anyMatch(sql -> sql.contains("normalized_record_type <=> LOWER(TRIM(e.record_type))"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void backfillShouldUseBoundedNullOnlyIdBatchAndFixedClassifier() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(11L, 12L));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(2);
        AutomationUiRecordSourceMigrationService service = new AutomationUiRecordSourceMigrationServiceImpl(jdbcTemplate);

        AutomationUiRecordSourceMigrationService.BackfillResult result = service.backfillBatch(10, 5000);

        assertThat(result).isEqualTo(new AutomationUiRecordSourceMigrationService.BackfillResult(12L, 2, 2));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue())
            .contains("record_source IS NULL", "LOWER(TRIM(record_type)) IN ('internal-interactive-context', 'interactive-execution-context')", "LOWER(TRIM(trigger_type)) IN ('test-plan','schedule')", "id IN (?,?)")
            .doesNotContain("build_number");
        assertThat(arguments.getValue()).containsExactly(11L, 12L);
    }
}
