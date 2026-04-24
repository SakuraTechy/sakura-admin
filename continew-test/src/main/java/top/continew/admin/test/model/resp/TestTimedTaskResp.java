package top.continew.admin.test.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "测试定时任务信息")
public class TestTimedTaskResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long testPlanId;
    private String testPlanName;
    private Long scheduleJobId;
    private String type;
    private String name;
    private String description;
    private String cronExpression;
    private String misfirePolicy;
    private Integer allowConcurrent;
    private Long projectEnvironmentId;
    private Long automationEnvironmentId;
    private String executeName;
    private String executeEmail;
    private LocalDateTime nextExecuteTime;
    private String status;
}
