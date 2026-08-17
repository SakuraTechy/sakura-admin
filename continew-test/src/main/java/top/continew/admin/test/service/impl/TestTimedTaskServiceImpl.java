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
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.cron.pattern.CronPattern;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.automation.mapper.AutomationEnvironmentConfigMapper;
import top.continew.admin.automation.model.entity.AutomationEnvironmentConfigDO;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.json.JsonUtil;
import top.continew.admin.common.regex.RegexUtil;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.schedule.model.query.JobLogQuery;
import top.continew.admin.schedule.model.req.JobTriggerReq;
import top.continew.admin.schedule.model.resp.JobLogResp;
import top.continew.admin.schedule.service.JobLogService;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;
import top.continew.admin.test.model.query.TestTimedTaskQuery;
import top.continew.admin.test.model.query.TestTimedTaskLogQuery;
import top.continew.admin.test.model.query.TestTimedTaskRunQuery;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.model.req.TestTimedTaskReq;
import top.continew.admin.test.model.resp.TestTimedTaskDetailResp;
import top.continew.admin.test.model.resp.TestTimedTaskLogResp;
import top.continew.admin.test.model.resp.TestTimedTaskResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunSummaryResp;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.admin.test.service.TestTimedTaskScheduleSyncService;
import top.continew.admin.test.service.TestTimedTaskService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.extension.crud.service.BaseServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestTimedTaskServiceImpl extends BaseServiceImpl<TestTimedTaskMapper, TestTimedTaskDO, TestTimedTaskResp, TestTimedTaskDetailResp, TestTimedTaskQuery, TestTimedTaskReq> implements TestTimedTaskService {

    private static final String TASK_TYPE = "PLAN";
    private static final String MISFIRE_POLICY = "DO_NOTHING";

    private final TestPlanMapper testPlanMapper;
    private final ProjectEnvironmentConfigMapper projectEnvironmentMapper;
    private final AutomationEnvironmentConfigMapper automationEnvironmentMapper;
    private final JobService jobService;
    private final JobLogService jobLogService;
    private final TestTimedTaskRunService runService;
    private final TestTimedTaskScheduleSyncService scheduleSyncService;

    @Override
    public PageResp<TestTimedTaskResp> page(TestTimedTaskQuery query, PageQuery pageQuery) {
        QueryWrapper<TestTimedTaskDO> queryWrapper = buildQueryWrapper(query);
        if (query.getProjectId() != null && query.getTestPlanId() == null) {
            List<Long> planIds = testPlanMapper.lambdaQuery()
                .eq(TestPlanDO::getProjectId, query.getProjectId())
                .eq(TestPlanDO::getDelFlag, StatusTypeEnum.NORMAL)
                .list()
                .stream()
                .map(TestPlanDO::getId)
                .toList();
            if (planIds.isEmpty()) {
                PageResp<TestTimedTaskResp> empty = new PageResp<>();
                empty.setList(List.of());
                empty.setTotal(0);
                return empty;
            }
            queryWrapper.in("test_plan_id", planIds);
        }
        super.sort(queryWrapper, pageQuery);
        IPage<TestTimedTaskDO> page = baseMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery
            .getSize()), queryWrapper);
        PageResp<TestTimedTaskResp> resp = PageResp.build(page, super.getListClass());
        enrich(resp.getList());
        return resp;
    }

    @Override
    public List<TestTimedTaskDetailResp> selectByIds(List<Long> ids) {
        return BeanUtil.copyToList(baseMapper.selectBatchIds(ids), TestTimedTaskDetailResp.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        List<Long> syncTaskIds = new ArrayList<>();
        for (Long id : ids) {
            TestTimedTaskDO task = baseMapper.selectByIdForUpdate(id);
            if (task == null) {
                continue;
            }
            long version = nextSyncVersion(task);
            baseMapper.update(null, Wrappers.<TestTimedTaskDO>update()
                .eq("id", id)
                .set("status", "DELETING")
                .set("schedule_sync_status", "DELETING")
                .set("schedule_sync_error", null)
                .set("schedule_sync_version", version)
                .set("schedule_sync_retry_count", 0)
                .set("schedule_sync_next_retry_time", LocalDateTime.now()));
            syncTaskIds.add(id);
        }
        syncAfterCommit(syncTaskIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByPlanIds(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return;
        }
        List<Long> taskIds = baseMapper.lambdaQuery()
            .select(TestTimedTaskDO::getId)
            .in(TestTimedTaskDO::getTestPlanId, planIds)
            .eq(TestTimedTaskDO::getDelFlag, StatusTypeEnum.NORMAL)
            .list()
            .stream()
            .map(TestTimedTaskDO::getId)
            .toList();
        deleteByIds(taskIds);
    }

    @Override
    public boolean isExists(String name, Long planId, Long id) {
        return baseMapper.lambdaQuery()
            .eq(TestTimedTaskDO::getTestPlanId, planId)
            .eq(TestTimedTaskDO::getName, name)
            .eq(TestTimedTaskDO::getDelFlag, StatusTypeEnum.NORMAL)
            .ne(id != null, TestTimedTaskDO::getId, id)
            .exists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TestTimedTaskReq req) {
        TestPlanDO plan = checkPlan(req.getTestPlanId());
        normalizeAndValidate(req, plan);
        req.setTestPlanName(plan.getName());
        req.setType(TASK_TYPE);
        req.setMisfirePolicy(MISFIRE_POLICY);
        req.setStatus("DISABLED");
        if (CharSequenceUtil.isBlank(req.getExecuteName())) {
            req.setExecuteName(UserContextHolder.getNickname());
        }
        req.setExecuteEmail(req.getNotificationEmails().get(0));
        Long id = super.create(req);
        syncAfterCommit(List.of(id));
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TestTimedTaskReq req, Long id) {
        TestTimedTaskDO existing = baseMapper.selectById(id);
        CheckUtils.throwIfNull(existing, "测试定时任务不存在");
        TestPlanDO plan = checkPlan(req.getTestPlanId());
        normalizeAndValidate(req, plan);
        req.setTestPlanName(plan.getName());
        req.setType(TASK_TYPE);
        req.setMisfirePolicy(MISFIRE_POLICY);
        req.setStatus(existing.getStatus());
        req.setExecuteName(CharSequenceUtil.blankToDefault(existing.getExecuteName(), UserContextHolder.getNickname()));
        req.setExecuteEmail(req.getNotificationEmails().get(0));
        super.update(req, id);
        markPending(id, nextSyncVersion(existing));
        syncAfterCommit(List.of(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        CheckUtils.throwIf(!List.of("ENABLED", "DISABLED").contains(status), "定时任务状态无效");
        TestTimedTaskDO task = baseMapper.selectById(id);
        CheckUtils.throwIfNull(task, "测试定时任务不存在");
        if ("ENABLED".equals(status)) {
            validateExecutable(task);
        }
        baseMapper.lambdaUpdate().eq(TestTimedTaskDO::getId, id).set(TestTimedTaskDO::getStatus, status).update();
        markPending(id, nextSyncVersion(task));
        syncAfterCommit(List.of(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retrySync(Long id) {
        TestTimedTaskDO task = baseMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BusinessException("测试定时任务不存在");
        }
        String syncStatus = "DELETING".equalsIgnoreCase(task.getStatus()) ? "DELETING" : "PENDING";
        baseMapper.update(null, Wrappers.<TestTimedTaskDO>update()
            .eq("id", id)
            .set("schedule_sync_status", syncStatus)
            .set("schedule_sync_error", null)
            .set("schedule_sync_version", nextSyncVersion(task))
            .set("schedule_sync_retry_count", 0)
            .set("schedule_sync_next_retry_time", LocalDateTime.now()));
        syncAfterCommit(List.of(id));
    }

    @Override
    public void trigger(Long id) {
        TestTimedTaskDO task = baseMapper.selectById(id);
        CheckUtils.throwIfNull(task, "测试定时任务不存在");
        validateExecutable(task);
        scheduleSyncService.syncNow(id);
        task = baseMapper.selectById(id);
        Long jobId = task == null ? null : task.getScheduleJobId();
        CheckUtils.throwIfNull(jobId, "调度任务不存在");
        JobTriggerReq req = new JobTriggerReq();
        req.setJobId(jobId);
        req.setTmpArgsStr(buildPayload(task, "MANUAL"));
        CheckUtils.throwIf(!jobService.trigger(req), "定时任务触发失败，请检查调度服务");
    }

    @Override
    public PageResp<TestTimedTaskLogResp> pageLogs(Long id, TestTimedTaskLogQuery logQuery, PageQuery pageQuery) {
        TestTimedTaskDO task = baseMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("测试定时任务不存在");
        }
        if (task.getScheduleJobId() == null) {
            scheduleSyncService.syncNow(id);
            task = baseMapper.selectById(id);
        }
        Long jobId = task == null ? null : task.getScheduleJobId();
        if (jobId == null) {
            throw new BusinessException("调度任务不存在");
        }
        JobLogQuery query = new JobLogQuery();
        query.setJobId(jobId);
        query.setPage(pageQuery.getPage());
        query.setSize(pageQuery.getSize());
        if (logQuery != null) {
            query.setTaskBatchStatus(logQuery.getTaskBatchStatus());
            if (logQuery.getStartTime() != null && logQuery.getEndTime() != null) {
                query.setDatetimeRange(new LocalDateTime[] {logQuery.getStartTime(), logQuery.getEndTime()});
            }
        }
        PageResp<JobLogResp> result = jobLogService.page(query);
        PageResp<TestTimedTaskLogResp> resp = new PageResp<>();
        List<TestTimedTaskLogResp> logs = new ArrayList<>();
        if (result.getList() != null) {
            for (JobLogResp item : result.getList()) {
                TestTimedTaskLogResp log = new TestTimedTaskLogResp();
                log.setId(item.getId());
                log.setJobId(item.getJobId());
                log.setGroupName(item.getGroupName());
                log.setJobName(item.getJobName());
                log.setTaskBatchStatus(item.getTaskBatchStatus() == null ? null : item.getTaskBatchStatus().getValue());
                log.setOperationReason(item.getOperationReason() == null ? null : item.getOperationReason().getValue());
                log.setExecutorType(item.getExecutorType());
                log.setExecutorInfo(item.getExecutorInfo());
                log.setExecutionAt(item.getExecutionAt());
                log.setCreateDt(item.getCreateDt());
                logs.add(log);
            }
        }
        resp.setList(logs);
        resp.setTotal(result.getTotal());
        return resp;
    }

    @Override
    public PageResp<TestTimedTaskRunResp> pageRuns(Long id, TestTimedTaskRunQuery query, PageQuery pageQuery) {
        CheckUtils.throwIfNull(baseMapper.selectById(id), "测试定时任务不存在");
        return runService.page(id, query, pageQuery);
    }

    private void normalizeAndValidate(TestTimedTaskReq req, TestPlanDO plan) {
        validateCron(req.getCronExpression());
        req.setAllowConcurrent(Integer.valueOf(1).equals(req.getAllowConcurrent()) ? 1 : 0);
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (req.getNotificationEmails() != null) {
            req.getNotificationEmails()
                .stream()
                .filter(CharSequenceUtil::isNotBlank)
                .map(String::trim)
                .map(String::toLowerCase)
                .forEach(emails::add);
        }
        CheckUtils.throwIf(emails.isEmpty(), "至少配置一个通知邮箱");
        CheckUtils.throwIf(emails.size() > 20, "通知邮箱不能超过 20 个");
        CheckUtils.throwIf(emails.stream().anyMatch(email -> !RegexUtil.isEmail(email)), "通知邮箱格式不正确");
        req.setNotificationEmails(new ArrayList<>(emails));
        TestExecutionEngineEnum engine = req.getExecutionEngine() == null
            ? TestExecutionEngineEnum.SELENIUM
            : req.getExecutionEngine();
        CheckUtils.throwIf(TestExecutionEngineEnum.CHROME_DEVTOOLS_PROTOCOL.equals(engine), "CDP 依赖浏览器会话，不支持无人值守定时执行");
        req.setExecutionEngine(engine);
        validateEnvironment(plan, req.getProjectEnvironmentId(), req.getAutomationEnvironmentId(), engine);
    }

    private void validateExecutable(TestTimedTaskDO task) {
        TestPlanDO plan = checkPlan(task.getTestPlanId());
        CheckUtils.throwIf(plan.getUiTestScene() == null || plan.getUiTestScene().isEmpty(), "测试计划未关联 UI 场景，无法启用或执行");
        validateCron(task.getCronExpression());
        CheckUtils.throwIf(task.getNotificationEmails() == null || task.getNotificationEmails().isEmpty(), "请先配置通知邮箱");
        TestExecutionEngineEnum engine = parseEngine(task.getExecutionEngine());
        CheckUtils.throwIf(TestExecutionEngineEnum.CHROME_DEVTOOLS_PROTOCOL.equals(engine), "CDP 依赖浏览器会话，不支持无人值守定时执行");
        validateEnvironment(plan, task.getProjectEnvironmentId(), task.getAutomationEnvironmentId(), engine);
    }

    private void validateEnvironment(TestPlanDO plan,
                                     Long projectEnvironmentId,
                                     Long automationEnvironmentId,
                                     TestExecutionEngineEnum engine) {
        CheckUtils.throwIfNull(projectEnvironmentId, "项目环境不能为空");
        ProjectEnvironmentConfigDO projectEnvironment = projectEnvironmentMapper.selectById(projectEnvironmentId);
        CheckUtils.throwIfNull(projectEnvironment, "项目环境不存在");
        CheckUtils.throwIf(!Objects.equals(projectEnvironment.getProjectId(), plan.getProjectId()), "项目环境不属于测试计划所在项目");
        CheckUtils.throwIf(!DisEnableStatusEnum.ENABLE.equals(projectEnvironment.getStatus()), "项目环境未启用");
        if (!TestExecutionEngineEnum.SELENIUM.equals(engine)) {
            return;
        }
        CheckUtils.throwIfNull(automationEnvironmentId, "自动化环境不能为空");
        AutomationEnvironmentConfigDO automationEnvironment = automationEnvironmentMapper
            .selectById(automationEnvironmentId);
        CheckUtils.throwIfNull(automationEnvironment, "自动化环境不存在");
        CheckUtils.throwIf(!StatusTypeEnum.ENABLE.equals(automationEnvironment.getStatus()), "自动化环境未启用");
    }

    private String buildPayload(TestTimedTaskDO task, String triggerMode) {
        TestTimedTaskExecutePayload payload = new TestTimedTaskExecutePayload();
        payload.setTaskId(task.getId());
        payload.setTriggerMode(triggerMode);
        if ("MANUAL".equals(triggerMode)) {
            payload.setExecuteName(CharSequenceUtil.blankToDefault(UserContextHolder.getNickname(), task
                .getExecuteName()));
            payload.setExecuteEmail(task.getExecuteEmail());
        }
        try {
            return JsonUtil.marshal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("序列化定时任务参数失败", e);
        }
    }

    private void markPending(Long id, long version) {
        baseMapper.update(null, Wrappers.<TestTimedTaskDO>update()
            .eq("id", id)
            .set("schedule_sync_status", "PENDING")
            .set("schedule_sync_error", null)
            .set("schedule_sync_version", version)
            .set("schedule_sync_retry_count", 0)
            .set("schedule_sync_next_retry_time", LocalDateTime.now()));
    }

    private long nextSyncVersion(TestTimedTaskDO task) {
        return (task.getScheduleSyncVersion() == null ? 0L : task.getScheduleSyncVersion()) + 1L;
    }

    private void syncAfterCommit(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        Runnable syncAction = () -> taskIds.forEach(scheduleSyncService::submit);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncAction.run();
            }
        });
    }

    private TestPlanDO checkPlan(Long planId) {
        TestPlanDO plan = testPlanMapper.selectById(planId);
        CheckUtils.throwIfNull(plan, "测试计划不存在");
        return plan;
    }

    private void validateCron(String cronExpression) {
        try {
            CronPattern.of(cronExpression);
        } catch (Exception ex) {
            CheckUtils.throwIf(true, "Cron 表达式不合法");
        }
    }

    private TestExecutionEngineEnum parseEngine(String engine) {
        try {
            return CharSequenceUtil.isBlank(engine)
                ? TestExecutionEngineEnum.SELENIUM
                : TestExecutionEngineEnum.valueOf(engine);
        } catch (Exception e) {
            return TestExecutionEngineEnum.SELENIUM;
        }
    }

    private void enrich(List<TestTimedTaskResp> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        List<Long> planIds = tasks.stream()
            .map(TestTimedTaskResp::getTestPlanId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, TestPlanDO> planMap = planIds.isEmpty()
            ? Collections.emptyMap()
            : testPlanMapper.selectBatchIds(planIds)
                .stream()
                .collect(Collectors.toMap(TestPlanDO::getId, Function.identity()));

        List<Long> projectEnvironmentIds = tasks.stream()
            .map(TestTimedTaskResp::getProjectEnvironmentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, ProjectEnvironmentConfigDO> projectEnvironmentMap = projectEnvironmentIds.isEmpty()
            ? Collections.emptyMap()
            : projectEnvironmentMapper.selectBatchIds(projectEnvironmentIds)
                .stream()
                .collect(Collectors.toMap(ProjectEnvironmentConfigDO::getId, Function.identity()));

        List<Long> automationEnvironmentIds = tasks.stream()
            .map(TestTimedTaskResp::getAutomationEnvironmentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, AutomationEnvironmentConfigDO> automationEnvironmentMap = automationEnvironmentIds.isEmpty()
            ? Collections.emptyMap()
            : automationEnvironmentMapper.selectBatchIds(automationEnvironmentIds)
                .stream()
                .collect(Collectors.toMap(AutomationEnvironmentConfigDO::getId, Function.identity()));
        Map<Long, TestTimedTaskRunSummaryResp> lastRuns = runService.latestByTaskIds(tasks.stream()
            .map(TestTimedTaskResp::getId)
            .toList());
        for (TestTimedTaskResp task : tasks) {
            TestPlanDO plan = planMap.get(task.getTestPlanId());
            if (plan != null) {
                task.setProjectId(plan.getProjectId());
                task.setProjectName(plan.getProjectName());
            }
            ProjectEnvironmentConfigDO projectEnvironment = projectEnvironmentMap.get(task.getProjectEnvironmentId());
            task.setProjectEnvironmentName(projectEnvironment == null ? null : projectEnvironment.getName());
            AutomationEnvironmentConfigDO automationEnvironment = automationEnvironmentMap.get(task
                .getAutomationEnvironmentId());
            task.setAutomationEnvironmentName(automationEnvironment == null ? null : automationEnvironment.getName());
            task.setLastRun(lastRuns.get(task.getId()));
            task.setCreateUserString(UserContextHolder.getNickname(task.getCreateUser()));
            task.setUpdateUserString(UserContextHolder.getNickname(task.getUpdateUser()));
        }
    }
}
