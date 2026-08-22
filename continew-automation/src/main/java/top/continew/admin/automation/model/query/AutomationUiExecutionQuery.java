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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** UI 自动化执行历史查询。 */
@Data
public class AutomationUiExecutionQuery {

    @NotNull(message = "场景数据库 ID 不能为空")
    @Positive(message = "场景数据库 ID 必须为正整数")
    private Long sceneDbId;

    @NotBlank(message = "recordSource 不能为空")
    @Size(max = 16, message = "recordSource 长度不能超过 16")
    private String recordSource;

    @Positive(message = "测试计划 ID 必须为正整数")
    private Long testPlanId;

    @Positive(message = "测试报告 ID 必须为正整数")
    private Long testReportId;

    @PositiveOrZero(message = "构建号不能为负数")
    private Integer buildNumber;

    @Size(max = 32, message = "执行状态长度不能超过 32")
    private String status;

    @Size(max = 32, message = "执行结果长度不能超过 32")
    private String result;

    /** 使用 start 开启游标模式，后续请求传服务端签发的不透明游标。 */
    @Size(max = 2048, message = "cursor 长度不能超过 2048")
    private String cursor;
}
