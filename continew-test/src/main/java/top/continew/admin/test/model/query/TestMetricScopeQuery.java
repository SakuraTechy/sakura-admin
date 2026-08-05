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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 测试度量查询范围。
 */
@Data
@Schema(description = "测试度量查询范围")
public class TestMetricScopeQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目 ID 不能为空")
    @Schema(description = "项目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目版本 ID")
    private Long versionId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "开始日期（含）", example = "2026-07-01")
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "结束日期（含）", example = "2026-07-31")
    private LocalDate endDate;

    @Schema(description = "执行引擎")
    private String executionEngine;

    @Schema(description = "触发方式")
    private String triggerType;

    @Schema(description = "项目环境 ID")
    private Long environmentId;
}
