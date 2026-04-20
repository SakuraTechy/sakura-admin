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

package top.continew.admin.project.model.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.time.*;

/**
 * 项目管理-模块配置查询条件
 *
 * @author hagyao520
 * @since 2025/06/06 17:44
 */
@Data
@Schema(description = "项目管理-模块配置查询条件")
public class ProjectModuleConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模块ID
     */
    @Schema(description = "模块ID")
    @Query(type = QueryType.LIKE)
    private Long id;

    /**
     * 项目ID
     */
    @Schema(description = "项目ID")
    @Query(type = QueryType.EQ)
    private Long projectId;

    /**
     * 父模块ID
     */
    @Schema(description = "父模块ID")
    @Query(type = QueryType.EQ)
    private Long parentId;

    /**
     * 版本ID
     */
    @Schema(description = "版本ID")
    @Query(type = QueryType.EQ)
    private Long versionId;

    /**
     * 模块名称
     */
    @Schema(description = "模块名称")
    @Query(type = QueryType.EQ)
    private String name;

    /**
     * 模块状态
     */
    @Schema(description = "模块状态")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}