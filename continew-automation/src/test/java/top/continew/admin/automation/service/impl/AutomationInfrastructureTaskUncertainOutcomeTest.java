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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.continew.admin.automation.converter.AutomationInfrastructureRuntimeBindingResolver;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.mapper.AutomationInfrastructureTaskLogMapper;
import top.continew.admin.automation.mapper.AutomationInfrastructureTaskMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationInfrastructureTaskLogDO;
import top.continew.admin.automation.model.entity.AutomationInfrastructureTaskDO;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskCreateReq;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskDispositionReq;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureStatementResp;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.service.AutomationEnvironmentResourceService;
import top.continew.admin.automation.support.AutomationExecutionAgentClient;
import top.continew.admin.automation.support.AutomationInfrastructureResultSanitizer;
import top.continew.admin.automation.support.AutomationInfrastructureRiskPolicy;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.starter.core.exception.BusinessException;

class AutomationInfrastructureTaskUncertainOutcomeTest {

    @Test
    @SuppressWarnings("unchecked")
    void sqlAndServerCommandShouldComeFromBoundDefinitionRevision() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureTaskMapper taskMapper = mock(AutomationInfrastructureTaskMapper.class);
        AutomationPlaywrightStepExtractor stepExtractor = mock(AutomationPlaywrightStepExtractor.class);
        AutomationInfrastructureRuntimeBindingResolver bindingResolver = mock(AutomationInfrastructureRuntimeBindingResolver.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AutomationInfrastructureTaskDO task = new AutomationInfrastructureTaskDO();
        task.setTaskId("INFRA_SQL");
        task.setCaseKey("SCENE_1:CASE_1");
        task.setStepId("STEP_1");
        task.setActionType("database_sql");
        task.setOwnerUserId(101L);
        task.setDefinitionRevisionId(88L);
        task.setDefinitionVersion(7L);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List
            .of("[{\"id\":\"CASE_1\",\"stepList\":[{\"id\":\"STEP_1\"}]}]"));
        when(stepExtractor.extract(any(), eq(0))).thenReturn(Map
            .of("action_type", "database_sql", "sql_mode", "query", "sql", "SELECT user_id FROM sys_user", "target_ref", Map
                .of("scope", "project_config", "kind", "database", "config_id", 1)));
        AutomationInfrastructureTaskServiceImpl service = new AutomationInfrastructureTaskServiceImpl(taskMapper, mock(AutomationInfrastructureTaskLogMapper.class), mock(AutomationUiSceneMapper.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectServerConfigMapper.class), mock(ProjectDataBaseConfigMapper.class), stepExtractor, bindingResolver, objectMapper, mock(AutomationExecutionAgentClient.class), mock(AutomationUiExecutionRecordService.class), jdbcTemplate, new AutomationInfrastructureResultSanitizer(objectMapper), new AutomationInfrastructureRiskPolicy(""), mock(AutomationEnvironmentResourceService.class));
        try {
            UserContext owner = new UserContext();
            owner.setId(101L);
            UserContextHolder.setContext(owner, false);

            AutomationInfrastructureStatementResp statement = service.getStatement("INFRA_SQL");

            assertThat(statement.getSql()).isEqualTo("SELECT user_id FROM sys_user");
            assertThat(statement.getSqlMode()).isEqualTo("query");
            assertThat(statement.getDefinitionVersion()).isEqualTo(7L);

            task.setActionType("server_command");
            when(stepExtractor.extract(any(), eq(0))).thenReturn(Map
                .of("action_type", "server_command", "command", "sudo systemctl restart sakura-agent", "target_ref", Map
                    .of("scope", "project_config", "kind", "server", "config_id", 2)));

            statement = service.getStatement("INFRA_SQL");

            assertThat(statement.getCommand()).isEqualTo("sudo systemctl restart sakura-agent");
            assertThat(statement.getSql()).isNull();
            assertThat(statement.getSqlMode()).isNull();

            UserContext anotherUser = new UserContext();
            anotherUser.setId(202L);
            UserContextHolder.setContext(anotherUser, false);
            assertThatThrownBy(() -> service.getStatement("INFRA_SQL")).hasMessageContaining("EXECUTION_SCOPE_DENIED");
        } finally {
            UserContextHolder.clearContext();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitResponseLossShouldBecomeUnknownOutcome() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureTaskMapper taskMapper = mock(AutomationInfrastructureTaskMapper.class);
        AutomationInfrastructureTaskLogMapper logMapper = mock(AutomationInfrastructureTaskLogMapper.class);
        AutomationExecutionAgentClient agentClient = mock(AutomationExecutionAgentClient.class);
        when(logMapper.selectList(any())).thenReturn(List.of());
        when(agentClient.submit(any())).thenThrow(new BusinessException("connection closed before response"));
        AutomationInfrastructureTaskServiceImpl service = new AutomationInfrastructureTaskServiceImpl(taskMapper, logMapper, mock(AutomationUiSceneMapper.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectServerConfigMapper.class), mock(ProjectDataBaseConfigMapper.class), mock(AutomationPlaywrightStepExtractor.class), mock(AutomationInfrastructureRuntimeBindingResolver.class), objectMapper, agentClient, mock(AutomationUiExecutionRecordService.class), mock(JdbcTemplate.class), new AutomationInfrastructureResultSanitizer(objectMapper), new AutomationInfrastructureRiskPolicy(""), mock(AutomationEnvironmentResourceService.class));
        AutomationInfrastructureTaskDO task = new AutomationInfrastructureTaskDO();
        task.setTaskId("INFRA_UNCERTAIN");
        task.setActionType("global_variable_system_info");

        Method dispatch = AutomationInfrastructureTaskServiceImpl.class
            .getDeclaredMethod("dispatchToAgent", AutomationInfrastructureTaskDO.class, Map.class, Map.class, Map.class);
        dispatch.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>)dispatch.invoke(service, task, Map
            .of("info_type", "os", "variable_name", "system"), Map.of(), Map.of());

        assertThat(result).isEmpty();
        assertThat(task.getStatus()).isEqualTo("unknown_outcome");
        assertThat(task.getErrorCode()).isEqualTo("TASK_UNKNOWN_OUTCOME");
        assertThat(task.getErrorMessage()).contains("提交响应未确认");
        verify(taskMapper).updateById(task);
    }

    @Test
    void taskReadAndCancelShouldRejectAnotherPrincipal() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureTaskMapper taskMapper = mock(AutomationInfrastructureTaskMapper.class);
        AutomationInfrastructureTaskLogMapper logMapper = mock(AutomationInfrastructureTaskLogMapper.class);
        AutomationExecutionAgentClient agentClient = mock(AutomationExecutionAgentClient.class);
        AutomationInfrastructureTaskDO task = new AutomationInfrastructureTaskDO();
        task.setTaskId("INFRA_OWNED");
        task.setOwnerUserId(101L);
        task.setStatus("running");
        task.setActionType("database_sql");
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(logMapper.selectList(any())).thenReturn(List.of());
        when(agentClient.get("INFRA_OWNED")).thenReturn(Map.of());
        when(agentClient.downloadArtifact("INFRA_OWNED"))
            .thenReturn(new AutomationExecutionAgentClient.ArtifactDownload("INFRA_OWNED.json", "application/json", "{}"
                .getBytes(StandardCharsets.UTF_8), "a".repeat(64)));
        AutomationInfrastructureTaskServiceImpl service = new AutomationInfrastructureTaskServiceImpl(taskMapper, logMapper, mock(AutomationUiSceneMapper.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectServerConfigMapper.class), mock(ProjectDataBaseConfigMapper.class), mock(AutomationPlaywrightStepExtractor.class), mock(AutomationInfrastructureRuntimeBindingResolver.class), objectMapper, agentClient, mock(AutomationUiExecutionRecordService.class), mock(JdbcTemplate.class), new AutomationInfrastructureResultSanitizer(objectMapper), new AutomationInfrastructureRiskPolicy(""), mock(AutomationEnvironmentResourceService.class));
        try {
            UserContext owner = new UserContext();
            owner.setId(101L);
            UserContextHolder.setContext(owner, false);
            AutomationInfrastructureTaskLogDO log = new AutomationInfrastructureTaskLogDO();
            log.setSequence(1L);
            log.setLevel("INFO");
            log.setMessage("任务已创建");
            when(logMapper.selectList(any())).thenReturn(List.of(log));
            assertThat(service.get("INFRA_OWNED", null).getTaskId()).isEqualTo("INFRA_OWNED");
            assertThat(service.get("INFRA_OWNED", null).getLogs()).extracting("message").containsExactly("任务已创建");
            assertThat(service.downloadArtifact("INFRA_OWNED", null).bytes()).isEqualTo("{}"
                .getBytes(StandardCharsets.UTF_8));

            UserContext anotherUser = new UserContext();
            anotherUser.setId(202L);
            UserContextHolder.setContext(anotherUser, false);
            assertThatThrownBy(() -> service.get("INFRA_OWNED", null)).hasMessageContaining("EXECUTION_SCOPE_DENIED");
            assertThatThrownBy(() -> service.cancel("INFRA_OWNED")).hasMessageContaining("EXECUTION_SCOPE_DENIED");
            assertThatThrownBy(() -> service.downloadArtifact("INFRA_OWNED", null))
                .hasMessageContaining("EXECUTION_SCOPE_DENIED");
        } finally {
            UserContextHolder.clearContext();
        }
    }

    @Test
    void createWithoutBoundExecutionContextIsRejectedBeforeTaskInsert() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureTaskMapper taskMapper = mock(AutomationInfrastructureTaskMapper.class);
        AutomationInfrastructureTaskLogMapper logMapper = mock(AutomationInfrastructureTaskLogMapper.class);
        AutomationInfrastructureTaskServiceImpl service = new AutomationInfrastructureTaskServiceImpl(taskMapper, logMapper, mock(AutomationUiSceneMapper.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectServerConfigMapper.class), mock(ProjectDataBaseConfigMapper.class), mock(AutomationPlaywrightStepExtractor.class), mock(AutomationInfrastructureRuntimeBindingResolver.class), objectMapper, mock(AutomationExecutionAgentClient.class), mock(AutomationUiExecutionRecordService.class), mock(JdbcTemplate.class), new AutomationInfrastructureResultSanitizer(objectMapper), new AutomationInfrastructureRiskPolicy(""), mock(AutomationEnvironmentResourceService.class));
        AutomationInfrastructureTaskCreateReq request = new AutomationInfrastructureTaskCreateReq();
        request.setExecutionId("EXEC_UNBOUND");
        request.setCaseKey("CASE_UNBOUND");
        request.setStepId("STEP_UNBOUND");
        try {
            UserContextHolder.clearContext();
            assertThatThrownBy(() -> service.create(request)).hasMessageContaining("EXECUTION_SCOPE_DENIED");
            verifyNoInteractions(taskMapper);
        } finally {
            UserContextHolder.clearContext();
        }
    }

    @Test
    void unknownOutcomeDispositionShouldBeOwnerBoundAndAuditedByDigest() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureTaskMapper taskMapper = mock(AutomationInfrastructureTaskMapper.class);
        AutomationInfrastructureTaskLogMapper logMapper = mock(AutomationInfrastructureTaskLogMapper.class);
        AutomationInfrastructureTaskDO task = new AutomationInfrastructureTaskDO();
        task.setTaskId("INFRA_DISPOSITION");
        task.setOwnerUserId(101L);
        task.setStatus("unknown_outcome");
        task.setActionType("database_sql");
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(logMapper.selectList(any())).thenReturn(List.of());
        AutomationInfrastructureTaskServiceImpl service = new AutomationInfrastructureTaskServiceImpl(taskMapper, logMapper, mock(AutomationUiSceneMapper.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectServerConfigMapper.class), mock(ProjectDataBaseConfigMapper.class), mock(AutomationPlaywrightStepExtractor.class), mock(AutomationInfrastructureRuntimeBindingResolver.class), objectMapper, mock(AutomationExecutionAgentClient.class), mock(AutomationUiExecutionRecordService.class), mock(JdbcTemplate.class), new AutomationInfrastructureResultSanitizer(objectMapper), new AutomationInfrastructureRiskPolicy(""), mock(AutomationEnvironmentResourceService.class));
        AutomationInfrastructureTaskDispositionReq request = new AutomationInfrastructureTaskDispositionReq();
        request.setResolution("confirmed_succeeded");
        request.setVerificationNote("已在目标数据库核对事务提交记录");
        try {
            UserContext owner = new UserContext();
            owner.setId(101L);
            UserContextHolder.setContext(owner, false);

            assertThat(service.disposeUnknownOutcome("INFRA_DISPOSITION", request, null).getStatus())
                .isEqualTo("passed");
            assertThat(task.getDisposition()).isEqualTo("confirmed_succeeded");
            assertThat(task.getDispositionUserId()).isEqualTo(101L);
            assertThat(task.getDispositionNoteDigest()).hasSize(64);
            assertThat(task.toString()).doesNotContain(request.getVerificationNote());
            assertThatThrownBy(() -> service.disposeUnknownOutcome("INFRA_DISPOSITION", request, null))
                .hasMessageContaining("TASK_DISPOSITION_NOT_ALLOWED");
        } finally {
            UserContextHolder.clearContext();
        }
    }
}
