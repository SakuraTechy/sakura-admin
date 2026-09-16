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

package top.continew.admin.automation.model.resp.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 视觉识别响应
 *
 * @author Codex
 * @since 2026/09/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 视觉识别响应")
public class AutomationAiVisionRecognizeResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 识别出的文本
     */
    @Schema(description = "识别出的文本内容", example = "A3b9K")
    private String text;

    /**
     * 识别置信度
     */
    @Schema(description = "识别置信度（0-1）", example = "0.95")
    private Double confidence;
}
