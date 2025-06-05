package top.continew.admin.automation.model.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.time.*;

/**
 * 自动化管理-浏览器配置查询条件
 *
 * @author hagyao520
 * @since 2025/05/29 15:41
 */
@Data
@Schema(description = "自动化管理-浏览器配置查询条件")
public class AutomationBrowserConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * 浏览器ID
     */
    @Schema(description = "浏览器ID")
    @Query(type = QueryType.LIKE)
    private String id;

    /**
     * 浏览器类型
     */
    @Schema(description = "浏览器类型")
    @Query(type = QueryType.EQ)
    private String type;

    /**
     * 浏览器名称
     */
    @Schema(description = "浏览器名称")
    @Query(type = QueryType.LIKE)
    private String name;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @Query(type = QueryType.EQ)
    private DisEnableStatusEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}