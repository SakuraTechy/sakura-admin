package top.continew.admin.test.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "创建或修改测试计划参数")
public class TestPlanReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String projectName;
    private String type;

    @NotBlank(message = "计划名称不能为空")
    @Length(max = 128, message = "计划名称长度不能超过 {max}")
    private String name;

    @Length(max = 64, message = "计划简称长度不能超过 {max}")
    private String abbreviate;

    @Length(max = 500, message = "计划描述长度不能超过 {max}")
    private String description;

    private List<Long> memberIds;
    private List<Long> principalIds;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Map<String, Object> timedTasksConfig;
    private Map<String, Object> projectConfig;
    private Map<String, Object> automationConfig;
    private List<Object> functionalScene;
    private List<Long> uiTestScene;
    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
