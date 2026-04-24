package top.continew.admin.test.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.common.json.JsonUtil;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestTimedTaskExecutePayload;
import top.continew.admin.test.service.TestPlanService;
import top.continew.starter.core.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class TestPlanJobExecutor {

    public static final String EXECUTOR_NAME = "ExecuteTestPlanJob";

    private final TestPlanService testPlanService;

    @JobExecutor(name = EXECUTOR_NAME)
    public void executeTestPlan(String args) {
        TestTimedTaskExecutePayload payload = parsePayload(args);
        TestPlanExecuteReq req = new TestPlanExecuteReq();
        req.setProjectEnvironmentId(payload.getProjectEnvironmentId());
        req.setAutomationEnvironmentId(payload.getAutomationEnvironmentId());
        req.setExecuteName(payload.getExecuteName());
        req.setExecuteEmail(payload.getExecuteEmail());

        SnailJobLog.REMOTE.info("开始执行测试计划，taskId={}, planId={}", payload.getTaskId(), payload.getTestPlanId());
        testPlanService.execute(payload.getTestPlanId(), req);
        SnailJobLog.REMOTE.info("测试计划执行触发完成，taskId={}, planId={}", payload.getTaskId(), payload.getTestPlanId());
    }

    private TestTimedTaskExecutePayload parsePayload(String args) {
        try {
            return JsonUtil.unmarshal(args, TestTimedTaskExecutePayload.class);
        } catch (Exception e) {
            throw new BusinessException("定时任务参数解析失败");
        }
    }
}
