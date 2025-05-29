package top.continew.admin.automation.model.resp;

import com.alibaba.excel.annotation.ExcelIgnore;
import lombok.Data;

import java.io.Serial;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import cn.crane4j.annotation.AssembleMethod;
import cn.crane4j.annotation.ContainerMethod;
import cn.crane4j.annotation.Mapping;
import cn.crane4j.annotation.condition.ConditionOnExpression;

import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.service.AutomationJenkinsConfigService;
import top.continew.admin.common.config.excel.ExcelDictConverter;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

/**
 * 自动化管理-节点配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "自动化管理-节点配置详情信息")
public class AutomationNodeConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属Jenkins
     */
    @Schema(description = "所属Jenkins")
    @ConditionOnExpression("#target.jenkinsName == null")
    @AssembleMethod(props = @Mapping(src = "ip", ref = "jenkinsName"), targetType = AutomationJenkinsConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = AutomationJenkinsConfigResp.class))
    private Long jenkinsId;

    @ExcelProperty(value = "所属Jenkins", order = 2)
    private String jenkinsName;

    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    @ExcelProperty(value = "节点名称", order = 3)
    private String name;


    /**
     * 节点类型
     */
    @Schema(description = "节点类型")
    @ExcelProperty(value = "节点类型", order = 4)
    private String type;

    /**
     * 节点json配置
     */
    @Schema(description = "节点json配置")
    @ExcelProperty(value = "节点json配置", order = 5)
    private String json;

    /**
     * 节点xml配置
     */
    @Schema(description = "节点xml配置")
    @ExcelProperty(value = "节点xml配置", order = 6)
    private String xml;

    /**
     * 节点地址
     */
    @Schema(description = "节点地址")
    @ExcelProperty(value = "节点地址", order = 7)
    private String url;

    /**
     * 节点描述
     */
    @Schema(description = "节点描述")
    @ExcelIgnore
    private AutomationNodeConfigDO.Description description;

    /**
     * 节点环境状态
     */
    @Schema(description = "节点环境状态")
    @ExcelIgnore
    private AutomationNodeConfigDO.Active active;

    @Schema(description = "节点在线状态")
    @ExcelProperty(value = "节点在线状态", converter = ExcelBaseEnumConverter.class,  order = 10)
    private StatusTypeEnum offlineStatus;

    @Schema(description = "节点使用状态")
    @ExcelProperty(value = "节点使用状态", converter = ExcelBaseEnumConverter.class,  order = 11)
    private StatusTypeEnum idleStatus;

    /**
     * 节点参数列表
     */
    @Schema(description = "节点参数列表")
    @ExcelProperty(value = "节点参数列表", converter = ExcelListConverter.class, order = 12)
    private List<AutomationNodeConfigDO.Config> configList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 13)
    private StatusTypeEnum status;

    /**
     * 更新IP
     */
    @Schema(description = "更新IP")
    @ExcelProperty(value = "更新IP", order = 14)
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 15)
    private StatusTypeEnum delFlag;
}