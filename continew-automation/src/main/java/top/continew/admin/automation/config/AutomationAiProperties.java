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

package top.continew.admin.automation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 配置属性
 *
 * @author Claude Code
 * @since 2026/09/12
 */
@Data
@Component
@ConfigurationProperties(prefix = "automation.ai")
public class AutomationAiProperties {

    /**
     * 是否启用 AI 能力
     */
    private Boolean enabled = false;

    /**
     * AI 提供商（anthropic、openai、custom）
     */
    private String provider = "anthropic";

    /**
     * API 端点地址
     */
    private String endpoint = "https://api.anthropic.com/v1/messages";

    /**
     * 模型名称
     */
    private String model = "claude-3-5-sonnet-20241022";

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 是否使用结构化输出
     */
    private Boolean structuredOutput = true;

    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectTimeout = 10000;

    /**
     * 请求超时时间（毫秒）
     */
    private Integer requestTimeout = 60000;

    /**
     * 总超时时间（毫秒）
     */
    private Integer totalTimeout = 120000;

    /**
     * 最大输出 token 数
     */
    private Integer maxOutputTokens = 4096;

    @Override
    public String toString() {
        return "AutomationAiProperties{" + "enabled=" + enabled + ", provider='" + provider + '\'' + ", endpoint='" + endpoint + '\'' + ", model='" + model + '\'' + ", apiKey='***'" + ", structuredOutput=" + structuredOutput + ", connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout + ", totalTimeout=" + totalTimeout + ", maxOutputTokens=" + maxOutputTokens + '}';
    }
}
