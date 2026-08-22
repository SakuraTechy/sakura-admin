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

import java.util.List;

import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;

/** Definition 首次窄查询结果，不包含 case_list 正文。 */
@Data
public class AutomationUiSceneDefinitionRow {

    private Long sceneDbId;
    private String sceneKey;
    private String name;
    private String description;
    private Long projectDbId;
    private String projectName;
    private Long versionDbId;
    private String versionName;
    private Long moduleDbId;
    private String modulePath;
    private String level;
    private StatusTypeEnum status;
    private List<Object> tags;
    private Long definitionVersion;
    private Long definitionBytes;
    private Long definitionStepCount;
}
