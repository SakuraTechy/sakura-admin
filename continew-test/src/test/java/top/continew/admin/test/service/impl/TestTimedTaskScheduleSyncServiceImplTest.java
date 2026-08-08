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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.schedule.enums.JobStatusEnum;
import top.continew.admin.schedule.model.req.JobReq;
import top.continew.admin.schedule.model.resp.JobResp;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.starter.extension.crud.model.resp.PageResp;

@ExtendWith(MockitoExtension.class)
class TestTimedTaskScheduleSyncServiceImplTest {

    @Mock
    private TestTimedTaskMapper taskMapper;
    @Mock
    private JobService jobService;

    private TestTimedTaskScheduleSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestTimedTaskScheduleSyncServiceImpl(taskMapper, jobService);
        when(taskMapper.claimScheduleSync(any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void shouldCreateMissingRemoteJobAndPersistMetadata() {
        TestTimedTaskDO task = task();
        JobResp remote = remoteJob(99L, "test-plan-task-1");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(jobService.page(any())).thenReturn(page(), page(), page(remote));
        when(jobService.create(any())).thenReturn(true);

        service.syncNow(1L);

        ArgumentCaptor<JobReq> reqCaptor = ArgumentCaptor.forClass(JobReq.class);
        verify(jobService).create(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getJobName()).isEqualTo("test-plan-task-1");
        assertThat(reqCaptor.getValue().getJobStatus()).isEqualTo(JobStatusEnum.ENABLED);
        verify(taskMapper).markScheduleSynced(eq(1L), eq(2L), eq(99L), isNull(), any());
    }

    @Test
    void shouldDeleteRemoteJobForDeletingTask() {
        TestTimedTaskDO task = task();
        task.setStatus("DELETING");
        task.setScheduleJobId(99L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(jobService.page(any())).thenReturn(page(remoteJob(99L, "test-plan-task-1")));
        when(jobService.delete(99L)).thenReturn(true);

        service.syncNow(1L);

        verify(jobService).delete(99L);
        verify(taskMapper).markScheduleDeleted(eq(1L), eq(2L), any());
        verify(jobService, never()).create(any());
    }

    @Test
    void shouldPersistFailureAndExponentialRetryWhenRemoteCreateFails() {
        TestTimedTaskDO task = task();
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(jobService.page(any())).thenReturn(page());
        when(jobService.create(any())).thenReturn(false);

        assertThatThrownBy(() -> service.syncNow(1L)).hasMessageContaining("创建远程调度任务失败");

        verify(taskMapper).markScheduleSyncFailed(eq(1L), eq(2L), any(), any(), eq(1), any());
        verify(taskMapper, never()).markScheduleSynced(any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotCallRemoteApiWhenAnotherInstanceOwnsSyncClaim() {
        TestTimedTaskDO task = task();
        task.setScheduleSyncStatus("SYNCING");
        reset(taskMapper);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.claimScheduleSync(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.syncNow(1L)).hasMessageContaining("正在同步");

        verify(jobService, never()).page(any());
        verify(jobService, never()).create(any());
        verify(jobService, never()).update(any(), any());
    }

    @Test
    void shouldKeepReconcileAliveWhenOrphanCleanupCannotReachScheduleServer() {
        reset(taskMapper);
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(jobService.page(any())).thenThrow(new IllegalStateException("schedule server unavailable"));

        assertThat(service.reconcile()).isZero();
    }

    private TestTimedTaskDO task() {
        TestTimedTaskDO task = new TestTimedTaskDO();
        task.setId(1L);
        task.setName("夜间回归");
        task.setDescription("每日回归");
        task.setCronExpression("0 0 2 * * ?");
        task.setStatus("ENABLED");
        task.setScheduleSyncVersion(2L);
        task.setScheduleSyncRetryCount(0);
        task.setDelFlag(StatusTypeEnum.NORMAL);
        return task;
    }

    private JobResp remoteJob(Long id, String name) {
        JobResp job = new JobResp();
        job.setId(id);
        job.setJobName(name);
        return job;
    }

    @SafeVarargs
    private final PageResp<JobResp> page(JobResp... jobs) {
        PageResp<JobResp> page = new PageResp<>();
        page.setList(List.of(jobs));
        page.setTotal(jobs.length);
        return page;
    }
}
