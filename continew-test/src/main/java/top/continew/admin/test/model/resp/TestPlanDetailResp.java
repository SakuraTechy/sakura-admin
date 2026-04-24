package top.continew.admin.test.model.resp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class TestPlanDetailResp extends TestPlanResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private Map<String, Object> timedTasksConfig;
    private Map<String, Object> projectConfig;
    private Map<String, Object> automationConfig;
    private List<Object> functionalScene;
}
