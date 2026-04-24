package top.continew.admin.test.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Schema(description = "创建或修改测试报告参数")
public class TestReportReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String projectName;
    private String versionName;
    private Long testPlanId;
    private String testPlanName;

    @NotBlank(message = "报告名称不能为空")
    @Length(max = 128, message = "报告名称长度不能超过 {max}")
    private String name;

    @Length(max = 500, message = "报告描述长度不能超过 {max}")
    private String description;

    private String triggerMode;
    private String executeMode;
    private Map<String, Object> projectConfig;
    private Map<String, Object> automationConfig;
    private Map<String, Object> runtimeEnvironment;
    private Map<String, Object> statisticAnalysis;
    private Long runTime;
    private String buildNumber;
    private String consoleUrl;
    private String reportUrl;
    private String videoUrl;
    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
