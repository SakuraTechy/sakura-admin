package top.continew.admin.test.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.cron.pattern.CronPattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.json.JsonUtil;
import top.continew.admin.schedule.enums.JobBlockStrategyEnum;
import top.continew.admin.schedule.enums.JobRouteStrategyEnum;
import top.continew.admin.schedule.enums.JobStatusEnum;
import top.continew.admin.schedule.enums.JobTaskTypeEnum;
import top.continew.admin.schedule.enums.JobTriggerTypeEnum;
import top.continew.admin.schedule.model.query.JobLogQuery;
import top.continew.admin.schedule.model.query.JobQuery;
import top.continew.admin.schedule.model.req.JobReq;
import top.continew.admin.schedule.model.req.JobStatusReq;
import top.continew.admin.schedule.model.req.JobTriggerReq;
import top.continew.admin.schedule.model.resp.JobLogResp;
import top.continew.admin.schedule.model.resp.JobResp;
import top.continew.admin.schedule.service.JobLogService;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.job.TestPlanJobExecutor;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.query.TestTimedTaskQuery;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.model.req.TestTimedTaskReq;
import top.continew.admin.test.model.resp.TestTimedTaskDetailResp;
import top.continew.admin.test.model.resp.TestTimedTaskLogResp;
import top.continew.admin.test.model.resp.TestTimedTaskResp;
import top.continew.admin.test.service.TestTimedTaskService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.extension.crud.service.BaseServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TestTimedTaskServiceImpl extends BaseServiceImpl<TestTimedTaskMapper, TestTimedTaskDO, TestTimedTaskResp, TestTimedTaskDetailResp, TestTimedTaskQuery, TestTimedTaskReq> implements TestTimedTaskService {

    private static final String JOB_GROUP_NAME = "continew-admin";

    private final TestPlanMapper testPlanMapper;
    private final JobService jobService;
    private final JobLogService jobLogService;

    @Override
    public List<TestTimedTaskDetailResp> selectByIds(List<Long> ids) {
        return BeanUtil.copyToList(baseMapper.selectBatchIds(ids), TestTimedTaskDetailResp.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            TestTimedTaskDO task = baseMapper.selectById(id);
            if (task == null) {
                continue;
            }
            Long jobId = resolveScheduleJobId(task);
            if (jobId != null) {
                jobService.delete(jobId);
            }
            baseMapper.lambdaUpdate()
                .eq(TestTimedTaskDO::getId, id)
                .set(TestTimedTaskDO::getDelFlag, StatusTypeEnum.ABNORMAL)
                .update();
        }
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
        validateCron(req.getCronExpression());
        TestPlanDO plan = checkPlan(req.getTestPlanId());
        req.setTestPlanName(plan.getName());
        Long id = super.create(req);
        ensureScheduleJob(baseMapper.selectById(id));
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TestTimedTaskReq req, Long id) {
        validateCron(req.getCronExpression());
        TestPlanDO plan = checkPlan(req.getTestPlanId());
        req.setTestPlanName(plan.getName());
        super.update(req, id);
        ensureScheduleJob(baseMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        TestTimedTaskDO task = baseMapper.selectById(id);
        CheckUtils.throwIfNull(task, "测试定时任务不存在");
        baseMapper.lambdaUpdate().eq(TestTimedTaskDO::getId, id).set(TestTimedTaskDO::getStatus, status).update();
        Long jobId = resolveScheduleJobId(task);
        if (jobId == null) {
            return;
        }
        JobStatusReq req = new JobStatusReq();
        req.setJobStatus(toJobStatus(status));
        jobService.updateStatus(req, jobId);
        syncScheduleMetadata(baseMapper.selectById(id));
    }

    @Override
    public void trigger(Long id) {
        TestTimedTaskDO task = baseMapper.selectById(id);
        CheckUtils.throwIfNull(task, "测试定时任务不存在");
        Long jobId = resolveScheduleJobId(task);
        CheckUtils.throwIfNull(jobId, "调度任务不存在");
        JobTriggerReq req = new JobTriggerReq();
        req.setJobId(jobId);
        req.setTmpArgsStr(buildPayload(task));
        jobService.trigger(req);
    }

    @Override
    public PageResp<TestTimedTaskLogResp> pageLogs(Long id, Integer page, Integer size) {
        TestTimedTaskDO task = baseMapper.selectById(id);
        CheckUtils.throwIfNull(task, "测试定时任务不存在");
        Long jobId = resolveScheduleJobId(task);
        CheckUtils.throwIfNull(jobId, "调度任务不存在");

        JobLogQuery query = new JobLogQuery();
        query.setJobId(jobId);
        query.setPage(page == null ? 1 : page);
        query.setSize(size == null ? 10 : size);
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
                log.setTaskBatchStatus(item.getTaskBatchStatus() == null ? null : item.getTaskBatchStatus().name());
                log.setOperationReason(item.getOperationReason() == null ? null : item.getOperationReason().name());
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

    private void ensureScheduleJob(TestTimedTaskDO task) {
        if (task == null) {
            return;
        }
        JobReq req = buildJobReq(task);
        Long jobId = resolveScheduleJobId(task);
        if (jobId == null) {
            jobService.create(req);
        } else {
            jobService.update(req, jobId);
        }
        syncScheduleMetadata(task);
    }

    private void syncScheduleMetadata(TestTimedTaskDO task) {
        JobResp job = loadScheduleJob(task);
        if (job == null) {
            return;
        }
        baseMapper.lambdaUpdate()
            .eq(TestTimedTaskDO::getId, task.getId())
            .set(TestTimedTaskDO::getScheduleJobId, job.getId())
            .set(TestTimedTaskDO::getNextExecuteTime, job.getNextTriggerAt())
            .update();
    }

    private Long resolveScheduleJobId(TestTimedTaskDO task) {
        if (task == null) {
            return null;
        }
        if (task.getScheduleJobId() != null) {
            return task.getScheduleJobId();
        }
        JobResp job = loadScheduleJob(task);
        return job == null ? null : job.getId();
    }

    private JobResp loadScheduleJob(TestTimedTaskDO task) {
        if (task == null) {
            return null;
        }
        JobQuery query = new JobQuery();
        query.setGroupName(JOB_GROUP_NAME);
        query.setJobName(task.getName());
        query.setPage(1);
        query.setSize(20);
        PageResp<JobResp> result = jobService.page(query);
        if (result.getList() == null) {
            return null;
        }
        for (JobResp item : result.getList()) {
            if (Objects.equals(item.getId(), task.getScheduleJobId()) || Objects.equals(item.getJobName(), task.getName())) {
                return item;
            }
        }
        return null;
    }

    private JobReq buildJobReq(TestTimedTaskDO task) {
        JobReq req = new JobReq();
        req.setGroupName(JOB_GROUP_NAME);
        req.setJobName(task.getName());
        req.setDescription(task.getDescription());
        req.setTriggerType(JobTriggerTypeEnum.CRON);
        req.setTriggerInterval(task.getCronExpression());
        req.setExecutorType(1);
        req.setTaskType(JobTaskTypeEnum.CLUSTER);
        req.setExecutorInfo(TestPlanJobExecutor.EXECUTOR_NAME);
        req.setArgsStr(buildPayload(task));
        req.setArgsType(1);
        req.setRouteKey(JobRouteStrategyEnum.POLLING);
        req.setBlockStrategy(task.getAllowConcurrent() != null && task.getAllowConcurrent() == 1
            ? JobBlockStrategyEnum.PARALLEL
            : JobBlockStrategyEnum.DISCARD);
        req.setExecutorTimeout(3600);
        req.setMaxRetryTimes(0);
        req.setRetryInterval(0);
        req.setParallelNum(1);
        req.setJobStatus(toJobStatus(task.getStatus()));
        return req;
    }

    private String buildPayload(TestTimedTaskDO task) {
        TestTimedTaskExecutePayload payload = new TestTimedTaskExecutePayload();
        payload.setTaskId(task.getId());
        payload.setTestPlanId(task.getTestPlanId());
        payload.setProjectEnvironmentId(task.getProjectEnvironmentId());
        payload.setAutomationEnvironmentId(task.getAutomationEnvironmentId());
        payload.setExecuteName(task.getExecuteName());
        payload.setExecuteEmail(task.getExecuteEmail());
        try {
            return JsonUtil.marshal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("序列化定时任务参数失败", e);
        }
    }

    private JobStatusEnum toJobStatus(String status) {
        return "ENABLED".equalsIgnoreCase(status) ? JobStatusEnum.ENABLED : JobStatusEnum.DISABLED;
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
}
