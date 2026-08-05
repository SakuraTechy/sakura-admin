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

package top.continew.admin.test.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "测试计划查询条件")
public class TestPlanQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Query(type = QueryType.LIKE)
    private String id;

    @Query(type = QueryType.EQ)
    private Long projectId;

    @Query(type = QueryType.EQ)
    private Long versionId;

    @Query(type = QueryType.LIKE)
    private String name;

    @Query(type = QueryType.LIKE)
    private String type;

    @Query(type = QueryType.EQ)
    private String status;

    /**
     * 创建人（用户 ID，与 {@code BaseDO#createUser} 一致）
     */
    @Schema(description = "创建人（用户 ID）")
    @Query(type = QueryType.EQ)
    private Long createUser;

    /**
     * 创建时间范围
     */
    @Schema(description = "创建时间", example = "2024-01-01T00:00:00,2024-01-31T23:59:59")
    @Size(max = 2, message = "创建时间必须是一个范围")
    @Query(type = QueryType.BETWEEN)
    private List<LocalDateTime> createTime;

    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
