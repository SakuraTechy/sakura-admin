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

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.continew.admin.schedule.enums.JobBlockStrategyEnum;
import top.continew.admin.schedule.enums.JobRouteStrategyEnum;
import top.continew.admin.schedule.enums.JobStatusEnum;
import top.continew.admin.schedule.enums.JobTaskTypeEnum;
import top.continew.admin.schedule.enums.JobTriggerTypeEnum;
import top.continew.admin.schedule.model.query.JobQuery;
import top.continew.admin.schedule.model.req.JobReq;
import top.continew.admin.schedule.model.resp.JobResp;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.job.TestPlanJobExecutor;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.service.TestTimedTaskScheduleSyncService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.json.JsonUtil;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestTimedTaskScheduleSyncServiceImpl implements TestTimedTaskScheduleSyncService {

    private static final String JOB_GROUP_NAME = "continew-admin";
    private static final String JOB_NAME_PREFIX = "test-plan-task-";
    private static final int RECONCILE_BATCH_SIZE = 200;

    private final TestTimedTaskMapper taskMapper;
    private final JobService jobService;

    @Override
    public void syncNow(Long taskId) {
        TestTimedTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        long version = task.getScheduleSyncVersion() == null ? 1L : task.getScheduleSyncVersion();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.claimScheduleSync(taskId, version, now, now.minusMinutes(10)) != 1) {
            TestTimedTaskDO latest = taskMapper.selectById(taskId);
            if (latest == null || "SYNCED".equals(latest.getScheduleSyncStatus())) {
                return;
            }
            throw new BusinessException("调度任务正在同步，请稍后重试");
        }
        try {
            if (isDeleteRequested(task)) {
                deleteRemote(task, version);
            } else {
                upsertRemote(task, version);
            }
        } catch (Exception e) {
            markFailed(task, version, e);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("同步调度任务失败", e);
        }
    }

    @Async
    @Override
    public void submit(Long taskId) {
        try {
            syncNow(taskId);
        } catch (Exception e) {
            log.warn("异步同步测试定时任务失败，taskId={}", taskId, e);
        }
    }

    @Override
    public int reconcile() {
        LocalDateTime now = LocalDateTime.now();
        List<TestTimedTaskDO> candidates = taskMapper.selectList(Wrappers.<TestTimedTaskDO>query()
            .and(wrapper -> wrapper.in("schedule_sync_status", List.of("PENDING", "FAILED", "DELETING"))
                .or(active -> active.eq("del_flag", StatusTypeEnum.NORMAL)
                    .and(stale -> stale.isNull("schedule_sync_time")
                        .or()
                        .lt("schedule_sync_time", now.minusMinutes(10)))))
            .and(wrapper -> wrapper.isNull("schedule_sync_next_retry_time")
                .or()
                .le("schedule_sync_next_retry_time", now))
            .orderByAsc("schedule_sync_next_retry_time", "schedule_sync_time")
            .last("LIMIT " + RECONCILE_BATCH_SIZE));
        int synced = 0;
        for (TestTimedTaskDO task : candidates) {
            try {
                syncNow(task.getId());
                synced++;
            } catch (Exception e) {
                log.warn("测试定时任务调度对账失败，taskId={}", task.getId(), e);
            }
        }
        try {
            deleteOrphanJobs();
        } catch (Exception e) {
            log.warn("清理孤儿测试调度任务失败，将在下次对账时重试", e);
        }
        return synced;
    }

    private void upsertRemote(TestTimedTaskDO task, long version) throws Exception {
        JobReq req = buildJobReq(task);
        JobResp current = loadScheduleJob(task);
        if (current == null) {
            ensure(jobService.create(req), "创建远程调度任务失败");
        } else if (!matches(current, req)) {
            if (!jobService.update(req, current.getId())) {
                JobResp latest = loadScheduleJob(task);
                ensure(latest == null && jobService.create(req), "更新远程调度任务失败");
            }
        }
        JobResp synced = loadScheduleJob(task);
        ensure(synced != null, "远程调度任务同步后不可见");
        taskMapper.markScheduleSynced(task.getId(), version, synced.getId(), synced.getNextTriggerAt(), LocalDateTime
            .now());
    }

    private void deleteRemote(TestTimedTaskDO task, long version) {
        JobResp current = loadScheduleJob(task);
        if (current != null) {
            ensure(jobService.delete(current.getId()), "删除远程调度任务失败");
        }
        taskMapper.markScheduleDeleted(task.getId(), version, LocalDateTime.now());
    }

    private void markFailed(TestTimedTaskDO task, long version, Exception exception) {
        int retryCount = (task.getScheduleSyncRetryCount() == null ? 0 : task.getScheduleSyncRetryCount()) + 1;
        long delaySeconds = Math.min(3600L, 30L << Math.min(retryCount - 1, 7));
        String message = CharSequenceUtil.subWithLength(String.valueOf(exception.getMessage()), 0, 500);
        LocalDateTime now = LocalDateTime.now();
        taskMapper.markScheduleSyncFailed(task.getId(), version, message, now, retryCount, now
            .plusSeconds(delaySeconds));
    }

    private JobReq buildJobReq(TestTimedTaskDO task) throws Exception {
        JobReq req = new JobReq();
        req.setGroupName(JOB_GROUP_NAME);
        req.setJobName(internalJobName(task.getId()));
        req.setDescription(task.getDescription());
        req.setTriggerType(JobTriggerTypeEnum.CRON);
        req.setTriggerInterval(task.getCronExpression());
        req.setExecutorType(1);
        req.setTaskType(JobTaskTypeEnum.CLUSTER);
        req.setExecutorInfo(TestPlanJobExecutor.EXECUTOR_NAME);
        TestTimedTaskExecutePayload payload = new TestTimedTaskExecutePayload();
        payload.setTaskId(task.getId());
        payload.setTriggerMode("SCHEDULE");
        req.setArgsStr(JsonUtil.marshal(payload));
        req.setArgsType(1);
        req.setRouteKey(JobRouteStrategyEnum.POLLING);
        req.setBlockStrategy(JobBlockStrategyEnum.PARALLEL);
        req.setExecutorTimeout(3600);
        req.setMaxRetryTimes(0);
        req.setRetryInterval(0);
        req.setParallelNum(1);
        req.setJobStatus("ENABLED".equalsIgnoreCase(task.getStatus()) ? JobStatusEnum.ENABLED : JobStatusEnum.DISABLED);
        return req;
    }

    private JobResp loadScheduleJob(TestTimedTaskDO task) {
        JobResp current = findJob(task, internalJobName(task.getId()));
        if (current == null) {
            current = findJob(task, task.getName());
        }
        return current == null ? findJobById(task.getScheduleJobId()) : current;
    }

    private JobResp findJob(TestTimedTaskDO task, String jobName) {
        JobQuery query = new JobQuery();
        query.setGroupName(JOB_GROUP_NAME);
        query.setJobName(jobName);
        query.setPage(1);
        query.setSize(20);
        PageResp<JobResp> result = jobService.page(query);
        if (result.getList() == null) {
            return null;
        }
        return result.getList()
            .stream()
            .filter(item -> Objects.equals(item.getId(), task.getScheduleJobId()) || Objects.equals(item
                .getJobName(), jobName))
            .findFirst()
            .orElse(null);
    }

    private JobResp findJobById(Long jobId) {
        if (jobId == null) {
            return null;
        }
        JobQuery query = new JobQuery();
        query.setGroupName(JOB_GROUP_NAME);
        query.setPage(1);
        query.setSize(1000);
        PageResp<JobResp> result = jobService.page(query);
        return result.getList() == null
            ? null
            : result.getList().stream().filter(item -> Objects.equals(item.getId(), jobId)).findFirst().orElse(null);
    }

    private boolean matches(JobResp current, JobReq expected) {
        return Objects.equals(current.getJobName(), expected.getJobName()) && Objects.equals(current
            .getDescription(), expected.getDescription()) && Objects.equals(current.getTriggerInterval(), expected
                .getTriggerInterval()) && Objects.equals(current.getExecutorInfo(), expected
                    .getExecutorInfo()) && Objects.equals(current.getArgsStr(), expected.getArgsStr()) && Objects
                        .equals(current.getJobStatus(), expected.getJobStatus());
    }

    private void deleteOrphanJobs() {
        List<JobResp> jobs = new ArrayList<>();
        int page = 1;
        long total;
        do {
            JobQuery query = new JobQuery();
            query.setGroupName(JOB_GROUP_NAME);
            query.setPage(page++);
            query.setSize(1000);
            PageResp<JobResp> result = jobService.page(query);
            total = result.getTotal();
            if (result.getList() == null || result.getList().isEmpty()) {
                break;
            }
            jobs.addAll(result.getList());
        } while (jobs.size() < total);
        for (JobResp job : jobs) {
            Long taskId = parseInternalTaskId(job.getJobName());
            if (taskId == null) {
                continue;
            }
            TestTimedTaskDO task = taskMapper.selectById(taskId);
            if (task == null || !StatusTypeEnum.NORMAL.equals(task.getDelFlag())) {
                if (!jobService.delete(job.getId())) {
                    log.warn("删除孤儿测试调度任务失败，jobId={}，jobName={}", job.getId(), job.getJobName());
                }
            }
        }
    }

    private Long parseInternalTaskId(String jobName) {
        if (jobName == null || !jobName.startsWith(JOB_NAME_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(jobName.substring(JOB_NAME_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String internalJobName(Long taskId) {
        return JOB_NAME_PREFIX + taskId;
    }

    private boolean isDeleteRequested(TestTimedTaskDO task) {
        return !StatusTypeEnum.NORMAL.equals(task.getDelFlag()) || "DELETING".equalsIgnoreCase(task.getStatus());
    }

    private void ensure(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(message);
        }
    }
}
