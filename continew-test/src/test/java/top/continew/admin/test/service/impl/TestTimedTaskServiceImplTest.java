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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.mapper.AutomationEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.schedule.enums.JobExecuteReasonEnum;
import top.continew.admin.schedule.enums.JobExecuteStatusEnum;
import top.continew.admin.schedule.model.query.JobLogQuery;
import top.continew.admin.schedule.model.resp.JobLogResp;
import top.continew.admin.schedule.service.JobLogService;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.query.TestTimedTaskLogQuery;
import top.continew.admin.test.model.resp.TestTimedTaskLogResp;
import top.continew.admin.test.model.resp.TestTimedTaskResp;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.admin.test.service.TestTimedTaskScheduleSyncService;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class TestTimedTaskServiceImplTest {

    @Mock
    private TestPlanMapper planMapper;
    @Mock
    private ProjectEnvironmentConfigMapper projectEnvironmentMapper;
    @Mock
    private AutomationEnvironmentConfigMapper automationEnvironmentMapper;
    @Mock
    private JobService jobService;
    @Mock
    private JobLogService jobLogService;
    @Mock
    private TestTimedTaskRunService runService;
    @Mock
    private TestTimedTaskScheduleSyncService scheduleSyncService;
    @Mock
    private TestTimedTaskMapper timedTaskMapper;

    private TestTimedTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestTimedTaskServiceImpl(planMapper, projectEnvironmentMapper, automationEnvironmentMapper, jobService, jobLogService, runService, scheduleSyncService);
        ReflectionTestUtils.setField(service, "baseMapper", timedTaskMapper);
    }

    @Test
    void shouldPersistDeleteIntentBeforeSynchronizingRemoteJob() {
        TestTimedTaskDO task = new TestTimedTaskDO();
        task.setId(1L);
        task.setScheduleJobId(99L);
        task.setScheduleSyncVersion(2L);
        when(timedTaskMapper.selectByIdForUpdate(1L)).thenReturn(task);
        when(timedTaskMapper.update(isNull(), any())).thenReturn(1);

        service.deleteByIds(List.of(1L));

        verify(timedTaskMapper).update(isNull(), any());
        verify(scheduleSyncService).submit(1L);
        verify(jobService, never()).delete(any());
    }

    @Test
    void shouldEnrichTaskWithoutAutomationEnvironment() {
        TestTimedTaskResp task = new TestTimedTaskResp();
        task.setId(1L);
        task.setTestPlanId(10L);
        task.setProjectEnvironmentId(20L);
        task.setAutomationEnvironmentId(null);

        TestPlanDO plan = new TestPlanDO();
        plan.setId(10L);
        plan.setProjectId(30L);
        plan.setProjectName("project");
        ProjectEnvironmentConfigDO projectEnvironment = new ProjectEnvironmentConfigDO();
        projectEnvironment.setId(20L);
        projectEnvironment.setName("product");
        when(planMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(plan));
        when(projectEnvironmentMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(projectEnvironment));
        when(runService.latestByTaskIds(List.of(1L))).thenReturn(Map.of());

        ReflectionTestUtils.invokeMethod(service, "enrich", List.of(task));

        verify(automationEnvironmentMapper, never()).selectBatchIds(any());
    }

    @Test
    void shouldFilterAndEnrichScheduleLogs() {
        TestTimedTaskDO task = new TestTimedTaskDO();
        task.setId(1L);
        task.setScheduleJobId(99L);
        when(timedTaskMapper.selectById(1L)).thenReturn(task);

        JobLogResp sourceLog = new JobLogResp();
        sourceLog.setId(700L);
        sourceLog.setJobId(99L);
        sourceLog.setGroupName("sakura-admin");
        sourceLog.setJobName("测试计划调度");
        sourceLog.setTaskBatchStatus(JobExecuteStatusEnum.FAILED);
        sourceLog.setOperationReason(JobExecuteReasonEnum.TIME_OUT);
        sourceLog.setExecutorType(1);
        sourceLog.setExecutorInfo("ExecuteTestPlanJob");
        PageResp<JobLogResp> sourcePage = new PageResp<>();
        sourcePage.setList(List.of(sourceLog));
        sourcePage.setTotal(1L);
        when(jobLogService.page(any())).thenReturn(sourcePage);

        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 10, 18, 0);
        TestTimedTaskLogQuery query = new TestTimedTaskLogQuery();
        query.setTaskBatchStatus(JobExecuteStatusEnum.FAILED.getValue());
        query.setStartTime(start);
        query.setEndTime(end);

        PageResp<TestTimedTaskLogResp> result = service.pageLogs(1L, query, new PageQuery());

        ArgumentCaptor<JobLogQuery> captor = ArgumentCaptor.forClass(JobLogQuery.class);
        verify(jobLogService).page(captor.capture());
        assertThat(captor.getValue().getJobId()).isEqualTo(99L);
        assertThat(captor.getValue().getTaskBatchStatus()).isEqualTo(JobExecuteStatusEnum.FAILED.getValue());
        assertThat(captor.getValue().getDatetimeRange()).containsExactly(start, end);
        assertThat(result.getList()).singleElement().satisfies(log -> {
            assertThat(log.getId()).isEqualTo(700L);
            assertThat(log.getGroupName()).isEqualTo("sakura-admin");
            assertThat(log.getTaskBatchStatus()).isEqualTo(JobExecuteStatusEnum.FAILED.getValue());
            assertThat(log.getOperationReason()).isEqualTo(JobExecuteReasonEnum.TIME_OUT.getValue());
            assertThat(log.getExecutorType()).isEqualTo(1);
        });
    }
}
