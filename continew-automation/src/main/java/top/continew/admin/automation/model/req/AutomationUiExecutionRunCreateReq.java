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

package top.continew.admin.automation.model.req;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 冻结整次调试的有序场景范围，不接收用例正文、执行凭据或报告归属。 */
@Data
public class AutomationUiExecutionRunCreateReq {
    @NotEmpty
    @Size(max = 10000)
    private List<@NotNull @Positive Long> sceneIds;
    @NotNull
    @Positive
    private Long projectEnvironmentId;
    @NotNull
    @Pattern(regexp = "playwright-runner|extension-cdp")
    private String executionType;
}
