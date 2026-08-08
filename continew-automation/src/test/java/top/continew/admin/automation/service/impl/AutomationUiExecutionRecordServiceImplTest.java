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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.InvocationTargetException;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;

class AutomationUiExecutionRecordServiceImplTest {

    @Test
    void shouldRemoveOnlyTypedOperationWhenDiagnosticFlagIsDisabled() {
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(mock(JdbcTemplate.class), mock(IdentifierGenerator.class), new ObjectMapper(), null);
        ProjectConfigMapper projectConfigMapper = mock(ProjectConfigMapper.class);
        ProjectConfigDO project = new ProjectConfigDO();
        project.setOperationDiagnosticV1(false);
        when(projectConfigMapper.selectById(9L)).thenReturn(project);
        ReflectionTestUtils.setField(service, "projectConfigMapper", projectConfigMapper);

        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setProjectId(9L);
        Boolean enabled = ReflectionTestUtils.invokeMethod(service, "isOperationDiagnosticEnabled", scene);
        assertThat(enabled).isFalse();

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("profile", "variable");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", operation);
        details.put("variable", Map.of("name", "today"));
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("action_type", "global_variable_date");
        step.put("details", details);

        @SuppressWarnings("unchecked") Map<String, Object> persisted = ReflectionTestUtils
            .invokeMethod(service, "withoutTypedOperation", step);
        @SuppressWarnings("unchecked") Map<String, Object> persistedDetails = (Map<String, Object>)persisted
            .get("details");
        @SuppressWarnings("unchecked") Map<String, Object> originalDetails = (Map<String, Object>)step.get("details");
        assertThat(persistedDetails).doesNotContainKey("operation").containsKey("variable");
        assertThat(originalDetails).containsKey("operation");
    }

    @Test
    void shouldDefaultDiagnosticFlagToEnabledForMissingProjectConfig() {
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(mock(JdbcTemplate.class), mock(IdentifierGenerator.class), new ObjectMapper(), null);
        ProjectConfigMapper projectConfigMapper = mock(ProjectConfigMapper.class);
        when(projectConfigMapper.selectById(10L)).thenReturn(null);
        ReflectionTestUtils.setField(service, "projectConfigMapper", projectConfigMapper);

        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setProjectId(10L);
        Boolean enabled = ReflectionTestUtils.invokeMethod(service, "isOperationDiagnosticEnabled", scene);
        assertThat(enabled).isTrue();
    }

    @Test
    void shouldFindPreparedJenkinsExecutionByBuildNumber() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);

        assertThat(service.findBatch(1L, "42")).isNull();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getAllValues().get(0)).contains("batch_id = ?");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("build_number = ?");
    }

    @Test
    void shouldExcludeInternalInteractiveContextsFromExecutionHistory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);

        assertThat(service.listRecords(1L, false, 100)).isEmpty();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("record_type <> ?");
        assertThat(argsCaptor.getValue()).containsExactly(1L, "interactive-execution-context");
    }

    @Test
    void shouldCommitExecutionAndDefinitionRevisionBeforeExternalExecutionStarts() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(identifierGenerator.nextId(any())).thenReturn(7L, 8L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SCENE_001");
        scene.setDefinitionVersion(3L);
        scene.setCaseList(List.of());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("batchId", "JENKINS_BATCH_001");
        record.put("executionType", "jenkins");
        record.put("executeStatus", "queued");

        service.saveExternalExecutionRecord(scene, record);

        Transactional transactional = AutomationUiExecutionRecordServiceImpl.class
            .getMethod("saveExternalExecutionRecord", AutomationUiSceneDO.class, Map.class)
            .getAnnotation(Transactional.class);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        boolean revisionInserted = false;
        boolean executionInserted = false;
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            String sql = sqlCaptor.getAllValues().get(i);
            Object[] args = argsCaptor.getAllValues().get(i);
            if (sql.startsWith("INSERT INTO automation_ui_scene_definition_revision")) {
                revisionInserted = true;
                assertThat(args[0]).isEqualTo(7L);
                assertThat(args[1]).isEqualTo(1L);
                assertThat(args[2]).isEqualTo(3L);
            }
            if (sql.startsWith("INSERT INTO automation_ui_execution")) {
                executionInserted = true;
                assertThat(args[0]).isEqualTo(8L);
                assertThat(args[4]).isEqualTo(7L);
                assertThat(args[7]).isEqualTo("JENKINS_BATCH_001");
                assertThat(args[13]).isEqualTo(true);
            }
        }
        assertThat(revisionInserted).isTrue();
        assertThat(executionInserted).isTrue();
    }

    @Test
    void shouldReadFrozenDiagnosticFlagFromExecutionInsteadOfProjectConfig() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate
            .query(eq("SELECT operation_diagnostic_v1 FROM automation_ui_execution WHERE id = ? LIMIT 1"), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(false));
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, mock(IdentifierGenerator.class), new ObjectMapper(), null);

        Boolean enabled = ReflectionTestUtils.invokeMethod(service, "findOperationDiagnosticEnabled", 100L);

        assertThat(enabled).isFalse();
        verify(jdbcTemplate)
            .query(eq("SELECT operation_diagnostic_v1 FROM automation_ui_execution WHERE id = ? LIMIT 1"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void shouldNotPublishInteractiveContextAsLatestSceneExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(identifierGenerator.nextId(any())).thenReturn(7L, 8L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SCENE_001");
        scene.setDefinitionVersion(3L);
        scene.setCaseList(List.of());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordType", "interactive-execution-context");
        record.put("batchId", "interactive-1-example");
        record.put("executeStatus", "running");

        service.saveRecord(scene, record, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues()).noneMatch(sql -> sql.contains("automation_ui_scene_execution_state"));
    }

    @Test
    void shouldKeepBoundRevisionWhenExistingExecutionReceivesLaterResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM automation_ui_execution WHERE execution_key")) {
                return List.of(100L);
            }
            if (sql.contains("SELECT definition_revision_id FROM automation_ui_execution")) {
                return List.of(7L);
            }
            return List.of();
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SCENE_001");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("batchId", "BATCH_001");
        record.put("executionType", "playwright-runner");
        record.put("executeStatus", "completed");
        record.put("executeResult", "passed");

        service.saveRecord(scene, record, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        boolean updatedWithFrozenRevision = false;
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            if (sqlCaptor.getAllValues().get(i).startsWith("UPDATE automation_ui_execution SET")) {
                updatedWithFrozenRevision = true;
                assertThat(argsCaptor.getAllValues().get(i)[0]).isEqualTo(7L);
            }
        }
        assertThat(updatedWithFrozenRevision).isTrue();
        verify(jdbcTemplate, never())
            .update(eq("INSERT INTO automation_ui_scene_definition_revision"), any(Object[].class));
    }

    @Test
    void shouldEnrichOperationLabelsFromTheFrozenCatalogBeforePersisting() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        AutomationOperationCatalogService catalogService = mock(AutomationOperationCatalogService.class);
        AutomationOperationCatalog.OperationMethod method = new AutomationOperationCatalog.OperationMethod();
        method.setMethodCode("global.variable.date");
        method.setMethodVersion(1);
        method.setLabel("获取日期设置值到全局");
        method.setActionType("global_variable_date");
        method.setDiagnosticProfile("variable");
        method.setFormSchema(List.of(Map.of("name", "variable_name", "label", "变量名")));
        when(catalogService.findOperation("global.variable.date")).thenReturn(Optional
            .of(new AutomationOperationCatalogService.OperationDescriptor("global", "全局变量操作", method)));

        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), catalogService);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("schema_version", 1);
        operation.put("profile", "variable");
        operation.put("executor", "playwright");
        operation.put("method", Map.of("method_code", "global.variable.date", "action_type", "global_variable_date"));
        operation.put("inputs", List.of(Map.of("key", "variable_name", "source", Map
            .of("code", "variable_reference", "label", "引用变量：run.date", "secret", "must-drop"), "effective", Map
                .of("value_state", "visible", "preview", "run.date"))));
        operation.put("outcome", Map.of("kind", "variable", "summary", "日期变量设置完成", "facts", List.of()));

        var sanitizer = AutomationUiExecutionRecordServiceImpl.class.getDeclaredMethod("sanitizeOperation", Map.class);
        sanitizer.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, Object> sanitized = (Map<String, Object>)sanitizer
            .invoke(service, operation);
        Map<String, Object> methodResult = castMap(sanitized.get("method"));
        Map<String, Object> inputResult = castMap(((List<?>)sanitized.get("inputs")).get(0));

        assertThat(methodResult).containsEntry("type_label", "全局变量操作").containsEntry("method_label", "获取日期设置值到全局");
        assertThat(inputResult).containsEntry("label", "变量名");
        assertThat(castMap(inputResult.get("source"))).containsEntry("code", "variable_reference")
            .containsEntry("label", "引用变量：run.date")
            .doesNotContainKey("secret");
    }

    @Test
    void shouldKeepUnknownGenericOperationDetailsWithoutInventingCatalogIdentity() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("schema_version", 1);
        operation.put("profile", "generic");
        operation.put("executor", "playwright");
        operation.put("method", Map.of("action_type", "pw-custom"));
        operation.put("inputs", List.of());
        operation.put("outcome", Map.of("kind", "generic", "summary", "未知动作", "facts", List.of()));

        var sanitizer = AutomationUiExecutionRecordServiceImpl.class.getDeclaredMethod("sanitizeOperation", Map.class);
        sanitizer.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, Object> sanitized = (Map<String, Object>)sanitizer
            .invoke(service, operation);
        assertThat(castMap(sanitized.get("method"))).containsEntry("action_type", "pw-custom");
    }

    @Test
    void shouldRejectUnknownProfileBeforePersistingOperationDetails() throws Exception {
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(mock(JdbcTemplate.class), mock(IdentifierGenerator.class), new ObjectMapper(), null);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("schema_version", 1);
        operation.put("profile", "unsupported-profile");
        operation.put("executor", "playwright");
        operation.put("method", Map.of());
        operation.put("outcome", Map.of("kind", "generic"));

        var sanitizer = AutomationUiExecutionRecordServiceImpl.class.getDeclaredMethod("sanitizeOperation", Map.class);
        sanitizer.setAccessible(true);
        try {
            sanitizer.invoke(service, operation);
            throw new AssertionError("expected unsupported profile rejection");
        } catch (InvocationTargetException error) {
            assertThat(error.getCause()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("operation profile 不受支持");
        }
    }

    @Test
    void shouldMaskSensitiveOperationDisplayValues() throws Exception {
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(mock(JdbcTemplate.class), mock(IdentifierGenerator.class), new ObjectMapper(), null);
        var sanitizer = AutomationUiExecutionRecordServiceImpl.class
            .getDeclaredMethod("sanitizeOperationDisplay", String.class, Object.class);
        sanitizer.setAccessible(true);

        @SuppressWarnings("unchecked") Map<String, Object> sanitized = (Map<String, Object>)sanitizer
            .invoke(service, "password", Map.of("value_state", "visible", "preview", "must-not-be-retained"));

        assertThat(sanitized).containsEntry("value_state", "masked").doesNotContainKey("preview");
    }

    @Test
    void shouldBoundStepDiagnosticsAndMarkOverflow() throws Exception {
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(mock(JdbcTemplate.class), mock(IdentifierGenerator.class), new ObjectMapper(), null);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("step_id", "STEP_001");
        source.put("value", "x".repeat(70_000));

        var bounded = AutomationUiExecutionRecordServiceImpl.class
            .getDeclaredMethod("boundedSummary", Map.class, int.class, String.class);
        bounded.setAccessible(true);
        String result = (String)bounded.invoke(service, source, 64 * 1024, "step_diagnostics");

        assertThat(result).contains("\"_truncated\":true").doesNotContain("x".repeat(100));
    }

    @Test
    void shouldRejectTypedOperationThatDiffersFromFrozenRevision() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), null);

        StepDO frozenStep = new StepDO();
        frozenStep.setId("STEP_001");
        frozenStep.setConfigList(List
            .of(config("method_code", "input.text"), config("method_version", "1"), config("action_type", "input")));
        CaseDO frozenCase = new CaseDO();
        frozenCase.setStepList(List.of(frozenStep));
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("method", Map.of("method_code", "click.element", "method_version", 1, "action_type", "click"));
        Map<String, Object> step = Map.of("step_id", "STEP_001", "details", Map.of("operation", operation));

        var validator = AutomationUiExecutionRecordServiceImpl.class
            .getDeclaredMethod("validateOperationAgainstFrozenRevision", Map.class, CaseDO.class, int.class);
        validator.setAccessible(true);
        try {
            validator.invoke(service, step, frozenCase, 0);
            throw new AssertionError("expected frozen revision mismatch");
        } catch (InvocationTargetException error) {
            assertThat(error.getCause()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("method_code 与冻结 definition revision 不一致");
        }
    }

    @Test
    void shouldAllowRunnerActionAdaptationWhenFrozenMethodIdentityMatches() throws Exception {
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(mock(JdbcTemplate.class), mock(IdentifierGenerator.class), new ObjectMapper(), null);
        StepDO variableStep = typedStep("STEP_001", "global.variable.set", "set_variable");
        StepDO assertionStep = typedStep("STEP_002", "assertion.element.match", "assert_text");
        CaseDO frozenCase = new CaseDO();
        frozenCase.setStepList(List.of(variableStep, assertionStep));

        var validator = AutomationUiExecutionRecordServiceImpl.class
            .getDeclaredMethod("validateOperationAgainstFrozenRevision", Map.class, CaseDO.class, int.class);
        validator.setAccessible(true);

        validator.invoke(service, typedResult("STEP_001", "global.variable.set", "global_variable_set"), frozenCase, 0);
        validator
            .invoke(service, typedResult("STEP_002", "assertion.element.match", "assert_element_match"), frozenCase, 1);
    }

    private StepDO typedStep(String stepId, String methodCode, String recordingActionType) {
        StepDO step = new StepDO();
        step.setId(stepId);
        step.setConfigList(List
            .of(config("method_code", methodCode), config("method_version", "1"), config("action_type", recordingActionType)));
        return step;
    }

    private Map<String, Object> typedResult(String stepId, String methodCode, String runnerActionType) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("method", Map
            .of("method_code", methodCode, "method_version", 1, "action_type", runnerActionType));
        return Map.of("step_id", stepId, "details", Map.of("operation", operation));
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>)value;
    }
}
