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
 * 项目管理-环境配置信息
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Data
@Schema(description = "项目管理-环境配置信息")
public class ProjectEnvironmentConfigResp extends BaseDetailResp {

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
     * 环境名称
     */
    @Schema(description = "环境名称")
    private String name;

    /**
     * 环境描述
     */
    @Schema(description = "环境描述")
    private String description;

    /**
     * 环境版本信息
     */
    @Schema(description = "环境版本信息")
    private List<Object> versionConfig;

    /**
     * 环境服务器信息
     */
    @Schema(description = "环境服务器信息")
    private List<Object> serverConfig;

    /**
     * 环境数据库信息
     */
    @Schema(description = "环境数据库信息")
    private List<Object> dataBaseConfig;

    /**
     * 主线版本
     */
    @Schema(description = "主线版本")
    private String lastVersion;

    /**
     * 环境域名
     */
    @Schema(description = "环境域名")
    private String lastDomain;

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