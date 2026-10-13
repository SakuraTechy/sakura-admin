package top.continew.admin.automation.model.resp;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 整次任务或未分组旧批次的有界摘要；runId 不是可执行的子 batchId。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AutomationUiExecutionRunResp extends AutomationUiExecutionSummaryResp {
    private String runId;
    private String sceneName;
    private Integer sceneOrder;
    private Integer sceneTotal;
    private Integer sceneCompleted;
    private Integer scenePass;
    private Integer sceneFail;
    private Integer sceneCancelled;
    private Integer sceneBlocked;
    private Integer sceneSkip;
}
