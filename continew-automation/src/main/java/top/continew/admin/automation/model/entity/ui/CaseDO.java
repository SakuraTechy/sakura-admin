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

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    /** 兼容接口也必须携带读取时的场景定义版本。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long expectedDefinitionVersion;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String copyId;
    private String name;
    private String remark;
    private String cancel;
    private String type;

    private Integer order;
    private Integer sortType;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer itemOrder;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private CaseDO dragNode;     // 被拖拽的节点
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private CaseDO dropNode;     // 放置的目标节点
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer dropPosition; // 放置位置(-1:上方, 0:内部, 1:下方)

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String stepMsg;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private StepDO step;
    private List<StepDO> stepList;

    /** 起始地址、视口和截图策略属于用例，不从第一条步骤反推。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CaseExecutionConfigDO executionConfig;
    /** 来源追踪只读，录制/复制流程由服务端设置。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CaseOriginDO origin;

    private StatusTypeEnum status = StatusTypeEnum.ENABLE;
}
