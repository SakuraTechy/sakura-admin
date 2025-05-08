package top.continew.admin.project.model.resp;

import cn.crane4j.annotation.AssembleMethod;
import cn.crane4j.annotation.ContainerMethod;
import cn.crane4j.annotation.Mapping;
import cn.crane4j.annotation.condition.ConditionOnExpression;
import lombok.Data;

import java.io.Serial;
import java.util.List;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.admin.project.service.ProjectConfigService;

import java.time.*;

/**
 * 项目管理-服务器配置信息
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Data
@Schema(description = "项目管理-服务器配置信息")
public class ProjectServerConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @ConditionOnExpression("#target.projectName == null")
    @AssembleMethod(props = @Mapping(src = "name", ref = "projectName"), targetType = ProjectConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectConfigDetailResp.class))
    private Long projectId;
    private String projectName;

    /**
     * 服务器类型
     */
    @Schema(description = "服务器类型")
    private String type;

    /**
     * 服务器版本
     */
    @Schema(description = "服务器版本")
    private String version;

    /**
     * 服务器IP
     */
    @Schema(description = "服务器IP")
    private String ip;

    /**
     * 服务器端口
     */
    @Schema(description = "服务器端口")
    private Long port;

    /**
     * 服务器用户名
     */
    @Schema(description = "服务器用户名")
    private String userName;

    /**
     * 服务器密码
     */
    @Schema(description = "服务器密码")
    private String passWord;

    /**
     * 服务器描述
     */
    @Schema(description = "服务器描述")
    private String description;

    /**
     * 服务器参数配置
     */
    @Schema(description = "服务器参数配置")
    private List<Object> configList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 修改人
     */
    @Schema(description = "修改人")
    private Long updateUser;

    /**
     * 修改时间
     */
    @Schema(description = "修改时间")
    private LocalDateTime updateTime;

    /**
     * 更新人IP
     */
    @Schema(description = "更新人IP")
    private String updateIp;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    private Integer delFlag;
}