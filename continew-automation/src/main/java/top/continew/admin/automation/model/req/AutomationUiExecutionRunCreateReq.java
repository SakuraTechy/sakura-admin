package top.continew.admin.automation.model.req;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 冻结整次调试的有序场景范围，不接收用例正文、执行凭据或报告归属。 */
@Data
public class AutomationUiExecutionRunCreateReq {
    @NotEmpty
    @Size(max = 10000)
    private List<@NotNull @Positive Long> sceneIds;
    @NotNull
    @Positive
    private Long projectEnvironmentId;
    @NotNull
    @Pattern(regexp = "playwright-runner|extension-cdp")
    private String executionType;
}
