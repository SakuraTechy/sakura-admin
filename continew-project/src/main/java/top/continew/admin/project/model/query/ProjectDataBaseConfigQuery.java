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

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.time.*;

/**
 * 项目管理-数据库配置查询条件
 *
 * @author hagyao520
 * @since 2025/05/08 18:00
 */
@Data
@Schema(description = "项目管理-数据库配置查询条件")
public class ProjectDataBaseConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据库ID
     */
    @Schema(description = "数据库ID")
    @Query(type = QueryType.LIKE)
    private Long id;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @Query(type = QueryType.EQ)
    private Long projectId;

    /**
     * 数据库类型
     */
    @Schema(description = "数据库类型")
    @Query(type = QueryType.EQ)
    private String type;

    /**
     * 数据库IP
     */
    @Schema(description = "数据库IP")
    @Query(type = QueryType.LIKE)
    private String ip;

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