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
 * 自动化管理-环境配置查询条件
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Data
@Schema(description = "自动化管理-环境配置查询条件")
public class AutomationEnvironmentConfigQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境ID
     */
    @Schema(description = "环境ID")
    @Query(type = QueryType.LIKE)
    private String id;

    /**
     * 环境类型
     */
    @Schema(description = "环境类型")
    @Query(type = QueryType.EQ)
    private String type;

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
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}