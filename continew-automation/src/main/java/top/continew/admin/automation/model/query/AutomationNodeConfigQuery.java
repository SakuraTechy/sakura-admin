package top.continew.admin.automation.model.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.time.*;

/**
 * 自动化管理-节点配置查询条件
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Data
@Schema(description = "自动化管理-节点配置查询条件")
public class AutomationNodeConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    @Schema(description = "节点ID")
    @Query(type = QueryType.LIKE)
    private String id;

    /**
     * 所属Jenkins
     */
    @Schema(description = "所属Jenkins")
    @Query(type = QueryType.EQ)
    private Long jenkinsId;

    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    @Query(type = QueryType.LIKE)
    private String name;

    /**
     * 节点在线状态
     */
    @Schema(description = "节点在线状态")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum offlineStatus;

    /**
     * 节点使用状态
     */
    @Schema(description = "节点使用状态")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum idleStatus;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}