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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService.SessionFiles;
import top.continew.starter.core.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationPlaywrightRunnerJobServiceImplTest {

    @TempDir
    private Path temporaryDirectory;

    private final AutomationPlaywrightSessionStateService sessionStateService = mock(AutomationPlaywrightSessionStateService.class);
    private final AutomationPlaywrightCaseService caseService = mock(AutomationPlaywrightCaseService.class);
    private final AutomationPlaywrightRunnerJobServiceImpl service = new AutomationPlaywrightRunnerJobServiceImpl(caseService, new ObjectMapper(), sessionStateService);

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void shouldEnableSemanticLocatorAndPreserveExplicitPageErrorOverride() throws Exception {
        AutomationPlaywrightRunnerOptionsReq options = new AutomationPlaywrightRunnerOptionsReq();
        options.setPageErrorCheckEnabled(false);
        AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
        request.setProjectEnvironmentId(47L);
        request.setExecutionCapability("capability-1");
        request.setOptions(options);

        List<String> command = invokeBuildCommand(request);

        assertThat(optionValue(command, "--locator-mode")).isEqualTo("semantic-v1");
        assertThat(optionValue(command, "--ignore-https-errors")).isEqualTo("true");
        assertThat(optionValue(command, "--page-error-check-enabled")).isEqualTo("false");
        assertThat(command).doesNotContain("--execution-capability");
    }

    @Test
    void shouldOmitPageErrorOverrideWhenTaskInheritsCaseValue() throws Exception {
        AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
        request.setProjectEnvironmentId(47L);
        request.setOptions(new AutomationPlaywrightRunnerOptionsReq());

        List<String> command = invokeBuildCommand(request);

        assertThat(command).doesNotContain("--page-error-check-enabled");
    }

    @Test
    void shouldPreserveExecutionCapabilityForRunnerEnvironmentInjection() throws Exception {
        AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
        request.setCaseKey("SCENE_001:CASE_001");
        request.setExecutionCapability("capability-1");

        Method method = AutomationPlaywrightRunnerJobServiceImpl.class
            .getDeclaredMethod("normalizeRequest", AutomationPlaywrightRunnerJobReq.class);
        method.setAccessible(true);
        AutomationPlaywrightRunnerJobReq normalized = (AutomationPlaywrightRunnerJobReq)method.invoke(service, request);

        assertThat(normalized.getExecutionCapability()).isEqualTo("capability-1");
    }

    @Test
    void shouldNotTreatAdminCommandOptionAsErrorLog() throws Exception {
        Method method = AutomationPlaywrightRunnerJobServiceImpl.class.getDeclaredMethod("inferLevel", String.class);
        method.setAccessible(true);

        String level = (String)method
            .invoke(service, "[admin] command=node src/index.js --page-error-check-enabled false");

        assertThat(level).isEqualTo("info");
    }

    @Test
    void shouldRejectNewRunnerJobAfterBatchCancellation() throws Exception {
        AutomationPlaywrightCaseCancellationResp cancellation = new AutomationPlaywrightCaseCancellationResp();
        cancellation.setBatchCancelRequested(true);
        when(caseService.getCaseCancellation("SCENE_001", "BATCH_001", "CASE_001")).thenReturn(cancellation);
        AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
        request.setBatchId("BATCH_001");

        Method method = AutomationPlaywrightRunnerJobServiceImpl.class
            .getDeclaredMethod("ensureBatchCaseNotCancelled", AutomationPlaywrightRunnerJobReq.class, String.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> method.invoke(service, request, "SCENE_001:CASE_001"))
            .hasCauseInstanceOf(BusinessException.class)
            .hasRootCauseMessage("Playwright Runner 批次已取消，不能创建新任务");
    }

    @Test
    void shouldPreserveVideoPolicyForReuseBrowserSession() throws Exception {
        AutomationPlaywrightRunnerOptionsReq options = new AutomationPlaywrightRunnerOptionsReq();
        options.setSessionMode("reuse-browser");
        options.setVideo("retain-on-failure");
        AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
        request.setProjectEnvironmentId(47L);
        request.setOptions(options);

        List<String> command = invokeBuildCommand(request);

        assertThat(optionValue(command, "--session-mode")).isEqualTo("reuse-browser");
        assertThat(optionValue(command, "--video")).isEqualTo("retain-on-failure");
    }

    @Test
    void shouldInjectPrivateVariableFilesWithoutChangingBrowserSessionArguments() throws Exception {
        SessionFiles files = new SessionFiles(Path.of("private/current.json"), Path.of("private/candidate.json"));
        when(sessionStateService.hasCurrent(files)).thenReturn(true);
        for (String sessionMode : List.of("isolated", "reuse-auth", "reuse-browser")) {
            AutomationPlaywrightRunnerOptionsReq options = new AutomationPlaywrightRunnerOptionsReq();
            options.setSessionMode(sessionMode);
            AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
            request.setProjectEnvironmentId(47L);
            request.setBatchId("BATCH_001");
            request.setOptions(options);
            Map<String, String> environment = new HashMap<>();

            invokeVariableEnvironment(environment, files);
            List<String> command = invokeBuildCommand(request);

            assertThat(environment).containsEntry("SAKURA_PLAYWRIGHT_VARIABLE_STATE_IN", files.currentPath().toString())
                .containsEntry("SAKURA_PLAYWRIGHT_VARIABLE_STATE_OUT", files.candidatePath().toString());
            assertThat(command).doesNotContain(files.currentPath().toString(), files.candidatePath().toString());
            assertThat(optionValue(command, "--session-mode")).isEqualTo(sessionMode);
        }
    }

    @Test
    void shouldNotInheritVariableFilesFromHostOrLoadUncommittedCandidate() throws Exception {
        Map<String, String> environment = new HashMap<>(Map
            .of("SAKURA_PLAYWRIGHT_VARIABLE_STATE_IN", "another-batch.json", "SAKURA_PLAYWRIGHT_VARIABLE_STATE_OUT", "another-candidate.json"));
        invokeVariableEnvironment(environment, null);
        assertThat(environment).isEmpty();

        SessionFiles files = new SessionFiles(Path.of("private/current.json"), Path.of("private/candidate.json"));
        invokeVariableEnvironment(environment, files);
        assertThat(environment).containsOnlyKeys("SAKURA_PLAYWRIGHT_VARIABLE_STATE_OUT");
    }

    @Test
    void shouldCommitVariablesOnlyAfterSuccessfulUncancelledProcessAndResultDelivery() throws Exception {
        for (String outcome : List.of("success", "failed", "report-failed", "cancelled")) {
            clearInvocations(sessionStateService);
            AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
            request.setBatchId("BATCH_001");
            request.setProjectEnvironmentId(47L);
            request.setOptions(new AutomationPlaywrightRunnerOptionsReq());
            SessionFiles files = new SessionFiles(temporaryDirectory.resolve("current.json"), temporaryDirectory
                .resolve(outcome + ".json"));
            Object runtime = newRuntime(request, files);
            setField(runtime, "cancelRequested", "cancelled".equals(outcome));
            Method method = AutomationPlaywrightRunnerJobServiceImpl.class
                .getDeclaredMethod("promoteVariableState", runtime.getClass(), int.class, boolean.class);
            method.setAccessible(true);
            method.invoke(service, runtime, "failed".equals(outcome) ? 1 : 0, "report-failed".equals(outcome));

            if ("success".equals(outcome)) {
                verify(sessionStateService).promoteVariables(files);
            } else {
                verify(sessionStateService, never()).promoteVariables(any());
            }
        }
    }

    @Test
    void shouldBlockAnotherCaseUntilTheCurrentBatchJobIsTerminal() throws Exception {
        AutomationPlaywrightRunnerJobReq request = new AutomationPlaywrightRunnerJobReq();
        request.setBatchId("BATCH_001");
        Object runtime = newRuntime(request, null);
        @SuppressWarnings("unchecked") Map<String, Object> jobs = (Map<String, Object>)fieldValue(service, "jobs");
        jobs.put("JOB_001", runtime);
        Method method = AutomationPlaywrightRunnerJobServiceImpl.class
            .getDeclaredMethod("hasActiveBatchJob", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "BATCH_001")).isEqualTo(true);
        assertThat(method.invoke(service, "BATCH_002")).isEqualTo(false);
        setField(runtime, "status", "passed");
        assertThat(method.invoke(service, "BATCH_001")).isEqualTo(false);
    }

    private Object newRuntime(AutomationPlaywrightRunnerJobReq request, SessionFiles variables) throws Exception {
        Class<?> runtimeClass = Class.forName(AutomationPlaywrightRunnerJobServiceImpl.class.getName() + "$JobRuntime");
        Constructor<?> constructor = runtimeClass
            .getDeclaredConstructor(String.class, String.class, AutomationPlaywrightRunnerJobReq.class, SessionFiles.class, SessionFiles.class, Long.class);
        constructor.setAccessible(true);
        return constructor.newInstance("JOB_001", "1:CASE_001", request, null, variables, 0L);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object fieldValue(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void invokeVariableEnvironment(Map<String, String> environment, SessionFiles files) throws Exception {
        Method method = AutomationPlaywrightRunnerJobServiceImpl.class
            .getDeclaredMethod("configureVariableEnvironment", Map.class, SessionFiles.class);
        method.setAccessible(true);
        method.invoke(service, environment, files);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeBuildCommand(AutomationPlaywrightRunnerJobReq request) throws Exception {
        Method method = AutomationPlaywrightRunnerJobServiceImpl.class
            .getDeclaredMethod("buildCommand", AutomationPlaywrightRunnerJobReq.class, String.class, String.class, AutomationPlaywrightSessionStateService.SessionFiles.class);
        method.setAccessible(true);
        return (List<String>)method.invoke(service, request, "SCENE_001:CASE_001", "JOB_001", null);
    }

    private String optionValue(List<String> command, String option) {
        int index = command.indexOf(option);
        return index >= 0 && index + 1 < command.size() ? command.get(index + 1) : null;
    }
}
