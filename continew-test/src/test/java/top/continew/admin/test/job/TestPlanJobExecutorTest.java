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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.service.TestPlanService;
import top.continew.admin.test.service.TestTimedTaskRunService;

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
    void shouldNotExecutePlanWhenOverlapIsSkipped() throws Exception {
        TestTimedTaskDO task = task();
        when(timedTaskMapper.selectById(1L)).thenReturn(task);
        when(timedTaskRunService.start(1L, "SCHEDULE"))
            .thenReturn(new TestTimedTaskRunService.StartResult(new TestTimedTaskRunDO(), true));

        executor.executeTestPlan(payload("SCHEDULE"));

        verify(testPlanService, never()).execute(any(), any());
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
