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
 * 自动化管理-Jenkins配置查询条件
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Data
@Schema(description = "自动化管理-Jenkins配置查询条件")
public class AutomationJenkinsConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * ID
     */
    @Schema(description = "ID")
    @Query(type = QueryType.LIKE)
    private String id;

    /**
     * IP
     */
    @Schema(description = "IP")
    @Query(type = QueryType.LIKE)
    private String ip;

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