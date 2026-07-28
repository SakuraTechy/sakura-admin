/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.automation.model.query;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.annotation.QueryIgnore;
import top.continew.starter.data.core.enums.QueryType;

import java.time.*;
import java.util.List;

/**
 * 自动化管理-UI自动化场景查询条件
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Data
@Schema(description = "自动化管理-UI自动化场景查询条件")
public class AutomationUiSceneQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Schema(description = "主键ID")
    @Query(type = QueryType.LIKE)
    private Long id;

    /**
     * 场景ID
     */
    @Schema(description = "场景ID")
    @Query(type = QueryType.LIKE)
    private String sceneId;

    /**
     * 场景名称
     */
    @Schema(description = "场景名称")
    @Query(type = QueryType.LIKE)
    private String name;

    /**
     * 所属项目ID
     */
    @Schema(description = "所属项目ID")
    @Query(type = QueryType.EQ)
    private Long projectId;

    /**
     * 所属项目版本ID
     */
    @Schema(description = "所属项目版本ID")
    @Query(type = QueryType.EQ)
    private Long versionId;

    /**
     * 所属模块ID
     */
    @Schema(description = "所属模块ID")
    @Query(type = QueryType.EQ)
    private Long moduleId;

    /**
     * 场景等级
     */
    @Schema(description = "场景等级")
    @Query(type = QueryType.EQ)
    private String level;

    /**
     * 执行状态
     */
    @Schema(description = "执行状态")
    @Query(type = QueryType.EQ)
    private String executeStatus;

    /**
     * 执行结果
     */
    @Schema(description = "执行结果")
    @QueryIgnore
    private String executeResult;

    /**
     * 创建人
     */
    @Schema(description = "创建人")
    @Query(type = QueryType.EQ)
    private Long createUser;

    /**
     * 修改人
     */
    @Schema(description = "修改人")
    @Query(type = QueryType.EQ)
    private Long updateUser;

    /**
     * 场景状态
     */
    @Schema(description = "场景状态")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum status;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2023-08-08 00:00:00,2023-08-08 23:59:59")
    @Size(max = 2, message = "创建时间必须是一个范围")
    @Query(type = QueryType.BETWEEN)
    private List<LocalDateTime> createTime;

    /**
     * 排除的场景主键 ID 列表（如测试计划已关联场景，由 Service 层转换为 id NOT IN）
     */
    @Schema(description = "排除的场景主键 ID 列表")
    @QueryIgnore
    private List<Long> excludeIds;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;

    /**
     * 测试计划ID
     */
    @Schema(description = "测试计划ID")
    @QueryIgnore
    private String testPlanId;

    /**
     * 正式测试报告 ID；用于隔离同一计划的多次执行。
     */
    @Schema(description = "测试报告ID")
    @QueryIgnore
    private String testReportId;

    /**
     * 构建号
     */
    @Schema(description = "构建号")
    @QueryIgnore
    private Integer buildNumber;

    /**
     * 执行结果类型: report-计划执行, debug-调试
     */
    @Schema(description = "执行结果类型: report-计划执行, debug-调试")
    @QueryIgnore
    private String executeResultType;

    /**
     * 仅场景用例树等定义接口启用；普通列表必须避免读取 case_list。
     */
    @Schema(hidden = true)
    @QueryIgnore
    private Boolean includeDefinition;
}
