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

package top.continew.admin.automation.model.req.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 步骤规划请求
 *
 * @author Codex
 * @since 2026/09/13
 */
@Data
@Schema(description = "AI 步骤规划请求")
public class AutomationAiStepPlanReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自然语言指令
     */
    @Schema(description = "自然语言指令", example = "点击登录按钮")
    @NotBlank(message = "自然语言指令不能为空")
    private String instruction;

    /**
     * 页面结构（JSON 字符串）
     */
    @Schema(description = "页面结构（JSON 字符串）")
    @NotBlank(message = "页面结构不能为空")
    private String pageStructure;

    /**
     * 调试模式
     */
    @Schema(description = "调试模式", example = "false")
    private Boolean debug;
}
