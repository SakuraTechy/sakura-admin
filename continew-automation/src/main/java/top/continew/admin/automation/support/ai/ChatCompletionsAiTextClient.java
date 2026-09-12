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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.config.AutomationAiProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Chat Completions API 实现。
 *
 * <p>基于 JDK HttpClient，支持 OpenAI Chat Completions 格式的结构化输出。
 * 支持 json_schema 和 json_object 两种响应格式。</p>
 *
 * @author Claude Code
 * @since 2026/09/12
 */
@Component
public class ChatCompletionsAiTextClient implements AiTextClient {

    private static final int MAX_RESPONSE_SIZE = 64 * 1024; // 64 KiB
    private final AutomationAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ChatCompletionsAiTextClient(AutomationAiProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
            .build();
    }

    @Override
    public JsonNode generateStructuredOutput(String systemPrompt,
                                             String userContent,
                                             JsonNode schema) throws AiProviderException {
        try {
            String requestBody = buildRequestBody(systemPrompt, userContent, schema);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getEndpoint()))
                .timeout(Duration.ofMillis(properties.getTotalTimeout()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            // 检查响应大小
            if (response.body().length > MAX_RESPONSE_SIZE) {
                throw new AiProviderException(properties.getProvider(), "响应超过最大限制 " + MAX_RESPONSE_SIZE + " 字节");
            }

            String responseBody = new String(response.body(), StandardCharsets.UTF_8);

            // 处理 HTTP 错误
            if (response.statusCode() >= 400) {
                handleErrorResponse(response.statusCode(), responseBody);
            }

            return parseSuccessResponse(responseBody);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(properties.getProvider(), "请求被中断", e, "INTERRUPTED", true);
        } catch (IOException e) {
            throw new AiProviderException(properties.getProvider(), "网络请求失败: " + e
                .getMessage(), e, "NETWORK_ERROR", true);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException(properties.getProvider(), "未预期的错误: " + e.getMessage(), e);
        }
    }

    /**
     * 构建请求体，支持 json_schema 和 json_object 模式
     */
    private String buildRequestBody(String systemPrompt,
                                    String userContent,
                                    JsonNode schema) throws AiProviderException {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", properties.getModel());
            root.put("max_tokens", properties.getMaxOutputTokens());

            // 构建 messages
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userContent);

            root.putArray("messages").add(systemMessage).add(userMessage);

            // 构建 response_format
            ObjectNode responseFormat = root.putObject("response_format");
            if (schema != null && schema.has("properties")) {
                // json_schema 模式
                responseFormat.put("type", "json_schema");
                ObjectNode jsonSchema = responseFormat.putObject("json_schema");
                jsonSchema.put("name", "structured_output");
                jsonSchema.put("strict", true);
                jsonSchema.set("schema", schema);
            } else {
                // json_object 模式
                responseFormat.put("type", "json_object");
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiProviderException(properties.getProvider(), "构建请求体失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理错误响应
     */
    private void handleErrorResponse(int statusCode, String responseBody) throws AiProviderException {
        String errorCode = "HTTP_" + statusCode;
        boolean retryable = false;
        String message = "请求失败";

        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            if (errorJson.has("error")) {
                JsonNode error = errorJson.get("error");
                if (error.has("message")) {
                    message = error.path("message").asText();
                }
                if (error.has("code")) {
                    errorCode = error.path("code").asText();
                }
                if (error.has("type")) {
                    String type = error.path("type").asText();
                    // 速率限制和服务端错误可重试
                    retryable = type.contains("rate_limit") || type.contains("overloaded") || statusCode >= 500;
                }
            }
        } catch (Exception ignored) {
            // 解析失败时使用默认错误信息
            message = "HTTP " + statusCode + ": " + responseBody.substring(0, Math.min(200, responseBody.length()));
        }

        // 429 和 5xx 错误可重试
        if (statusCode == 429 || statusCode >= 500) {
            retryable = true;
        }

        throw new AiProviderException(properties.getProvider(), message, errorCode, retryable);
    }

    /**
     * 解析成功响应，提取结构化输出
     */
    private JsonNode parseSuccessResponse(String responseBody) throws AiProviderException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 提取 choices[0].message.content
            if (!root.has("choices") || !root.get("choices").isArray() || root.get("choices").isEmpty()) {
                throw new AiProviderException(properties.getProvider(), "响应格式错误: 缺少 choices 数组");
            }

            JsonNode firstChoice = root.get("choices").get(0);
            if (!firstChoice.has("message") || !firstChoice.get("message").has("content")) {
                throw new AiProviderException(properties.getProvider(), "响应格式错误: 缺少 message.content");
            }

            String content = firstChoice.get("message").get("content").asText();

            // 解析 content 为 JSON
            return objectMapper.readTree(content);

        } catch (IOException e) {
            throw new AiProviderException(properties.getProvider(), "解析响应失败: " + e.getMessage(), e);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException(properties.getProvider(), "处理响应时出错: " + e.getMessage(), e);
        }
    }
}
