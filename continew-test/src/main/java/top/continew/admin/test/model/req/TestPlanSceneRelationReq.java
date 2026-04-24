package top.continew.admin.test.model.req;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class TestPlanSceneRelationReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "场景ID不能为空")
    private List<Long> sceneIds;
}
