package top.continew.admin.automation.model.resp;

import lombok.Data;

/** 全部场景按场景统计，指定场景按各用例最新调试记录统计，均与历史分页无关。 */
@Data
public class AutomationUiExecutionHistorySummaryResp {
    private String dimension = "scene";
    private long total;
    private long completed;
    private long passed;
    private long failed;
    private long blocked;
    private long cancelled;
    private long skipped;
    private long notExecuted;
}
