package top.continew.admin.automation.model.entity;

import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.StatusTypeEnum;


/**
 * 自动化管理-浏览器配置实体
 *
 * @author hagyao520
 * @since 2025/05/29 15:41
 */
@Data
@TableName(value = "automation_browser_config", autoResultMap = true)
public class AutomationBrowserConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 浏览器类型
     */
    private String type;

    /**
     * 浏览器版本
     */
    private String version;

    /**
     * 浏览器名称
     */
    private String name;

    /**
     * 浏览器程序下载地址
     */
    private String officialDownload;

    /**
     * 浏览器驱动下载地址
     */
    private String driverDownload;

    /**
     * 浏览器程序路径
     */
    private String exePath;

    /**
     * 浏览器驱动路径
     */
    private String driverPath;

    /**
     * 浏览器配置文件路径
     */
    private String profilePath;

    /**
     * 浏览器描述
     */
    private String description;

    /**
     * 状态
     */
    private StatusTypeEnum status;

    /**
     * 更新人IP
     */
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
