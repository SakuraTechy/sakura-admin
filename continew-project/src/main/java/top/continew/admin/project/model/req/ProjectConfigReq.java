package top.continew.admin.project.model.req;

import jakarta.validation.constraints.*;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建或修改项目配置参数
 *
 * @author hagyao520
 * @since 2025/04/15 11:56
 */
@Data
@Schema(description = "创建或修改项目配置参数")
public class ProjectConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    @NotBlank(message = "项目名称不能为空")
    @Length(max = 30, message = "项目名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 项目简称
     */
    @Schema(description = "项目简称")
    @NotBlank(message = "项目简称不能为空")
    @Length(max = 30, message = "项目简称长度不能超过 {max} 个字符")
    private String abbreviate;

    /**
     * 项目成员
     */
    @Schema(description = "项目成员", example = "[1,2,3]")
    @NotEmpty(message = "项目成员不能为空")
    private List<String> members;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    @Length(max = 255, message = "项目描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;
}