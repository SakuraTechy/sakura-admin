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

package top.continew.admin.automation.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 变量提取错误码枚举
 *
 * @author liuzhi
 * @since 2026/09/12
 */
@Getter
@RequiredArgsConstructor
public enum AiVariableExtractErrorCode {

    /**
     * AI 能力未启用
     */
    AI_DISABLED(400, "AI 能力未启用"),

    /**
     * AI 服务未配置
     */
    AI_NOT_CONFIGURED(500, "AI 服务未配置"),

    /**
     * AI 输入无效
     */
    AI_INPUT_INVALID(400, "AI 输入无效"),

    /**
     * AI 请求频率受限
     */
    AI_RATE_LIMITED(429, "AI 请求频率受限"),

    /**
     * AI 服务提供方不可用
     */
    AI_PROVIDER_UNAVAILABLE(503, "AI 服务提供方不可用"),

    /**
     * AI 服务提供方超时
     */
    AI_PROVIDER_TIMEOUT(504, "AI 服务提供方超时"),

    /**
     * AI 输出无效
     */
    AI_OUTPUT_INVALID(500, "AI 输出无效"),

    /**
     * AI 提取规则无效
     */
    AI_RULE_INVALID(400, "AI 提取规则无效"),

    /**
     * AI 提取规则无匹配结果
     */
    AI_RULE_NO_MATCH(404, "AI 提取规则无匹配结果");

    /**
     * HTTP 状态码
     */
    private final int httpStatus;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 获取错误码名称
     */
    public String getCode() {
        return this.name();
    }
}
