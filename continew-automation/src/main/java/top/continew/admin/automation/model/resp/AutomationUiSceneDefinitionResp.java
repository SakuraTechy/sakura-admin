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

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.common.enums.StatusTypeEnum;

/**
 * UI 自动化场景定义判别联合。
 *
 * <p>inline 与 projected 使用不同 Java 类型，避免通过 null 字段伪装响应模式。</p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract sealed class AutomationUiSceneDefinitionResp implements Serializable permits
    AutomationUiSceneDefinitionResp.Inline, AutomationUiSceneDefinitionResp.Projected {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private Long maskPolicyVersion;
    private String representationScopeDigest;
    private List<String> requiredCapabilities;
    private List<String> supportedExecutors;
    private Boolean requiresInfrastructure;

    public abstract String getMode();

    /** 小定义的内联表示，只有该分支允许出现 caseList。 */
    @Getter
    @Setter
    @EqualsAndHashCode(callSuper = true)
    public static final class Inline extends AutomationUiSceneDefinitionResp {

        @Serial
        private static final long serialVersionUID = 1L;

        private List<CaseDO> caseList;

        @Override
        public String getMode() {
            return "inline";
        }
    }

    /** 大定义的只读投影表示，该分支从类型上禁止携带完整 caseList。 */
    @Getter
    @Setter
    @EqualsAndHashCode(callSuper = true)
    public static final class Projected extends AutomationUiSceneDefinitionResp {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long projectionId;
        private Integer caseCount;
        private Integer stepCount;
        private String projectionStatus;

        @Override
        public String getMode() {
            return "projected";
        }
    }
}
