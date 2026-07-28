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

import cn.hutool.core.bean.BeanUtil;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.common.json.JsonUtil;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.service.TestPlanService;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.starter.core.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class TestPlanJobExecutor {

    public static final String EXECUTOR_NAME = "ExecuteTestPlanJob";

    private final TestPlanService testPlanService;
    private final TestTimedTaskMapper timedTaskMapper;
    private final TestTimedTaskRunService timedTaskRunService;

    @JobExecutor(name = EXECUTOR_NAME)
    public void executeTestPlan(String args) {
        TestTimedTaskExecutePayload payload = parsePayload(args);
        if (payload.getTaskId() == null) {
            throw new BusinessException("定时任务 ID 不能为空");
        }
        TestTimedTaskDO task = timedTaskMapper.selectById(payload.getTaskId());
        if (task == null) {
            throw new BusinessException("测试定时任务不存在");
        }

        TestTimedTaskRunService.StartResult startResult = timedTaskRunService.start(task.getId(), payload
            .getTriggerMode());
        if (startResult.skipped()) {
            SnailJobLog.REMOTE.info("测试计划存在未完成执行，本次已跳过，taskId={}", task.getId());
            return;
        }

        Long runId = startResult.run().getId();
        try {
            TestPlanExecuteReq req = buildExecuteReq(task, payload);
            SnailJobLog.REMOTE.info("开始执行测试计划，taskId={}, planId={}, runId={}", task.getId(), task
                .getTestPlanId(), runId);
            TestPlanExecuteResp executeResp = testPlanService.execute(task.getTestPlanId(), req);
            timedTaskRunService.attachExecution(runId, executeResp);
            SnailJobLog.REMOTE.info("测试计划执行触发完成，taskId={}, planId={}, runId={}, reportId={}", task.getId(), task
                .getTestPlanId(), runId, executeResp == null ? null : executeResp.getTestReportId());
        } catch (Exception e) {
            timedTaskRunService.fail(runId, e.getMessage());
            throw e;
        }
    }

    private TestPlanExecuteReq buildExecuteReq(TestTimedTaskDO task, TestTimedTaskExecutePayload payload) {
        TestPlanExecuteReq req = new TestPlanExecuteReq();
        req.setProjectEnvironmentId(task.getProjectEnvironmentId());
        req.setAutomationEnvironmentId(task.getAutomationEnvironmentId());
        TestExecutionEngineEnum engine = parseEngine(task.getExecutionEngine());
        if (TestExecutionEngineEnum.CHROME_DEVTOOLS_PROTOCOL == engine) {
            throw new BusinessException("CDP 依赖浏览器会话，不支持无人值守定时执行");
        }
        req.setExecutionEngine(engine);
        req.setTriggerMode("MANUAL".equalsIgnoreCase(payload.getTriggerMode()) ? "MANUAL" : "SCHEDULE");
        if (task.getExecutionConfig() != null && !task.getExecutionConfig().isEmpty()) {
            if (TestExecutionEngineEnum.PLAYWRIGHT_RUNNER == engine) {
                req.setRunnerOptions(BeanUtil.toBean(task
                    .getExecutionConfig(), AutomationPlaywrightRunnerOptionsReq.class));
            } else if (TestExecutionEngineEnum.CHROME_DEVTOOLS_PROTOCOL == engine) {
                req.setCdpOptions(task.getExecutionConfig());
            }
        }
        req.setExecuteName(payload.getExecuteName() == null ? task.getExecuteName() : payload.getExecuteName());
        req.setExecuteEmail(payload.getExecuteEmail() == null ? task.getExecuteEmail() : payload.getExecuteEmail());
        return req;
    }

    private TestTimedTaskExecutePayload parsePayload(String args) {
        try {
            return JsonUtil.unmarshal(args, TestTimedTaskExecutePayload.class);
        } catch (Exception e) {
            throw new BusinessException("定时任务参数解析失败");
        }
    }

    private TestExecutionEngineEnum parseEngine(String value) {
        if (value == null || value.isBlank()) {
            return TestExecutionEngineEnum.SELENIUM;
        }
        try {
            return TestExecutionEngineEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("定时任务执行引擎无效：" + value);
        }
    }
}
