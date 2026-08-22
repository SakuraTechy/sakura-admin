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

package top.continew.admin.automation.model.entity.ui;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class StepDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pid;

    /** 兼容接口也必须携带读取时的场景定义版本。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long expectedDefinitionVersion;

    private String id;

    private String name;

    private String remark;

    private String type;

    private String operationType;

    private String operationName;

    private String operationValue;

    private String setting;

    /** 步骤失败后是否仅跳过当前步骤并继续执行后续步骤。 */
    private Boolean continueOnFailure = false;

    private List<Config> configList;

    @Data
    public static class Config {
        private String paramsName;

        private String paramsValue;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String copyId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String copyPid;

    private Integer order;
    private Integer sortType;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer itemOrder;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private StepDO dragNode;     // 被拖拽的节点
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private StepDO dropNode;     // 放置的目标节点
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer dropPosition; // 放置位置(-1:上方, 0:内部, 1:下方)

    private StatusTypeEnum status = StatusTypeEnum.ENABLE;
}
