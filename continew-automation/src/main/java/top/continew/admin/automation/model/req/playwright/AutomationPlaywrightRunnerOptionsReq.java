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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Playwright Runner 单次任务白名单配置。
 */
@Data
public class AutomationPlaywrightRunnerOptionsReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Pattern(regexp = "chromium|firefox|webkit", message = "Runner 浏览器仅支持 chromium、firefox 或 webkit")
    private String browser = "chromium";

    @Pattern(regexp = "smooth|high|ultra|8k", message = "Runner 实时画面质量配置无效")
    private String liveFrameQuality = "smooth";

    @Pattern(regexp = "isolated|reuse-auth|reuse-browser", message = "Runner 用例会话模式配置无效")
    private String sessionMode = "isolated";

    private Boolean headed = false;

    private Boolean ignoreHttpsErrors = true;

    /**
     * 为空时继承录制用例的 page_error_check_enabled；显式 false 也必须传递给 Runner。
     */
    private Boolean pageErrorCheckEnabled;

    @Pattern(regexp = "off|on|retain-on-failure", message = "Runner trace 策略无效")
    private String trace = "retain-on-failure";

    @Pattern(regexp = "off|on|retain-on-failure", message = "Runner video 策略无效")
    private String video = "retain-on-failure";

    @Min(value = 1000, message = "Runner 步骤超时不能小于 1000 毫秒")
    @Max(value = 300000, message = "Runner 步骤超时不能大于 300000 毫秒")
    private Integer stepTimeoutMs = 6000;

    @Min(value = 10000, message = "Runner 用例超时不能小于 10000 毫秒")
    @Max(value = 3600000, message = "Runner 用例超时不能大于 3600000 毫秒")
    private Integer caseTimeoutMs = 600000;

    @Min(value = 0, message = "Runner 慢放时间不能小于 0")
    @Max(value = 10000, message = "Runner 慢放时间不能大于 10000 毫秒")
    private Integer slowMoMs = 0;

    @Min(value = 0, message = "Runner 结束停留时间不能小于 0")
    @Max(value = 600000, message = "Runner 结束停留时间不能大于 600000 毫秒")
    private Integer finishDelayMs = 0;
}
