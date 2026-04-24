package top.continew.admin.test.model.resp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class TestReportDetailResp extends TestReportResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private Map<String, Object> projectConfig;
    private Map<String, Object> automationConfig;
    private Map<String, Object> runtimeEnvironment;
    private Map<String, Object> statisticAnalysis;
}
