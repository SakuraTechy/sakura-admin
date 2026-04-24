package top.continew.admin.test.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "测试计划信息")
public class TestPlanResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private String projectName;
    private String type;
    private String name;
    private String abbreviate;
    private String description;
    private List<Long> memberIds;
    private List<Long> principalIds;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private List<Long> uiTestScene;
    private Integer sceneCount;
    private Integer executedCount;
    private Integer passedCount;
    private BigDecimal testProgress;
    private Long runTime;
    private String status;
}
