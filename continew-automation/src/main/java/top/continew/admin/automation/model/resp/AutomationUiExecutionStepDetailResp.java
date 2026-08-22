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

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 展开单个步骤后返回的受控诊断详情。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AutomationUiExecutionStepDetailResp extends AutomationUiExecutionStepResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private String locatorValue;
    private Object diagnostics;
}
