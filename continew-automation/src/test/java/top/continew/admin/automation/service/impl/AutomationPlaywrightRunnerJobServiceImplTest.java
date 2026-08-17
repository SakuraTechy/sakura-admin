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

import java.lang.reflect.Method;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService;
import top.continew.starter.core.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutomationPlaywrightRunnerJobServiceImplTest {

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
