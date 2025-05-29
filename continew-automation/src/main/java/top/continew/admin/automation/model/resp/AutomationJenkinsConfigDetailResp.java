package top.continew.admin.automation.model.resp;

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
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

import java.time.*;

/**
 * 自动化管理-Jenkins配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "自动化管理-Jenkins配置详情信息")
public class AutomationJenkinsConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本
     */
    @Schema(description = "版本")
    @ExcelProperty(value = "版本", order = 2)
    private String version;

    /**
     * IP
     */
    @Schema(description = "IP")
    @ExcelProperty(value = "IP", order = 3)
    private String ip;

    /**
     * 端口
     */
    @Schema(description = "端口")
    @ExcelProperty(value = "端口", order = 4)
    private Integer port;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    @ExcelProperty(value = "用户名", order = 5)
    private String userName;

    /**
     * 密码
     */
    @Schema(description = "密码")
    @ExcelProperty(value = "密码", order = 6)
    private String passWord;

    /**
     * 地址
     */
    @Schema(description = "地址")
    @ExcelProperty(value = "密码", order = 7)
    private String url;

    /**
     * 关联项目
     */
    @Schema(description = "关联项目")
    @ExcelProperty(value = "关联项目", converter = ExcelListConverter.class, order = 8)
    private List<Object> jobList;

    /**
     * 描述
     */
    @Schema(description = "描述")
    @ExcelProperty(value = "描述", order = 9)
    private String description;

    /**
     * 节点列表
     */
    @Schema(description = "节点列表")
    @ExcelProperty(value = "节点列表", converter = ExcelListConverter.class, order = 10)
    private List<Object> nodeList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 11)
    private DisEnableStatusEnum status;

    /**
     * 更新人IP
     */
    @Schema(description = "更新人IP")
    @ExcelProperty(value = "更新人IP", order = 12)
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 13)
    private StatusTypeEnum delFlag;
}