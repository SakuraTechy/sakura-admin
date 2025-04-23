package top.continew.admin.project.model.resp;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;
import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.io.Serial;
import java.util.List;

/**
 * 项目配置信息
 *
 * @author hagyao520
 * @since 2025/04/15 11:56
 */
@Data
@Schema(description = "项目配置信息")
public class ProjectConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    private String name;

    /**
     * 项目简称
     */
    @Schema(description = "项目简称")
    private String abbreviate;

    /**
     * 项目成员
     */
    @Schema(description = "项目成员", example = "[1,2,3]")
//    @Assemble(props = @Mapping(ref = "memberNames"), container = ContainerConstants.USER_NICKNAME, handlerType = ManyToManyAssembleOperationHandler.class)
    @Assemble(prop = ":memberNames", container = ContainerConstants.USER_NICKNAME, handlerType = ManyToManyAssembleOperationHandler.class)
    private List<String> members;
    private List<String> memberNames;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    private String description;

    /**
     * 项目域名
     */
    @Schema(description = "项目域名")
    private String lastDomain;

    /**
     * 主线版本
     */
    @Schema(description = "主线版本")
    private String lastVersion;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 创建部门
     */
    @Schema(description = "创建部门")
    private Long deptId;


    /**
     * 更新IP
     */
    @Schema(description = "更新IP")
    private String updateIp;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

    /**
     * 版本
     */
    @Schema(description = "版本")
    private String version;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    private Integer delFlag;
}