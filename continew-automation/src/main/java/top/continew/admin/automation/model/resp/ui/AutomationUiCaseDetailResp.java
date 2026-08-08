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

package top.continew.admin.automation.model.resp.ui;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;

/** 统一用例详情 DTO；不直接暴露 CaseDO 的树操作临时字段。 */
@Data
public class AutomationUiCaseDetailResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String remark;
    private String type;
    private Integer order;
    private StatusTypeEnum status;
    private Long definitionVersion;
    private AutomationUiCaseExecutionConfigResp executionConfig;
    private AutomationUiCaseOriginResp origin;
    private String normalizedSource;
    private String compositionSource;
    private Map<String, Integer> sourceCounts;
    private List<AutomationUiStepDetailResp> steps;
}
