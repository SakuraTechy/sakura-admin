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

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 批量查询 UI 自动化场景摘要请求。 */
@Data
public class AutomationUiSceneSummariesReq {

    @NotEmpty(message = "场景数据库 ID 不能为空")
    @Size(max = 100, message = "场景数据库 ID 单次最多 100 个")
    private List<@Positive(message = "场景数据库 ID 必须为正整数") Long> sceneDbIds;

    @Valid
    private AutomationUiExecutionScopeReq executionScope;
}
