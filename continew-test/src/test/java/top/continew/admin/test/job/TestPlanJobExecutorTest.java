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

package top.continew.admin.test.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.common.json.JsonUtil;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.model.exception.TestPlanDispatchException;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.service.TestPlanService;
import top.continew.admin.test.service.TestTimedTaskRunService;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class TestPlanJobExecutorTest {

    @Mock
    private TestPlanService testPlanService;
    @Mock
    private TestTimedTaskMapper timedTaskMapper;
    @Mock
    private TestTimedTaskRunService timedTaskRunService;

    private TestPlanJobExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TestPlanJobExecutor(testPlanService, timedTaskMapper, timedTaskRunService);
    }

    @Test
    void shouldReloadTaskMasterDataForScheduleTrigger() throws Exception {
        TestTimedTaskDO task = task();
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setId(99L);
        TestPlanExecuteResp executeResp = new TestPlanExecuteResp();
        executeResp.setTestReportId("101");
        when(timedTaskMapper.selectById(1L)).thenReturn(task);
        when(timedTaskRunService.start(1L, "SCHEDULE")).thenReturn(new TestTimedTaskRunService.StartResult(run, false));
        when(testPlanService.execute(any(), any())).thenReturn(executeResp);

        executor.executeTestPlan(payload("SCHEDULE"));

        ArgumentCaptor<TestPlanExecuteReq> reqCaptor = ArgumentCaptor.forClass(TestPlanExecuteReq.class);
        verify(testPlanService).execute(org.mockito.ArgumentMatchers.eq(10L), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getTriggerMode()).isEqualTo("SCHEDULE");
        assertThat(reqCaptor.getValue().getProjectEnvironmentId()).isEqualTo(20L);
        assertThat(reqCaptor.getValue().getAutomationEnvironmentId()).isEqualTo(30L);
        verify(timedTaskRunService).attachExecution(99L, executeResp);
    }

    @Test
    void shouldKeepManualTriggerMode() throws Exception {
        TestTimedTaskDO task = task();
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setId(99L);
        when(timedTaskMapper.selectById(1L)).thenReturn(task);
        when(timedTaskRunService.start(1L, "MANUAL")).thenReturn(new TestTimedTaskRunService.StartResult(run, false));
        when(testPlanService.execute(any(), any())).thenReturn(new TestPlanExecuteResp());

        executor.executeTestPlan(payload("MANUAL"));

        ArgumentCaptor<TestPlanExecuteReq> reqCaptor = ArgumentCaptor.forClass(TestPlanExecuteReq.class);
        verify(testPlanService).execute(org.mockito.ArgumentMatchers.eq(10L), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getTriggerMode()).isEqualTo("MANUAL");
    }

    @Test
    void shouldApplyPlaywrightExecutionConfigFromLockedTask() throws Exception {
        TestTimedTaskDO task = task();
        task.setExecutionEngine("PLAYWRIGHT_RUNNER");
        task.setExecutionConfig(Map
            .of("browser", "chromium", "stepTimeoutMs", 4_000, "caseTimeoutMs", 10_000, "pageErrorCheckEnabled", false));
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setId(99L);
        when(timedTaskRunService.start(1L, "SCHEDULE"))
            .thenReturn(new TestTimedTaskRunService.StartResult(run, false, task));
        when(testPlanService.execute(any(), any())).thenReturn(new TestPlanExecuteResp());

        executor.executeTestPlan(payload("SCHEDULE"));

        ArgumentCaptor<TestPlanExecuteReq> reqCaptor = ArgumentCaptor.forClass(TestPlanExecuteReq.class);
        verify(testPlanService).execute(org.mockito.ArgumentMatchers.eq(10L), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getRunnerOptions().getBrowser()).isEqualTo("chromium");
        assertThat(reqCaptor.getValue().getRunnerOptions().getStepTimeoutMs()).isEqualTo(4_000);
        assertThat(reqCaptor.getValue().getRunnerOptions().getCaseTimeoutMs()).isEqualTo(10_000);
        assertThat(reqCaptor.getValue().getRunnerOptions().getPageErrorCheckEnabled()).isFalse();
    }

    @Test
    void shouldAcceptStructuredJobParamsFromSnailJob() throws Exception {
        TestTimedTaskDO task = task();
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setId(99L);
        when(timedTaskMapper.selectById(1L)).thenReturn(task);
        when(timedTaskRunService.start(1L, "MANUAL")).thenReturn(new TestTimedTaskRunService.StartResult(run, false));
        when(testPlanService.execute(any(), any())).thenReturn(new TestPlanExecuteResp());
        JobArgs jobArgs = new JobArgs();
        jobArgs.setJobParams(java.util.Map.of("taskId", 1L, "triggerMode", "MANUAL"));

        executor.executeTestPlan(jobArgs);

        verify(testPlanService).execute(org.mockito.ArgumentMatchers.eq(10L), any());
    }

    @Test
    void shouldNotExecutePlanWhenOverlapIsSkipped() throws Exception {
        TestTimedTaskRunDO skipped = new TestTimedTaskRunDO();
        skipped.setFailureReason("测试定时任务已禁用");
        when(timedTaskRunService.start(1L, "SCHEDULE"))
            .thenReturn(new TestTimedTaskRunService.StartResult(skipped, true));

        JobArgs jobArgs = new JobArgs();
        jobArgs.setJobParams(payload("SCHEDULE"));
        executor.executeTestPlan(jobArgs);

        verify(testPlanService, never()).execute(any(), any());
        verify(timedTaskMapper, never()).selectById(any());
    }

    @Test
    void shouldAttachReportWhenPlanDispatchFails() throws Exception {
        TestTimedTaskDO task = task();
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setId(99L);
        when(timedTaskRunService.start(1L, "SCHEDULE"))
            .thenReturn(new TestTimedTaskRunService.StartResult(run, false, task));
        when(testPlanService.execute(any(), any()))
            .thenThrow(new TestPlanDispatchException(101L, new IllegalStateException("Runner 不可用")));

        assertThatThrownBy(() -> executor.executeTestPlan(payload("SCHEDULE")))
            .isInstanceOf(TestPlanDispatchException.class)
            .hasMessageContaining("Runner 不可用");

        ArgumentCaptor<TestPlanExecuteResp> respCaptor = ArgumentCaptor.forClass(TestPlanExecuteResp.class);
        verify(timedTaskRunService).attachExecution(org.mockito.ArgumentMatchers.eq(99L), respCaptor.capture());
        assertThat(respCaptor.getValue().getTestReportId()).isEqualTo("101");
        verify(timedTaskRunService).fail(99L, "Runner 不可用");
    }

    private TestTimedTaskDO task() {
        TestTimedTaskDO task = new TestTimedTaskDO();
        task.setId(1L);
        task.setTestPlanId(10L);
        task.setProjectEnvironmentId(20L);
        task.setAutomationEnvironmentId(30L);
        task.setExecutionEngine("SELENIUM");
        task.setExecuteName("定时任务");
        task.setExecuteEmail("owner@example.com");
        return task;
    }

    private String payload(String triggerMode) throws Exception {
        TestTimedTaskExecutePayload payload = new TestTimedTaskExecutePayload();
        payload.setTaskId(1L);
        payload.setTriggerMode(triggerMode);
        return JsonUtil.marshal(payload);
    }
}
