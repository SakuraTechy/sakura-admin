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
 * AI 视觉识别请求
 *
 * @author Codex
 * @since 2026/09/13
 */
@Data
@Schema(description = "AI 视觉识别请求")
public class AutomationAiVisionRecognizeReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 图片数据（base64 或 URL）
     */
    @Schema(description = "图片数据（base64 或 URL）", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA...")
    @NotBlank(message = "图片数据不能为空")
    private String image;

    /**
     * 识别模式
     */
    @Schema(description = "识别模式（captcha: 验证码识别, text: 通用文本识别）", example = "captcha")
    private String mode;

    /**
     * 提示信息（可选）
     */
    @Schema(description = "提示信息（可选，用于指导识别）", example = "请识别这个验证码")
    private String prompt;
}
