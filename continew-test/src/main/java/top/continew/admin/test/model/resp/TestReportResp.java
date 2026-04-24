package top.continew.admin.test.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "测试报告信息")
public class TestReportResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private String projectName;
    private String versionName;
    private Long testPlanId;
    private String testPlanName;
    private String name;
    private String description;
    private String triggerMode;
    private String executeMode;
    private Long runTime;
    private String buildNumber;
    private String consoleUrl;
    private String reportUrl;
    private String videoUrl;
    private String status;
}
