package top.continew.admin.project.model.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.time.*;

/**
 * 项目管理-环境配置查询条件
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Data
@Schema(description = "项目管理-环境配置查询条件")
public class ProjectEnvironmentConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * 环境ID
     */
    @Schema(description = "环境ID")
    @Query(type = QueryType.LIKE)
    private Long id;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @Query(type = QueryType.EQ)
    private Long projectId;

    /**
     * 环境名称
     */
    @Schema(description = "环境名称")
    @Query(type = QueryType.LIKE)
    private String name;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @Query(type = QueryType.EQ)
    private DisEnableStatusEnum status;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    @Query(type = QueryType.EQ)
    private Integer delFlag = 1;
}