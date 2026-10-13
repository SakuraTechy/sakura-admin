package top.continew.admin.automation.model.query;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 普通调试历史的完整场景范围，不放宽原单场景查询契约。 */
@Data
public class AutomationUiExecutionHistoryQuery {
    @NotNull(message = "项目 ID 不能为空")
    @Positive(message = "项目 ID 必须为正整数")
    private Long projectId;

    @Positive(message = "版本 ID 必须为正整数")
    private Long versionId;

    @Size(max = 1000, message = "模块范围不能超过 1000 个")
    private List<@NotNull @Positive Long> moduleIds;

    @Positive(message = "场景数据库 ID 必须为正整数")
    private Long sceneDbId;
}
