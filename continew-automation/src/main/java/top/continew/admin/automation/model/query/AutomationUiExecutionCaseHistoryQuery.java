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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 按场景和定义用例查询执行历史，避免先分页批次再逐批拼接。 */
@Data
public class AutomationUiExecutionCaseHistoryQuery {

    @NotNull(message = "场景数据库 ID 不能为空")
    @Positive(message = "场景数据库 ID 必须为正整数")
    private Long sceneDbId;

    @NotBlank(message = "用例 ID 不能为空")
    @Size(max = 128, message = "用例 ID 长度不能超过 128")
    private String caseId;

    @NotBlank(message = "recordSource 不能为空")
    @Size(max = 16, message = "recordSource 长度不能超过 16")
    private String recordSource;

    @Positive(message = "测试计划 ID 必须为正整数")
    private Long testPlanId;

    @Positive(message = "测试报告 ID 必须为正整数")
    private Long testReportId;

    @Positive(message = "构建号必须为正整数")
    private Integer buildNumber;
}
