package top.continew.admin.automation.model.req;

import lombok.Data;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改自动化管理-节点配置参数
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Data
@Schema(description = "创建或修改自动化管理-节点配置参数")
public class AutomationNodeConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属Jenkins")
    @NotNull(message = "所属Jenkins不能为空")
    private Long jenkinsId;

    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    @NotBlank(message = "节点名称不能为空")
    @Length(max = 30, message = "节点名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 节点类型
     */
    @Schema(description = "节点类型")
    @NotBlank(message = "节点类型不能为空")
    @Length(max = 30, message = "节点类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 节点json配置
     */
    @Schema(description = "节点json配置")
    @NotBlank(message = "节点json配置不能为空")
//    @Length(max = 255, message = "节点配置长度不能超过 {max} 个字符")
    private String json;

    /**
     * 节点xml配置
     */
    @Schema(description = "节点xml配置")
    @NotBlank(message = "节点xml配置不能为空")
//    @Length(max = 255, message = "节点配置长度不能超过 {max} 个字符")
    private String xml;

    /**
     * 节点地址
     */
    @Schema(description = "节点地址")
    @NotBlank(message = "节点地址不能为空")
    @Length(max = 255, message = "节点地址长度不能超过 {max} 个字符")
    private String url;

    /**
     * 节点描述
     */
    @Schema(description = "节点描述")
    @NotEmpty(message = "节点描述不能为空")
    @Size(max = 10, message = "节点描述最多支持 {max} 个")
    private AutomationNodeConfigDO.Description description;

    /**
     * 节点环境状态
     */
    @Schema(description = "节点环境状态")
    @NotEmpty(message = "节点环境状态不能为空")
    @Size(max = 10, message = "节点环境状态最多支持 {max} 个")
    private AutomationNodeConfigDO.Active active;

    /**
     * 节点参数列表
     */
    @Schema(description = "节点参数列表")
    @NotEmpty(message = "节点参数列表不能为空")
    @Size(max = 10, message = "节点参数列表最多支持 {max} 个")
    private List<AutomationNodeConfigDO.Config> configList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private StatusTypeEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}