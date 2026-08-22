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

/** 投影用例节点行；正文保持为 JSON 字符串，避免 MyBatis 隐式反序列化扩大热路径。 */
@Data
public class AutomationUiDefinitionCaseReadRow {
    private Long id;
    private String caseId;
    private Integer caseIndex;
    private String caseName;
    private Integer stepCount;
    private String caseJson;
    private String nodeSha256;
}
