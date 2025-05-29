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
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

import java.time.*;

/**
 * 自动化管理-浏览器配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/29 15:41
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "自动化管理-浏览器配置详情信息")
public class AutomationBrowserConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 浏览器类型
     */
    @Schema(description = "浏览器类型")
    @ExcelProperty(value = "浏览器类型", order = 2)
    private String type;

    /**
     * 浏览器版本
     */
    @Schema(description = "浏览器版本")
    @ExcelProperty(value = "浏览器版本", order = 3)
    private String version;

    /**
     * 浏览器名称
     */
    @Schema(description = "浏览器名称")
    @ExcelProperty(value = "浏览器名称", order = 4)
    private String name;

    /**
     * 浏览器程序下载地址
     */
    @Schema(description = "浏览器程序下载地址")
    @ExcelProperty(value = "浏览器程序下载地址", order = 5)
    private String officialDownload;

    /**
     * 浏览器驱动下载地址
     */
    @Schema(description = "浏览器驱动下载地址")
    @ExcelProperty(value = "浏览器驱动下载地址", order = 6)
    private String driverDownload;

    /**
     * 浏览器程序路径
     */
    @Schema(description = "浏览器程序路径")
    @ExcelProperty(value = "浏览器程序路径", order = 7)
    private String exePath;

    /**
     * 浏览器驱动路径
     */
    @Schema(description = "浏览器驱动路径")
    @ExcelProperty(value = "浏览器驱动路径", order = 8)
    private String driverPath;

    /**
     * 浏览器配置文件路径
     */
    @Schema(description = "浏览器配置文件路径")
    @ExcelProperty(value = "浏览器配置文件路径", order = 9)
    private String profilePath;

    /**
     * 浏览器描述
     */
    @Schema(description = "浏览器描述")
    @ExcelProperty(value = "浏览器描述", order = 10)
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 11)
    private StatusTypeEnum status;

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