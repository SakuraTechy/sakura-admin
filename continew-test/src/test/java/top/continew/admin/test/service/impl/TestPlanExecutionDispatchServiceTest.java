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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;
import top.continew.admin.automation.service.AutomationPlanReportProgressService;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestPlanExecutionDispatchServiceTest {

    private final AutomationUiSceneMapper sceneMapper = mock(AutomationUiSceneMapper.class);
    private final AutomationUiExecutionRecordService executionRecordService = mock(AutomationUiExecutionRecordService.class);
    private final AutomationPlaywrightCaseService caseService = mock(AutomationPlaywrightCaseService.class);
    private final AutomationPlaywrightRunnerJobService runnerJobService = mock(AutomationPlaywrightRunnerJobService.class);
    private final AutomationPlanReportProgressService reportProgressService = mock(AutomationPlanReportProgressService.class);
    private final TestPlanMapper testPlanMapper = mock(TestPlanMapper.class);
    private final TestPlanRunnerTokenService runnerTokenService = mock(TestPlanRunnerTokenService.class);
    private final TestPlanExecutionDispatchService service = new TestPlanExecutionDispatchService(sceneMapper, executionRecordService, caseService, runnerJobService, reportProgressService, testPlanMapper, runnerTokenService);

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void shouldStopPlanDispatchWhenSceneBatchWasCancelledExternally() {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(1L);
        TestPlanExecuteResp.SceneExecution scene = new TestPlanExecuteResp.SceneExecution();
        scene.setSceneKey("100");
        scene.setCaseIds(List.of("CASE_001"));
        AutomationPlaywrightBatchResp batch = new AutomationPlaywrightBatchResp();
        batch.setBatchId("BATCH_001");
        batch.setCases(List.of());
        when(caseService.createBatch(any())).thenReturn(batch);
        AutomationPlaywrightCaseCancellationResp cancellation = new AutomationPlaywrightCaseCancellationResp();
        cancellation.setBatchCancelRequested(true);
        when(caseService.getCaseCancellation("100", "BATCH_001", "CASE_001")).thenReturn(cancellation);

        service.dispatchRunner(plan, "REPORT_001", new TestPlanExecuteReq(), List.of(scene), "token");

        verify(runnerJobService, timeout(2_000)).cancelBatch("BATCH_001");
        verify(caseService, timeout(2_000)).cancelBatch("100", "BATCH_001");
        verify(reportProgressService, timeout(2_000)).onProgressChanged("1", "REPORT_001");
        verify(runnerJobService, never()).create(any(), anyString());
    }

    @Test
    void shouldIssueAndReleaseTemporaryTokenForUnattendedExecution() {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(1L);
        plan.setCreateUser(7L);
        TestPlanRunnerTokenService.TokenLease lease = new TestPlanRunnerTokenService.TokenLease("temporary-token");
        when(runnerTokenService.issue(7L, "REPORT_001")).thenReturn(lease);

        service.dispatchRunner(plan, "REPORT_001", new TestPlanExecuteReq(), List.of(), null);

        verify(runnerTokenService).issue(7L, "REPORT_001");
        verify(runnerTokenService, timeout(2_000)).release(lease);
        verify(reportProgressService, timeout(2_000)).onProgressChanged("1", "REPORT_001");
    }

    @Test
    void shouldKillRunnerBatchEvenWhenSceneRecordLooksTerminal() {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(1L);
        plan.setUiTestScene(List.of(100L));
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(100L);
        scene.setTestRecord(List.of(Map
            .of("testReportId", "REPORT_001", "batchId", "BATCH_001", "executeStatus", "completed")));
        when(testPlanMapper.selectById(1L)).thenReturn(plan);
        when(sceneMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(scene));
        when(executionRecordService.findReportRecord(100L, "REPORT_001")).thenReturn(Map
            .of("testReportId", "REPORT_001", "batchId", "BATCH_001", "executeStatus", "completed"));

        service.cancel("1", "REPORT_001");

        verify(runnerJobService).cancelBatch("BATCH_001");
        verify(caseService).cancelBatch("100", "BATCH_001");
    }

    @Test
    void shouldNotStartRunnerForBatchCaseRejectedDuringConfigResolution() {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(1L);
        TestPlanExecuteResp.SceneExecution scene = new TestPlanExecuteResp.SceneExecution();
        scene.setSceneKey("100");
        scene.setCaseIds(List.of("CASE_BAD", "CASE_DISABLED", "CASE_OK"));
        AutomationPlaywrightBatchResp batch = new AutomationPlaywrightBatchResp();
        batch.setBatchId("BATCH_001");
        AutomationPlaywrightBatchResp.CaseExecution rejected = new AutomationPlaywrightBatchResp.CaseExecution();
        rejected.setCaseId("CASE_BAD");
        rejected.setStatus("failed");
        AutomationPlaywrightBatchResp.CaseExecution queued = new AutomationPlaywrightBatchResp.CaseExecution();
        queued.setCaseId("CASE_OK");
        queued.setExecutionId("EXEC_001");
        queued.setStatus("queued");
        queued.setEffectiveExecutionConfig(Map
            .of("browser", "firefox", "case_timeout_ms", 12000, "headed", true, "sources", Map
                .of("browser", "case-default")));
        batch.setCases(List.of(rejected, queued));
        batch.setExecutionCapability("capability");
        when(caseService.createBatch(any())).thenReturn(batch);
        when(caseService.getCaseCancellation(anyString(), anyString(), anyString()))
            .thenReturn(new AutomationPlaywrightCaseCancellationResp());
        AutomationPlaywrightRunnerJobResp job = new AutomationPlaywrightRunnerJobResp();
        job.setJobId("JOB_001");
        job.setStatus("passed");
        when(runnerJobService.create(any(), anyString())).thenReturn(job);

        service.dispatchRunner(plan, "REPORT_001", new TestPlanExecuteReq(), List.of(scene), "token");

        verify(runnerJobService, timeout(2_000)).create(argThat(request -> "100:CASE_OK".equals(request
            .getCaseKey())), anyString());
        ArgumentCaptor<top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq> requestCaptor = ArgumentCaptor
            .forClass(top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq.class);
        verify(runnerJobService, timeout(2_000)).create(requestCaptor.capture(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals("firefox", requestCaptor.getValue().getOptions().getBrowser());
        org.junit.jupiter.api.Assertions.assertEquals(12000, requestCaptor.getValue().getOptions().getCaseTimeoutMs());
        org.junit.jupiter.api.Assertions.assertTrue(requestCaptor.getValue().getOptions().getHeaded());
        verify(runnerJobService, never()).create(argThat(request -> "100:CASE_BAD".equals(request
            .getCaseKey())), anyString());
        verify(runnerJobService, never()).create(argThat(request -> "100:CASE_DISABLED".equals(request
            .getCaseKey())), anyString());
    }
}
