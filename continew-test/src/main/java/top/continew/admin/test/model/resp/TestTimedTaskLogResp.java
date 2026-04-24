package top.continew.admin.test.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "测试定时任务执行日志")
public class TestTimedTaskLogResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long jobId;
    private String groupName;
    private String jobName;
    private String taskBatchStatus;
    private String operationReason;
    private String executorInfo;
    private LocalDateTime executionAt;
    private LocalDateTime createDt;
}
