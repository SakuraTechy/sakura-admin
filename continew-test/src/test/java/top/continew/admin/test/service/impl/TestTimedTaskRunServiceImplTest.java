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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.service.TestTimedTaskNotificationService;
import top.continew.admin.test.service.TestTimedTaskRunService;

@ExtendWith(MockitoExtension.class)
class TestTimedTaskRunServiceImplTest {

    @Mock
    private TestTimedTaskMapper taskMapper;
    @Mock
    private TestPlanMapper planMapper;
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private TestTimedTaskRunMapper runMapper;
    @Mock
    private TestReportMapper reportMapper;
    @Mock
    private TestTimedTaskNotificationService notificationService;

    private TestTimedTaskRunServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestTimedTaskRunServiceImpl(taskMapper, planMapper, runMapper, reportMapper, notificationService);
        lenient().when(runMapper.insert(any(TestTimedTaskRunDO.class))).thenAnswer(invocation -> {
            TestTimedTaskRunDO run = invocation.getArgument(0);
            run.setId(100L);
            return 1;
        });
    }

    @Test
    void shouldSkipDeletedTaskForScheduleTrigger() {
        TestTimedTaskDO task = task("ENABLED");
        task.setDelFlag(StatusTypeEnum.ABNORMAL);
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task);

        TestTimedTaskRunService.StartResult result = service.start(1L, "SCHEDULE");

        assertSkipped(result, "测试定时任务已删除");
        verify(planMapper, never()).selectById(any());
    }

    @Test
    void shouldSkipDisabledTaskForScheduleTrigger() {
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task("DISABLED"));

        TestTimedTaskRunService.StartResult result = service.start(1L, "SCHEDULE");

        assertSkipped(result, "测试定时任务已禁用");
        verify(planMapper, never()).selectById(any());
    }

    @Test
    void shouldSkipDeletingTaskForManualTrigger() {
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task("DELETING"));

        TestTimedTaskRunService.StartResult result = service.start(1L, "MANUAL");

        assertSkipped(result, "测试定时任务正在删除");
        verify(planMapper, never()).selectById(any());
    }

    @Test
    void shouldAllowDisabledTaskForManualTrigger() {
        TestTimedTaskDO task = task("DISABLED");
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task);
        when(planMapper.selectById(10L)).thenReturn(plan(StatusTypeEnum.NORMAL));
        when(runMapper.selectList(any())).thenReturn(List.of());

        TestTimedTaskRunService.StartResult result = service.start(1L, "MANUAL");

        assertThat(result.skipped()).isFalse();
        assertThat(result.run().getStatus()).isEqualTo("RUNNING");
        assertThat(result.run().getTriggerMode()).isEqualTo("MANUAL");
        assertThat(result.task()).isSameAs(task);
    }

    @Test
    void shouldSkipDeletedPlanForManualAndScheduleTriggers() {
        TestTimedTaskDO task = task("ENABLED");
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task);
        when(planMapper.selectById(10L)).thenReturn(plan(StatusTypeEnum.ABNORMAL));

        TestTimedTaskRunService.StartResult result = service.start(1L, "MANUAL");

        assertSkipped(result, "测试计划不存在或已删除");
    }

    @Test
    void shouldMapCancelledReportAndNotifyOnce() {
        TestTimedTaskRunDO run = runningRun(100L);
        TestReportDO report = new TestReportDO();
        report.setId(501L);
        report.setStatus("CANCELLED");
        report.setRunTime(123L);
        when(runMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(run);
        when(runMapper
            .finishRunning(eq(100L), eq("CANCELLED"), any(), eq(123L), eq(501L), isNull(), isNull(), isNull(), eq("测试计划执行已取消")))
            .thenReturn(1);

        service.completeByReport(report);

        verify(runMapper)
            .finishRunning(eq(100L), eq("CANCELLED"), any(), eq(123L), eq(501L), isNull(), isNull(), isNull(), eq("测试计划执行已取消"));
        verify(notificationService).send(100L);
    }

    @Test
    void shouldUseReportFailureReasonWhenDispatchSucceeded() {
        TestTimedTaskRunDO run = runningRun(100L);
        TestReportDO report = new TestReportDO();
        report.setId(501L);
        report.setStatus("FAILED");
        report.setStatisticAnalysis(Map.of("ui", Map.of("failureReason", "Runner 服务端鉴权令牌缺失")));
        when(runMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(run);
        when(runMapper
            .finishRunning(eq(100L), eq("FAILED"), any(), anyLong(), eq(501L), isNull(), isNull(), isNull(), eq("Runner 服务端鉴权令牌缺失")))
            .thenReturn(1);

        service.completeByReport(report);

        verify(notificationService).send(100L);
    }

    @Test
    void shouldPreferDispatchErrorOverReportFailureReason() {
        TestTimedTaskRunDO run = runningRun(100L);
        TestReportDO report = new TestReportDO();
        report.setId(501L);
        report.setStatus("FAILED");
        report.setRuntimeEnvironment(Map.of("dispatchError", "Runner 派发失败"));
        report.setStatisticAnalysis(Map.of("ui", Map.of("failureReason", "用例执行失败")));
        when(runMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(run);
        when(runMapper
            .finishRunning(eq(100L), eq("FAILED"), any(), anyLong(), eq(501L), isNull(), isNull(), isNull(), eq("Runner 派发失败")))
            .thenReturn(1);

        service.completeByReport(report);

        verify(notificationService).send(100L);
    }

    @Test
    void shouldNotNotifyWhenAnotherCallbackAlreadyFinishedRun() {
        TestTimedTaskRunDO run = runningRun(100L);
        TestReportDO report = new TestReportDO();
        report.setId(501L);
        report.setStatus("PASSED");
        when(runMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(run);
        when(runMapper
            .finishRunning(eq(100L), eq("PASSED"), any(), anyLong(), eq(501L), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(0);

        service.completeByReport(report);

        verify(notificationService, never()).send(any());
    }

    @Test
    void shouldRecoverOnlyStaleRunsWonByCurrentInstance() {
        TestTimedTaskRunDO first = runningRun(100L);
        TestTimedTaskRunDO second = runningRun(101L);
        when(runMapper.selectList(any())).thenReturn(List.of(first, second));
        when(runMapper
            .finishRunning(eq(100L), eq("FAILED"), any(), anyLong(), isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(1);
        when(runMapper
            .finishRunning(eq(101L), eq("FAILED"), any(), anyLong(), isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(0);

        int recovered = service.recoverStaleRuns();

        assertThat(recovered).isEqualTo(1);
        verify(notificationService).send(100L);
        verify(notificationService, never()).send(101L);
    }

    @Test
    void shouldLoadLatestRunsWithDatabaseSideQuery() {
        TestTimedTaskRunDO run = runningRun(100L);
        run.setTimedTaskId(1L);
        when(runMapper.selectLatestByTaskIds(List.of(1L, 2L))).thenReturn(List.of(run));

        var result = service.latestByTaskIds(List.of(1L, 2L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).getId()).isEqualTo(100L);
        verify(runMapper).selectLatestByTaskIds(List.of(1L, 2L));
    }

    @Test
    void shouldCleanupExpiredRunsInBoundedBatches() {
        when(runMapper.deleteExpiredRuns(any(), eq(100))).thenReturn(100, 40);

        int deleted = service.cleanupExpiredRuns(180, 100, 10);

        assertThat(deleted).isEqualTo(140);
        verify(runMapper, times(2)).deleteExpiredRuns(any(), eq(100));
    }

    private void assertSkipped(TestTimedTaskRunService.StartResult result, String reason) {
        assertThat(result.skipped()).isTrue();
        assertThat(result.run().getStatus()).isEqualTo("SKIPPED");
        assertThat(result.run().getEndTime()).isNotNull();
        assertThat(result.run().getFailureReason()).isEqualTo(reason);
        ArgumentCaptor<TestTimedTaskRunDO> captor = ArgumentCaptor.forClass(TestTimedTaskRunDO.class);
        verify(runMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SKIPPED");
        verify(notificationService).send(100L);
    }

    private TestTimedTaskDO task(String status) {
        TestTimedTaskDO task = new TestTimedTaskDO();
        task.setId(1L);
        task.setName("夜间回归");
        task.setTestPlanId(10L);
        task.setTestPlanName("回归计划");
        task.setStatus(status);
        task.setAllowConcurrent(0);
        task.setDelFlag(StatusTypeEnum.NORMAL);
        return task;
    }

    private TestPlanDO plan(StatusTypeEnum delFlag) {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(10L);
        plan.setDelFlag(delFlag);
        return plan;
    }

    private TestTimedTaskRunDO runningRun(Long id) {
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setId(id);
        run.setStatus("RUNNING");
        run.setStartTime(java.time.LocalDateTime.now().minusHours(25));
        return run;
    }
}
