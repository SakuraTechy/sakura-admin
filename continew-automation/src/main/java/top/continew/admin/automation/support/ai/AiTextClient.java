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

package top.continew.admin.automation.support.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 文本生成客户端接口。
 *
 * <p>封装结构化输出能力，支持多供应商扩展（Anthropic、OpenAI、本地模型等）。
 * 由具体实现处理鉴权、重试、限流和错误转换。</p>
 */
public interface AiTextClient {

    /**
     * 生成结构化输出。
     *
     * @param systemPrompt 系统提示词，定义 AI 的角色和行为约束
     * @param userContent  用户输入内容
     * @param schema       输出结构的 JSON Schema，供应商将强制 AI 按此结构返回
     * @return 结构化输出的 JSON 节点
     * @throws AiProviderException 当调用失败时抛出，包含供应商信息、错误码和是否可重试
     */
    JsonNode generateStructuredOutput(String systemPrompt,
                                      String userContent,
                                      JsonNode schema) throws AiProviderException;
}
