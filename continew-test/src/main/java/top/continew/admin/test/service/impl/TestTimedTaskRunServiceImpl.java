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

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.model.query.TestTimedTaskRunQuery;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunSummaryResp;
import top.continew.admin.test.service.TestTimedTaskNotificationService;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestTimedTaskRunServiceImpl implements TestTimedTaskRunService {

    private static final long STALE_HOURS = 24;

    private final TestTimedTaskMapper taskMapper;
    private final TestTimedTaskRunMapper runMapper;
    private final TestReportMapper reportMapper;
    private final TestTimedTaskNotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartResult start(Long taskId, String triggerMode) {
        TestTimedTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        CheckUtils.throwIfNull(task, "测试定时任务不存在");
        LocalDateTime now = LocalDateTime.now();
        List<TestTimedTaskRunDO> activeRuns = runMapper.lambdaQuery()
            .eq(TestTimedTaskRunDO::getTimedTaskId, taskId)
            .eq(TestTimedTaskRunDO::getStatus, "RUNNING")
            .eq(TestTimedTaskRunDO::getDelFlag, StatusTypeEnum.NORMAL)
            .orderByAsc(TestTimedTaskRunDO::getStartTime)
            .list();
        boolean hasActiveRun = false;
        for (TestTimedTaskRunDO activeRun : activeRuns) {
            if (activeRun.getStartTime() != null && activeRun.getStartTime().isBefore(now.minusHours(STALE_HOURS))) {
                finish(activeRun, "FAILED", "超过 24 小时未收到测试结果回传", now);
                notifyAfterCommit(activeRun.getId());
            } else {
                hasActiveRun = true;
            }
        }
        if (hasActiveRun && !Integer.valueOf(1).equals(task.getAllowConcurrent())) {
            TestTimedTaskRunDO skipped = createRun(task, triggerMode, now);
            skipped.setStatus("SKIPPED");
            skipped.setEndTime(now);
            skipped.setFailureReason("上一次执行尚未结束");
            runMapper.insert(skipped);
            notifyAfterCommit(skipped.getId());
            return new StartResult(skipped, true);
        }
        TestTimedTaskRunDO run = createRun(task, triggerMode, now);
        runMapper.insert(run);
        return new StartResult(run, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void attachExecution(Long runId, TestPlanExecuteResp executeResp) {
        if (runId == null || executeResp == null) {
            return;
        }
        Long reportId = parseId(executeResp.getTestReportId());
        runMapper.lambdaUpdate()
            .eq(TestTimedTaskRunDO::getId, runId)
            .set(TestTimedTaskRunDO::getTestReportId, reportId)
            .set(TestTimedTaskRunDO::getBuildNumber, executeResp.getBuildNumber() == null
                ? null
                : String.valueOf(executeResp.getBuildNumber()))
            .set(TestTimedTaskRunDO::getConsoleUrl, executeResp.getConsoleUrl())
            .set(TestTimedTaskRunDO::getReportUrl, executeResp.getTestReportUrl())
            .update();
        TestReportDO report = reportId == null ? null : reportMapper.selectById(reportId);
        if (report != null && isTerminal(report.getStatus())) {
            completeByReport(report);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fail(Long runId, String reason) {
        TestTimedTaskRunDO run = runMapper.selectById(runId);
        if (run == null || !"RUNNING".equals(run.getStatus())) {
            return;
        }
        finish(run, "FAILED", reason, LocalDateTime.now());
        notifyAfterCommit(runId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeByReport(TestReportDO report) {
        if (report == null || report.getId() == null || !isTerminal(report.getStatus())) {
            return;
        }
        TestTimedTaskRunDO run = runMapper.lambdaQuery()
            .eq(TestTimedTaskRunDO::getTestReportId, report.getId())
            .eq(TestTimedTaskRunDO::getStatus, "RUNNING")
            .orderByDesc(TestTimedTaskRunDO::getStartTime)
            .one();
        if (run == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        run.setStatus("PASSED".equalsIgnoreCase(report.getStatus()) ? "PASSED" : "FAILED");
        run.setEndTime(now);
        run.setRunTime(report.getRunTime() == null ? duration(run.getStartTime(), now) : report.getRunTime());
        run.setBuildNumber(report.getBuildNumber());
        run.setConsoleUrl(report.getConsoleUrl());
        run.setReportUrl(report.getReportUrl());
        if ("FAILED".equals(run.getStatus()) && report.getRuntimeEnvironment() != null) {
            Object error = report.getRuntimeEnvironment().get("dispatchError");
            run.setFailureReason(error == null ? "测试计划执行失败" : String.valueOf(error));
        }
        runMapper.updateById(run);
        notifyAfterCommit(run.getId());
    }

    @Override
    public PageResp<TestTimedTaskRunResp> page(Long taskId, TestTimedTaskRunQuery query, PageQuery pageQuery) {
        Page<TestTimedTaskRunDO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Page<TestTimedTaskRunDO> result = runMapper
            .selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestTimedTaskRunDO>()
                .eq(TestTimedTaskRunDO::getTimedTaskId, taskId)
                .eq(TestTimedTaskRunDO::getDelFlag, StatusTypeEnum.NORMAL)
                .eq(query != null && query.getStatus() != null, TestTimedTaskRunDO::getStatus, query == null
                    ? null
                    : query.getStatus())
                .eq(query != null && query.getTriggerMode() != null, TestTimedTaskRunDO::getTriggerMode, query == null
                    ? null
                    : query.getTriggerMode())
                .ge(query != null && query.getStartTime() != null, TestTimedTaskRunDO::getStartTime, query == null
                    ? null
                    : query.getStartTime())
                .le(query != null && query.getEndTime() != null, TestTimedTaskRunDO::getStartTime, query == null
                    ? null
                    : query.getEndTime())
                .orderByDesc(TestTimedTaskRunDO::getStartTime));
        PageResp<TestTimedTaskRunResp> resp = new PageResp<>();
        resp.setList(BeanUtil.copyToList(result.getRecords(), TestTimedTaskRunResp.class));
        resp.setTotal(result.getTotal());
        return resp;
    }

    @Override
    public Map<Long, TestTimedTaskRunSummaryResp> latestByTaskIds(Collection<Long> taskIds) {
        Map<Long, TestTimedTaskRunSummaryResp> result = new LinkedHashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return result;
        }
        List<TestTimedTaskRunDO> runs = runMapper.lambdaQuery()
            .in(TestTimedTaskRunDO::getTimedTaskId, taskIds)
            .eq(TestTimedTaskRunDO::getDelFlag, StatusTypeEnum.NORMAL)
            .orderByDesc(TestTimedTaskRunDO::getStartTime)
            .list();
        for (TestTimedTaskRunDO run : runs) {
            result.computeIfAbsent(run.getTimedTaskId(), key -> BeanUtil
                .copyProperties(run, TestTimedTaskRunSummaryResp.class));
        }
        return result;
    }

    private TestTimedTaskRunDO createRun(TestTimedTaskDO task, String triggerMode, LocalDateTime now) {
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setTimedTaskId(task.getId());
        run.setTaskName(task.getName());
        run.setTestPlanId(task.getTestPlanId());
        run.setTestPlanName(task.getTestPlanName());
        run.setTriggerMode("MANUAL".equalsIgnoreCase(triggerMode) ? "MANUAL" : "SCHEDULE");
        run.setStatus("RUNNING");
        run.setNotificationEmails(task.getNotificationEmails());
        run.setStartTime(now);
        run.setRunTime(0L);
        run.setNotificationStatus("PENDING");
        return run;
    }

    private void finish(TestTimedTaskRunDO run, String status, String reason, LocalDateTime endTime) {
        run.setStatus(status);
        run.setEndTime(endTime);
        run.setRunTime(duration(run.getStartTime(), endTime));
        run.setFailureReason(reason);
        runMapper.updateById(run);
    }

    private long duration(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime == null ? 0 : Math.max(0, Duration.between(startTime, endTime).toMillis());
    }

    private Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isTerminal(String status) {
        return "PASSED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }

    private void notifyAfterCommit(Long runId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationService.send(runId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.send(runId);
            }
        });
    }
}
