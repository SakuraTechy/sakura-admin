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

package top.continew.admin.automation.model.req.infrastructure;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 不确定任务的人工核验结论；不接受自动重试指令。 */
@Data
public class AutomationInfrastructureTaskDispositionReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "核验结论不能为空")
    @Pattern(regexp = "confirmed_succeeded|confirmed_failed", message = "核验结论仅支持 confirmed_succeeded 或 confirmed_failed")
    private String resolution;

    @NotBlank(message = "核验说明不能为空")
    @Size(max = 1000, message = "核验说明不能超过 1000 个字符")
    private String verificationNote;
}
