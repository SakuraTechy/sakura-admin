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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.service.TestTimedTaskRunService;

@ExtendWith(MockitoExtension.class)
class TestPlanReportProgressServiceImplTest {

    @Mock
    private AutomationUiExecutionRecordService executionRecordService;

    @Mock
    private TestPlanMapper planMapper;

    @Mock
    private TestReportMapper reportMapper;

    @Mock
    private TestTimedTaskRunService timedTaskRunService;

    private TestPlanReportProgressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestPlanReportProgressServiceImpl(executionRecordService, planMapper, reportMapper, timedTaskRunService);
    }

    @Test
    void shouldAggregateOnlyRecordsBelongingToCurrentReport() {
        TestPlanDO plan = plan();
        plan.setActualStartTime(LocalDateTime.now().minusSeconds(1));
        TestReportDO report = report();
        Map<Long, Map<String, Object>> records = Map.of(11L, Map
            .of("testReportId", "101", "executeStatus", "completed", "executeResult", "passed", "caseTotal", 2, "casePass", 2, "stepTotal", 3, "stepPass", 3), 12L, Map
                .of("testReportId", "101", "executeStatus", "completed", "executeResult", "skipped", "caseSkip", 1));
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(reportMapper.selectById(101L)).thenReturn(report);
        when(executionRecordService.findReportRecords(List.of(11L, 12L), "101")).thenReturn(records);

        service.onProgressChanged("1", "101");

        assertThat(report.getStatus()).isEqualTo("PASSED");
        assertThat(plan.getStatus()).isEqualTo("COMPLETED");
        assertThat(plan.getExecutedCount()).isEqualTo(2);
        assertThat(plan.getPassedCount()).isEqualTo(1);
        assertThat(plan.getRunTime()).isPositive();
        assertThat(plan.getActualEndTime()).isNotNull();
        @SuppressWarnings("unchecked") Map<String, Object> ui = (Map<String, Object>)report.getStatisticAnalysis()
            .get("ui");
        assertThat(ui).containsEntry("sceneTotal", 2)
            .containsEntry("scenePass", 1)
            .containsEntry("sceneSkip", 1)
            .containsEntry("caseTotal", 2)
            .containsEntry("caseFail", 0)
            .containsEntry("stepPass", 3);
        verify(reportMapper).updateById(report);
        verify(planMapper).updateById(plan);
        verify(timedTaskRunService).completeByReport(report);
    }

    @Test
    void shouldRejectMismatchedReportType() {
        TestReportDO report = report();
        report.setReportType("SELENIUM");
        when(reportMapper.selectById(101L)).thenReturn(report);

        assertThatThrownBy(() -> service.validateBinding("1", "101", "11", "playwright-runner"))
            .hasMessageContaining("测试报告类型与执行方式不一致");
    }

    @Test
    void shouldAggregateOnlyExecutionSceneSubset() {
        TestPlanDO plan = plan();
        TestReportDO report = report();
        report.setRuntimeEnvironment(Map.of("executionSceneIds", List.of("11")));
        Map<Long, Map<String, Object>> records = Map.of(11L, Map
            .of("testReportId", "101", "executeStatus", "completed", "executeResult", "passed", "caseTotal", 1, "casePass", 1));
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(reportMapper.selectById(101L)).thenReturn(report);
        when(executionRecordService.findReportRecords(List.of(11L), "101")).thenReturn(records);

        service.onProgressChanged("1", "101");

        @SuppressWarnings("unchecked") Map<String, Object> ui = (Map<String, Object>)report.getStatisticAnalysis()
            .get("ui");
        assertThat(ui).containsEntry("sceneTotal", 1).containsEntry("scenePass", 1);
        assertThat(plan.getSceneCount()).isEqualTo(2);
        assertThat(plan.getExecutedCount()).isEqualTo(1);
        assertThat(plan.getTestProgress()).isEqualByComparingTo("50.00");
        assertThat(plan.getStatus()).isEqualTo("COMPLETED");
        verify(executionRecordService).findReportRecords(List.of(11L), "101");
    }

    @Test
    void shouldKeepCancelledCountsSeparateFromSkippedCounts() {
        TestPlanDO plan = plan();
        TestReportDO report = report();
        report.setRuntimeEnvironment(Map.of("executionSceneIds", List.of(11L)));
        Map<Long, Map<String, Object>> records = Map.of(11L, Map
            .of("testReportId", "101", "executeStatus", "cancelled", "executeResult", "cancelled", "caseTotal", 1, "caseCancelled", 1, "caseSkip", 0));
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(reportMapper.selectById(101L)).thenReturn(report);
        when(executionRecordService.findReportRecords(List.of(11L), "101")).thenReturn(records);

        service.onProgressChanged("1", "101");

        @SuppressWarnings("unchecked") Map<String, Object> ui = (Map<String, Object>)report.getStatisticAnalysis()
            .get("ui");
        assertThat(ui).containsEntry("sceneCancelled", 1)
            .containsEntry("sceneSkip", 0)
            .containsEntry("caseCancelled", 1)
            .containsEntry("caseSkip", 0);
        assertThat(report.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldAggregatePlaywrightArtifactsIntoReport() {
        TestPlanDO plan = plan();
        TestReportDO report = report();
        Map<Long, Map<String, Object>> records = Map.of(11L, Map
            .of("testReportId", "101", "executeStatus", "completed", "executeResult", "passed", "caseTotal", 1, "casePass", 1, "caseResults", List
                .of(Map.of("artifact_urls", Map
                    .of("report_html", "/automation/playwright/artifacts/run/report.html", "video", "/automation/playwright/artifacts/run/video.webm", "console_log", "D:\\\\runner\\\\artifacts\\\\console.log"), "artifact_file_ids", Map
                        .of("report_html", "501"), "artifact_upload_errors", List.of(Map
                            .of("artifact_type", "trace", "error", "upload failed"))))));
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(reportMapper.selectById(101L)).thenReturn(report);
        when(executionRecordService.findReportRecords(List.of(11L, 12L), "101")).thenReturn(records);

        service.onProgressChanged("1", "101");

        @SuppressWarnings("unchecked") Map<String, Object> artifacts = (Map<String, Object>)report
            .getStatisticAnalysis()
            .get("playwrightArtifacts");
        assertThat(artifacts).containsEntry("count", 2);
        @SuppressWarnings("unchecked") Map<String, Set<String>> urls = (Map<String, Set<String>>)artifacts.get("urls");
        assertThat(urls.get("report_html")).containsExactly("/automation/playwright/artifacts/run/report.html");
        assertThat(report.getReportUrl()).isEqualTo("/automation/playwright/artifacts/run/report.html");
        assertThat(report.getVideoUrl()).isEqualTo("/automation/playwright/artifacts/run/video.webm");
    }

    @Test
    void shouldRejectSceneOutsideReportExecutionScope() {
        TestPlanDO plan = plan();
        TestReportDO report = report();
        report.setRuntimeEnvironment(Map.of("executionSceneIds", List.of(11L)));
        when(reportMapper.selectById(101L)).thenReturn(report);
        when(planMapper.selectById(1L)).thenReturn(plan);

        assertThatThrownBy(() -> service.validateBinding("1", "101", "12", "playwright-runner"))
            .hasMessageContaining("UI 场景不属于当前报告执行范围");
    }

    @Test
    void shouldFailReportWhenPlanHasNoExecutableScene() {
        TestPlanDO plan = plan();
        plan.setUiTestScene(List.of());
        TestReportDO report = report();
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(reportMapper.selectById(101L)).thenReturn(report);

        service.onProgressChanged("1", "101");

        assertThat(report.getStatus()).isEqualTo("FAILED");
        @SuppressWarnings("unchecked") Map<String, Object> ui = (Map<String, Object>)report.getStatisticAnalysis()
            .get("ui");
        assertThat(ui).containsEntry("failureReason", "无可执行用例");
        assertThat(plan.getStatus()).isEqualTo("COMPLETED");
    }

    private TestPlanDO plan() {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(1L);
        plan.setUiTestScene(List.of(11L, 12L));
        return plan;
    }

    private TestReportDO report() {
        TestReportDO report = new TestReportDO();
        report.setId(101L);
        report.setTestPlanId(1L);
        report.setReportType("PLAYWRIGHT_RUNNER");
        report.setStatus("RUNNING");
        return report;
    }

}
