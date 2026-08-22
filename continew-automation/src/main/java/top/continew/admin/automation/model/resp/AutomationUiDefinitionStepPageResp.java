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

package top.continew.admin.automation.model.resp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

/** 大定义步骤节点分页；只读取指定用例。 */
@Data
public class AutomationUiDefinitionStepPageResp {
    private Long sceneDbId;
    private Long definitionVersion;
    private Long projectionId;
    private String caseId;
    private Integer page;
    private Integer size;
    private Long total;
    private List<JsonNode> items;
}
