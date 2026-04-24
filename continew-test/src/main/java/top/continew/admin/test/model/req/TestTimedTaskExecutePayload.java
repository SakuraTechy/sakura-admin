package top.continew.admin.test.model.req;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class TestTimedTaskExecutePayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long testPlanId;
    private Long projectEnvironmentId;
    private Long automationEnvironmentId;
    private String executeName;
    private String executeEmail;
}
