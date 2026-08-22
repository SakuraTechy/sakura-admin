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

package top.continew.admin.automation.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 最新执行和历史分页使用的显式来源作用域。 */
@Data
public class AutomationUiExecutionScopeReq {

    @NotBlank(message = "执行记录来源不能为空")
    @Size(max = 16, message = "执行记录来源长度不能超过 16")
    private String recordSource;

    @Positive(message = "测试计划 ID 必须为正整数")
    private Long testPlanId;

    @Positive(message = "测试报告 ID 必须为正整数")
    private Long testReportId;

    @PositiveOrZero(message = "构建号不能为负数")
    private Integer buildNumber;
}
