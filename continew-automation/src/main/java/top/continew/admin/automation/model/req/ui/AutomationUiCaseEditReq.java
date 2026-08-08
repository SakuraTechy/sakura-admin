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

package top.continew.admin.automation.model.req.ui;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;

/** 用例语义编辑请求；来源、步骤和 raw config 不接受客户端写入。 */
@Data
public class AutomationUiCaseEditReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    @jakarta.validation.constraints.NotBlank
    private String name;
    private String remark;
    private StatusTypeEnum status;
    private AutomationUiCaseExecutionConfigReq executionConfig;
    @NotNull
    private Long expectedDefinitionVersion;
}
