package top.continew.admin.test.model.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class TestMetricQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long versionId;
    private Ui ui;

    /**
     * 兼容老项目测试度量接口的嵌套 UI 查询条件。
     */
    @Data
    public static class Ui implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long projectId;
        private Long versionId;
        private Long parentId;
    }
}
