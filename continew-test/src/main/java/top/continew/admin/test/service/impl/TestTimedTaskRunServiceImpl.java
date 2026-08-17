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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.model.query.TestTimedTaskRunQuery;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunSummaryResp;
import top.continew.admin.test.service.TestTimedTaskNotificationService;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.starter.core.exception.BusinessException;
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
    private final TestPlanMapper planMapper;
    private final TestTimedTaskRunMapper runMapper;
    private final TestReportMapper reportMapper;
    private final TestTimedTaskNotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartResult start(Long taskId, String triggerMode) {
        TestTimedTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException("测试定时任务不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String normalizedTriggerMode = normalizeTriggerMode(triggerMode);
        String rejectionReason = resolveRejectionReason(task, normalizedTriggerMode);
        if (rejectionReason != null) {
            TestTimedTaskRunDO skipped = createSkippedRun(task, normalizedTriggerMode, rejectionReason, now);
            runMapper.insert(skipped);
            notifyAfterCommit(skipped.getId());
            return new StartResult(skipped, true, task);
        }
        List<TestTimedTaskRunDO> activeRuns = runMapper.lambdaQuery()
            .eq(TestTimedTaskRunDO::getTimedTaskId, taskId)
            .eq(TestTimedTaskRunDO::getStatus, "RUNNING")
            .eq(TestTimedTaskRunDO::getDelFlag, StatusTypeEnum.NORMAL)
            .orderByAsc(TestTimedTaskRunDO::getStartTime)
            .list();
        boolean hasActiveRun = false;
        for (TestTimedTaskRunDO activeRun : activeRuns) {
            if (activeRun.getStartTime() != null && activeRun.getStartTime().isBefore(now.minusHours(STALE_HOURS))) {
                if (finishRunning(activeRun, "FAILED", "超过 24 小时未收到测试结果回传", now)) {
                    notifyAfterCommit(activeRun.getId());
                }
            } else {
                hasActiveRun = true;
            }
        }
        if (hasActiveRun && !Integer.valueOf(1).equals(task.getAllowConcurrent())) {
            TestTimedTaskRunDO skipped = createSkippedRun(task, normalizedTriggerMode, "上一次执行尚未结束", now);
            runMapper.insert(skipped);
            notifyAfterCommit(skipped.getId());
            return new StartResult(skipped, true, task);
        }
        TestTimedTaskRunDO run = createRun(task, normalizedTriggerMode, now);
        runMapper.insert(run);
        return new StartResult(run, false, task);
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
        if (run == null) {
            return;
        }
        if (finishRunning(run, "FAILED", reason, LocalDateTime.now())) {
            notifyAfterCommit(runId);
        }
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
        String terminalStatus = normalizeTerminalStatus(report.getStatus());
        run.setStatus(terminalStatus);
        run.setRunTime(report.getRunTime() == null ? duration(run.getStartTime(), now) : report.getRunTime());
        run.setTestReportId(report.getId());
        run.setBuildNumber(report.getBuildNumber());
        run.setConsoleUrl(report.getConsoleUrl());
        run.setReportUrl(report.getReportUrl());
        if ("FAILED".equals(terminalStatus)) {
            run.setFailureReason(resolveFailureReason(report));
        } else if ("CANCELLED".equals(terminalStatus)) {
            run.setFailureReason("测试计划执行已取消");
        }
        if (finishRunning(run, terminalStatus, run.getFailureReason(), now)) {
            notifyAfterCommit(run.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverStaleRuns() {
        LocalDateTime now = LocalDateTime.now();
        List<TestTimedTaskRunDO> staleRuns = runMapper.selectList(Wrappers.<TestTimedTaskRunDO>query()
            .eq("status", "RUNNING")
            .eq("del_flag", StatusTypeEnum.NORMAL)
            .and(wrapper -> wrapper.isNull("start_time").or().lt("start_time", now.minusHours(STALE_HOURS)))
            .orderByAsc("start_time")
            .last("LIMIT 200"));
        int recovered = 0;
        for (TestTimedTaskRunDO run : staleRuns) {
            if (finishRunning(run, "FAILED", "超过 24 小时未收到测试结果回传", now)) {
                recovered++;
                notifyAfterCommit(run.getId());
            }
        }
        return recovered;
    }

    @Override
    public int cleanupExpiredRuns(int retentionDays, int batchSize, int maxBatches) {
        int normalizedRetentionDays = Math.max(1, Math.min(retentionDays, 3650));
        int normalizedBatchSize = Math.max(10, Math.min(batchSize, 5000));
        int normalizedMaxBatches = Math.max(1, Math.min(maxBatches, 100));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(normalizedRetentionDays);
        int total = 0;
        for (int batch = 0; batch < normalizedMaxBatches; batch++) {
            int deleted = runMapper.deleteExpiredRuns(cutoff, normalizedBatchSize);
            total += deleted;
            if (deleted < normalizedBatchSize) {
                break;
            }
        }
        return total;
    }

    @Override
    public PageResp<TestTimedTaskRunResp> page(Long taskId, TestTimedTaskRunQuery query, PageQuery pageQuery) {
        Page<TestTimedTaskRunDO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Page<TestTimedTaskRunDO> result = runMapper
            .selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestTimedTaskRunDO>()
                .eq(TestTimedTaskRunDO::getTimedTaskId, taskId)
                .eq(TestTimedTaskRunDO::getDelFlag, StatusTypeEnum.NORMAL)
                .eq(query != null && query.getRunId() != null, TestTimedTaskRunDO::getId, query == null
                    ? null
                    : query.getRunId())
                .eq(query != null && query.getTestReportId() != null, TestTimedTaskRunDO::getTestReportId, query == null
                    ? null
                    : query.getTestReportId())
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
        List<TestTimedTaskRunDO> runs = runMapper.selectLatestByTaskIds(taskIds);
        for (TestTimedTaskRunDO run : runs) {
            result.put(run.getTimedTaskId(), BeanUtil.copyProperties(run, TestTimedTaskRunSummaryResp.class));
        }
        return result;
    }

    private TestTimedTaskRunDO createRun(TestTimedTaskDO task, String triggerMode, LocalDateTime now) {
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setTimedTaskId(task.getId());
        run.setTaskName(task.getName());
        run.setTestPlanId(task.getTestPlanId());
        run.setTestPlanName(task.getTestPlanName());
        run.setTriggerMode(normalizeTriggerMode(triggerMode));
        run.setStatus("RUNNING");
        run.setNotificationEmails(task.getNotificationEmails());
        run.setStartTime(now);
        run.setRunTime(0L);
        run.setNotificationStatus("PENDING");
        return run;
    }

    private TestTimedTaskRunDO createSkippedRun(TestTimedTaskDO task,
                                                String triggerMode,
                                                String reason,
                                                LocalDateTime now) {
        TestTimedTaskRunDO run = createRun(task, triggerMode, now);
        run.setStatus("SKIPPED");
        run.setEndTime(now);
        run.setFailureReason(reason);
        return run;
    }

    private String resolveRejectionReason(TestTimedTaskDO task, String triggerMode) {
        if (!StatusTypeEnum.NORMAL.equals(task.getDelFlag())) {
            return "测试定时任务已删除";
        }
        if ("DELETING".equalsIgnoreCase(task.getStatus())) {
            return "测试定时任务正在删除";
        }
        if ("SCHEDULE".equals(triggerMode) && !"ENABLED".equalsIgnoreCase(task.getStatus())) {
            return "测试定时任务已禁用";
        }
        TestPlanDO plan = planMapper.selectById(task.getTestPlanId());
        if (plan == null || !StatusTypeEnum.NORMAL.equals(plan.getDelFlag())) {
            return "测试计划不存在或已删除";
        }
        return null;
    }

    private String normalizeTriggerMode(String triggerMode) {
        return "MANUAL".equalsIgnoreCase(triggerMode) ? "MANUAL" : "SCHEDULE";
    }

    private boolean finishRunning(TestTimedTaskRunDO run, String status, String reason, LocalDateTime endTime) {
        long runTime = run.getRunTime() == null || run.getRunTime() <= 0
            ? duration(run.getStartTime(), endTime)
            : run.getRunTime();
        return runMapper.finishRunning(run.getId(), status, endTime, runTime, run.getTestReportId(), run
            .getBuildNumber(), run.getConsoleUrl(), run.getReportUrl(), reason) == 1;
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
        return "PASSED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status) || "CANCELLED"
            .equalsIgnoreCase(status);
    }

    private String normalizeTerminalStatus(String status) {
        if ("PASSED".equalsIgnoreCase(status)) {
            return "PASSED";
        }
        return "CANCELLED".equalsIgnoreCase(status) ? "CANCELLED" : "FAILED";
    }

    private String resolveFailureReason(TestReportDO report) {
        String dispatchError = mapText(report.getRuntimeEnvironment(), "dispatchError");
        String reportFailure = null;
        if (report.getStatisticAnalysis() != null) {
            Object ui = report.getStatisticAnalysis().get("ui");
            if (ui instanceof Map<?, ?> uiStatistic) {
                reportFailure = mapText(uiStatistic, "failureReason");
            }
        }
        return StringUtils.abbreviate(StringUtils.firstNonBlank(dispatchError, reportFailure, "测试计划执行失败"), 1000);
    }

    private String mapText(Map<?, ?> values, String key) {
        if (values == null || values.get(key) == null) {
            return null;
        }
        String value = String.valueOf(values.get(key)).trim();
        return StringUtils.isBlank(value) ? null : value;
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
