package top.continew.admin.test.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class TestPlanExecuteReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目环境ID不能为空")
    private Long projectEnvironmentId;

    @NotNull(message = "自动化环境ID不能为空")
    private Long automationEnvironmentId;

    private String executeName;
    private String executeEmail;
}
