package top.continew.admin.project.model.resp;

import cn.crane4j.annotation.AssembleMethod;
import cn.crane4j.annotation.ContainerMethod;
import cn.crane4j.annotation.Mapping;
import cn.crane4j.annotation.condition.ConditionOnExpression;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

import java.time.*;

/**
 * 项目管理-环境配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "项目管理-环境配置详情信息")
public class ProjectEnvironmentConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @ConditionOnExpression("#target.projectName == null")
    @AssembleMethod(props = @Mapping(src = "name", ref = "projectName"), targetType = ProjectConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectConfigDetailResp.class))
    private Long projectId;

    @ExcelProperty(value = "所属项目", order = 2)
    private String projectName;

    /**
     * 环境名称
     */
    @Schema(description = "环境名称")
    @ExcelProperty(value = "环境名称", order = 3)
    private String name;

    /**
     * 环境描述
     */
    @Schema(description = "环境描述")
    @ExcelProperty(value = "环境描述", order = 4)
    private String description;

    /**
     * 环境版本信息
     */
    @Schema(description = "环境版本信息")
    @ExcelProperty(value = "环境版本信息", converter = ExcelListConverter.class, order = 5)
    private List<Object> versionConfig;

    /**
     * 环境服务器信息
     */
    @Schema(description = "环境服务器信息")
    @ExcelProperty(value = "环境服务器信息", converter = ExcelListConverter.class, order = 6)
    private List<Object> serverConfig;

    /**
     * 环境数据库信息
     */
    @Schema(description = "环境数据库信息")
    @ExcelProperty(value = "环境数据库信息", converter = ExcelListConverter.class, order = 7)
    private List<Object> dataBaseConfig;

    /**
     * 主线版本
     */
    @Schema(description = "主线版本")
    @ExcelProperty(value = "主线版本", order = 8)
    private String lastVersion;

    /**
     * 环境域名
     */
    @Schema(description = "环境域名")
    @ExcelProperty(value = "环境域名", order = 9)
    private String lastDomain;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 10)
    private DisEnableStatusEnum status;

    /**
     * 创建部门
     */
    @Schema(description = "创建部门")
    @ExcelProperty(value = "创建部门", order = 11)
    private Long deptId;

    /**
     * 更新IP
     */
    @Schema(description = "更新IP")
    @ExcelProperty(value = "更新IP", order = 12)
    private String updateIp;

    /**
     * 备注
     */
    @Schema(description = "备注")
    @ExcelProperty(value = "备注", order = 13)
    private String remark;

    /**
     * 版本
     */
    @Schema(description = "版本")
    @ExcelProperty(value = "版本", order = 14)
    private String version;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    @ExcelProperty(value = "删除标志（0删除 1存在）", order = 15)
    private Integer delFlag;
}