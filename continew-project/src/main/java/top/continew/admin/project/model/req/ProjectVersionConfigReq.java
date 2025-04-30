package top.continew.admin.project.model.req;

import lombok.Data;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;

import java.time.*;

/**
 * 创建或修改项目管理-版本配置参数
 *
 * @author hagyao520
 * @since 2025/04/28 15:33
 */
@Data
@Schema(description = "创建或修改项目管理-版本配置参数")
public class ProjectVersionConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    /**
     * 版本名称
     */
    @Schema(description = "版本名称")
    @NotBlank(message = "版本名称不能为空")
    @Length(max = 30, message = "版本名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 版本描述
     */
    @Schema(description = "版本描述")
    @Length(max = 255, message = "版本描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    private Integer delFlag = 1;
}