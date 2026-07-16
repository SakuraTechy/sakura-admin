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

package top.continew.admin.automation.model.req.playwright;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Playwright Runner 单用例任务请求。
 */
@Data
public class AutomationPlaywrightRunnerJobReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** caseKey 格式为业务 sceneId:caseId，兼容数据库场景主键。 */
    @NotBlank(message = "Playwright Runner caseKey 不能为空")
    private String caseKey;

    /** 产品环境只用于生成本次执行快照，不会改写场景主数据。 */
    @NotNull(message = "Playwright Runner 产品环境不能为空")
    private Long projectEnvironmentId;

    /** 从零开始的步骤下标。 */
    @Min(value = 0, message = "Playwright Runner 起始步骤不能小于 0")
    private Integer startStep;

    /** token、产物路径和登录态仍由服务端管理，前端只能覆盖白名单参数。 */
    @Valid
    private AutomationPlaywrightRunnerOptionsReq options = new AutomationPlaywrightRunnerOptionsReq();
}
