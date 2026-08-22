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

/** 定义投影状态窄行，不包含任何节点正文。 */
@Data
public class AutomationUiDefinitionProjectionStateRow {
    private Long projectionId;
    private Long definitionVersion;
    private String sourceSha256;
    private String status;
    private Integer caseCount;
    private Integer stepCount;
    private String buildToken;
    private Long publishedProjectionId;
    private Boolean retryable;
    private String errorId;
}
