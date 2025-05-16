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
 * 创建或修改项目管理-环境配置参数
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Data
@Schema(description = "创建或修改项目管理-环境配置参数")
public class ProjectEnvironmentConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    /**
     * 环境名称
     */
    @Schema(description = "环境名称")
    @NotBlank(message = "环境名称不能为空")
    @Length(max = 30, message = "环境名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 环境描述
     */
    @Schema(description = "环境描述")
    @Length(max = 255, message = "环境描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 环境版本信息
     */
    @Schema(description = "环境版本信息")
    @NotEmpty(message = "环境版本信息不能为空")
    @Size(max = 10, message = "环境版本信息最多支持 {max} 个")
    private List<Object> versionConfig;

    /**
     * 环境服务器信息
     */
    @Schema(description = "环境服务器信息")
    @NotEmpty(message = "环境服务器信息不能为空")
    @Size(max = 10, message = "环境服务器信息最多支持 {max} 个")
    private List<Object> serverConfig;

    /**
     * 环境数据库信息
     */
    @Schema(description = "环境数据库信息")
    @NotEmpty(message = "环境数据库信息不能为空")
    @Size(max = 10, message = "环境数据库信息最多支持 {max} 个")
    private List<Object> dataBaseConfig;

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