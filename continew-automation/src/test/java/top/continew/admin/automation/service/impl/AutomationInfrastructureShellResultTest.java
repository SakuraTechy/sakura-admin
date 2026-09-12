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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.continew.admin.automation.converter.AutomationInfrastructureRuntimeBindingResolver;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.mapper.AutomationInfrastructureTaskLogMapper;
import top.continew.admin.automation.mapper.AutomationInfrastructureTaskMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationInfrastructureTaskDO;
import top.continew.admin.automation.service.AutomationEnvironmentResourceService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.support.AutomationExecutionAgentClient;
import top.continew.admin.automation.support.AutomationInfrastructureResultSanitizer;
import top.continew.admin.automation.support.AutomationInfrastructureRiskPolicy;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;

class AutomationInfrastructureShellResultTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AutomationInfrastructureTaskMapper tasks = mock(AutomationInfrastructureTaskMapper.class);
    private final AutomationExecutionAgentClient agent = mock(AutomationExecutionAgentClient.class);
    private final AutomationPlaywrightStepExtractor extractor = mock(AutomationPlaywrightStepExtractor.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AutomationInfrastructureTaskDO task = new AutomationInfrastructureTaskDO();
    private final Map<String, Object> definition = new LinkedHashMap<>();
    private AutomationInfrastructureTaskServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AutomationInfrastructureTaskServiceImpl(tasks, mock(AutomationInfrastructureTaskLogMapper.class), mock(AutomationUiSceneMapper.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectServerConfigMapper.class), mock(ProjectDataBaseConfigMapper.class), extractor, new AutomationInfrastructureRuntimeBindingResolver(mapper), mapper, agent, mock(AutomationUiExecutionRecordService.class), jdbc, new AutomationInfrastructureResultSanitizer(mapper), new AutomationInfrastructureRiskPolicy(""), mock(AutomationEnvironmentResourceService.class));
        task.setTaskId("INFRA_SHELL");
        task.setActionType("server_command");
        task.setCaseKey("1:CASE_1");
        task.setStepId("STEP_1");
        task.setOwnerUserId(101L);
        task.setDefinitionRevisionId(88L);
        task.setStatus("running");
        definition.put("action_type", "server_command");
        definition.put("command", "printf 123");
        definition.put("variable_name", "disk");
        when(tasks.selectOne(any())).thenReturn(task);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List
            .of("[{\"id\":\"CASE_1\",\"stepList\":[{\"id\":\"STEP_1\"}]}]"));
        when(extractor.extract(any(), eq(0))).thenAnswer(invocation -> new LinkedHashMap<>(definition));
        UserContext owner = new UserContext();
        owner.setId(101L);
        UserContextHolder.setContext(owner, false);
    }

    @AfterEach
    void clearContext() {
        UserContextHolder.clearContext();
    }

    @Test
    void returnsOnlyDeclaredVariableWithoutTrimmingOrSanitizing() {
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map
            .of("disk", " token=test-value\n", "other", "hidden")));
        var result = service.get("INFRA_SHELL", null);
        assertThat(result.getStatus()).isEqualTo("passed");
        assertThat(result.getResult().get("variables")).isEqualTo(Map.of("disk", " token=test-value\n"));
        assertThat(task.getResultJson()).doesNotContain("token=test-value", "other", "hidden", "variables");
    }

    @Test
    void acceptsEmptyStringButRejectsMissingValueAndKeepsFailureOnRepeatedPoll() {
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map.of("disk", "")));
        assertThat(service.get("INFRA_SHELL", null).getResult().get("variables")).isEqualTo(Map.of("disk", ""));
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map.of("other", "bad")));
        assertThat(service.get("INFRA_SHELL", null).getErrorCode()).isEqualTo("SERVER_RESULT_VARIABLE_MISSING");
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map.of("disk", "new")));
        var repeated = service.get("INFRA_SHELL", null);
        assertThat(repeated.getStatus()).isEqualTo("failed");
        assertThat(repeated.getResult()).doesNotContainKey("variables");
        verify(agent, never()).submit(any());
    }

    @Test
    void nonzeroFailureAndCancellationNeverReturnVariables() {
        when(agent.get("INFRA_SHELL")).thenReturn(response("failed", 1, Map.of("disk", "bad")));
        assertThat(service.get("INFRA_SHELL", null).getResult()).doesNotContainKey("variables");
        task.setStatus("running");
        task.setCancelRequestedAt(LocalDateTime.now());
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map.of("disk", "bad")));
        var cancelled = service.get("INFRA_SHELL", null);
        assertThat(cancelled.getStatus()).isEqualTo("cancelled");
        assertThat(cancelled.getResult()).doesNotContainKey("variables");
    }

    @Test
    void legacyWithoutBindingIgnoresUnexpectedVariables() {
        definition.remove("variable_name");
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map.of("disk", "bad")));
        var result = service.get("INFRA_SHELL", null);
        assertThat(result.getStatus()).isEqualTo("passed");
        assertThat(result.getResult()).doesNotContainKey("variables");
    }

    @Test
    void tooLargeVariablesAreNotSilentlyTruncated() {
        when(agent.get("INFRA_SHELL")).thenReturn(response("passed", 0, Map.of("disk", "x".repeat(4097))));
        var result = service.get("INFRA_SHELL", null);
        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getErrorCode()).isEqualTo("SERVER_RESULT_TOO_LARGE");
        assertThat(result.getResult()).doesNotContainKey("variables");
    }

    @Test
    void maskedResultDoesNotPersistRawPreviewOrAllowArtifactDownload() {
        definition.put("value_masked", 1);
        var response = response("passed", 0, Map.of("disk", "masked-fixture"));
        response.put("result", Map.of("variables", Map.of("disk", "masked-fixture"), "infrastructure", Map
            .of("schemaVersion", 2, "kind", "SERVER_COMMAND", "results", List
                .of(), "stdout", "masked-fixture", "stderr", "masked-fixture", "artifact", Map.of("available", true))));
        when(agent.get("INFRA_SHELL")).thenReturn(response);
        var result = service.get("INFRA_SHELL", null);
        assertThat(result.getResult().get("variables")).isEqualTo(Map.of("disk", "masked-fixture"));
        assertThat(task.getResultJson()).doesNotContain("masked-fixture");
        assertThatThrownBy(() -> service.downloadArtifact("INFRA_SHELL", null))
            .hasMessageContaining("SENSITIVE_RESULT_RESTRICTED");
        verify(agent, never()).downloadArtifact(anyString());
    }

    @Test
    void agentPayloadCarriesExactResultConfigWithoutAddingLegacyDefaults() throws Exception {
        Method build = service.getClass().getDeclaredMethod("addSshCommandPayload", Map.class, Map.class, Map.class);
        build.setAccessible(true);
        definition.put("replace_regex", "\\n");
        definition.put("replace_value", "");
        Map<String, Object> payload = new LinkedHashMap<>();
        build.invoke(service, payload, definition, Map.of());
        assertThat(payload).containsEntry("replaceRegex", "\\n")
            .containsEntry("replaceValue", "")
            .containsEntry("variableName", "disk")
            .doesNotContainKey("valueMasked");
    }

    private Map<String, Object> response(String status, int exitCode, Map<String, Object> variables) {
        return new LinkedHashMap<>(Map.of("status", status, "exitCode", exitCode, "result", Map
            .of("variables", variables, "infrastructure", Map
                .of("schemaVersion", 2, "kind", "SERVER_COMMAND", "results", List.of()))));
    }
}
