package top.continew.admin.project.model.query;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.io.Serial;
import java.io.Serializable;
import java.time.*;

/**
 * 项目配置查询条件
 *
 * @author hagyao520
 * @since 2025/04/15 11:56
 */
@Data
@Schema(description = "项目配置查询条件")
public class ProjectConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目ID
     */
    @Schema(description = "项目ID")
    @Query(type = QueryType.LIKE)
    private Long id;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    @Query(type = QueryType.LIKE)
    private String name;

    /**
     * 项目简称
     */
    @Schema(description = "项目简称")
    @Query(type = QueryType.LIKE)
    private String abbreviate;

    /**
     * 状态（0禁用 1启用）
     */
    @Schema(description = "状态（0禁用 1启用）")
    @Query(type = QueryType.EQ)
    private DisEnableStatusEnum status;
}