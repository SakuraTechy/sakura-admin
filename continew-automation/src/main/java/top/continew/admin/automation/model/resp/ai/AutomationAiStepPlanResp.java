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
import java.util.List;

/**
 * AI 步骤规划响应
 *
 * @author Codex
 * @since 2026/09/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 步骤规划响应")
public class AutomationAiStepPlanResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 操作列表
     */
    @Schema(description = "操作列表")
    private List<Operation> operations;

    /**
     * 规划理由
     */
    @Schema(description = "规划理由")
    private String reason;

    /**
     * 操作定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "操作定义")
    public static class Operation implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 操作类型
         */
        @Schema(description = "操作类型（click, type, select, hover, wait 等）", example = "click")
        private String type;

        /**
         * 目标元素 ID
         */
        @Schema(description = "目标元素 ID", example = "login-button")
        private String target;

        /**
         * 操作值（可选）
         */
        @Schema(description = "操作值（如输入的文本）", example = "admin")
        private String value;

        /**
         * 操作描述
         */
        @Schema(description = "操作描述", example = "点击登录按钮")
        private String description;
    }
}
