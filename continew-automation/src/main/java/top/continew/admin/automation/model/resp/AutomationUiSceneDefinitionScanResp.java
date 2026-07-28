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

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 阶段 0/7 使用的只读定义扫描结果，不执行任何修复。 */
@Data
public class AutomationUiSceneDefinitionScanResp {
    private int sceneCount;
    private int issueCount;
    private List<SceneIssue> issues = new ArrayList<>();

    @Data
    public static class SceneIssue {
        private Long sceneDbId;
        private String sceneId;
        private String code;
        private String path;
        private String detail;

        public SceneIssue(Long sceneDbId, String sceneId, String code, String path, String detail) {
            this.sceneDbId = sceneDbId;
            this.sceneId = sceneId;
            this.code = code;
            this.path = path;
            this.detail = detail;
        }
    }
}
