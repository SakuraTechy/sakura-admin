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
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String copyId;
    private String name;
    private String remark;
    private String cancel;
    private String type;

    private Integer order;
    private Integer sortType;
    private Integer itemOrder;

    private CaseDO dragNode;     // 被拖拽的节点
    private CaseDO dropNode;     // 放置的目标节点
    private Integer dropPosition; // 放置位置(-1:上方, 0:内部, 1:下方)

    private String stepMsg;
    private StepDO step;
    private List<StepDO> stepList;

    private StatusTypeEnum status;
}
