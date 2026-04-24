package top.continew.admin.test.model.req;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
public class TestReportUploadReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String status;
    private Long runTime;
    private String buildNumber;
    private String consoleUrl;
    private String reportUrl;
    private String videoUrl;
    private Map<String, Object> statisticAnalysis;
}
