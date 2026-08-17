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
 * 扩展 CDP 批次回放配置。
 *
 * <p>会话模式是批次级事实，不能由单个 Case 或环境默认值覆盖。</p>
 */
@Data
public class AutomationCdpPlaybackOptionsReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Pattern(regexp = "current-profile|managed-context", message = "CDP 浏览器会话来源配置无效")
    private String browserSessionSource = "current-profile";

    @Pattern(regexp = "legacy-profile|isolated|reuse-auth|reuse-browser", message = "CDP 用例会话模式配置无效")
    private String sessionMode = "legacy-profile";

    private Boolean ignoreHttpsErrors = false;

    @Pattern(regexp = "maximized|current|custom", message = "CDP 执行窗口模式配置无效")
    private String windowSizeMode = "maximized";

    @Min(value = 320, message = "CDP 视口宽度不能小于 320")
    @Max(value = 10000, message = "CDP 视口宽度不能大于 10000")
    private Integer viewportWidth = 1920;

    @Min(value = 320, message = "CDP 视口高度不能小于 320")
    @Max(value = 10000, message = "CDP 视口高度不能大于 10000")
    private Integer viewportHeight = 1080;

    private Boolean pageErrorCheckEnabled = true;
}
