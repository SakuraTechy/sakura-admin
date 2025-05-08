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
 * 创建或修改项目管理-服务器配置参数
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Data
@Schema(description = "创建或修改项目管理-服务器配置参数")
public class ProjectServerConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    /**
     * 服务器类型
     */
    @Schema(description = "服务器类型")
    @NotBlank(message = "服务器类型不能为空")
    @Length(max = 30, message = "服务器类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 服务器版本
     */
    @Schema(description = "服务器版本")
    @NotBlank(message = "服务器版本不能为空")
    @Length(max = 30, message = "服务器版本长度不能超过 {max} 个字符")
    private String version;

    /**
     * 服务器IP
     */
    @Schema(description = "服务器IP")
    @NotBlank(message = "服务器IP不能为空")
    @Length(max = 30, message = "服务器IP长度不能超过 {max} 个字符")
    private String ip;

    /**
     * 服务器端口
     */
    @Schema(description = "服务器端口")
    @NotNull(message = "服务器端口不能为空")
    private Long port;

    /**
     * 服务器用户名
     */
    @Schema(description = "服务器用户名")
    @NotBlank(message = "服务器用户名不能为空")
    @Length(max = 30, message = "服务器用户名长度不能超过 {max} 个字符")
    private String userName;

    /**
     * 服务器密码
     */
    @Schema(description = "服务器密码", hidden = true)
    @NotBlank(message = "服务器密码不能为空")
    @Length(max = 30, message = "服务器密码长度不能超过 {max} 个字符")
    private String passWord;

    /**
     * 服务器描述
     */
    @Schema(description = "服务器描述")
    @Length(max = 255, message = "服务器描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 服务器参数配置
     */
    @Schema(description = "服务器参数配置")
    @NotEmpty(message = "服务器参数配置不能为空")
    @Size(max = 10, message = "服务器参数配置最多支持 {max} 组")
    private List<Object> configList;

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