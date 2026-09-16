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

package top.continew.admin.automation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.enums.AiVariableExtractErrorCode;
import top.continew.admin.automation.model.req.ai.AutomationAiVisionRecognizeReq;
import top.continew.admin.automation.model.resp.ai.AutomationAiVisionRecognizeResp;
import top.continew.admin.automation.service.AutomationAiVisionRecognizeService;
import top.continew.admin.automation.support.ai.AiProviderException;
import top.continew.admin.automation.support.ai.AiTextClient;
import top.continew.starter.core.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 视觉识别服务实现
 *
 * @author Codex
 * @since 2026/09/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationAiVisionRecognizeServiceImpl implements AutomationAiVisionRecognizeService {

    private static final String SYSTEM_PROMPT_RESOURCE = "ai/vision-recognize-system-prompt.txt";

    private final AiTextClient aiTextClient;
    private final ObjectMapper objectMapper;

    @Value("${automation.ai.enabled:false}")
    private boolean aiEnabled;

    @Override
    public AutomationAiVisionRecognizeResp recognizeVision(AutomationAiVisionRecognizeReq req) {
        if (!aiEnabled) {
            log.warn("AI 能力未启用，无法进行视觉识别");
            throw new BusinessException(AiVariableExtractErrorCode.AI_DISABLED.getMessage());
        }

        if (aiTextClient == null) {
            log.error("AI 服务未配置，无法进行视觉识别");
            throw new BusinessException(AiVariableExtractErrorCode.AI_NOT_CONFIGURED.getMessage());
        }

        String systemPrompt = loadSystemPrompt();
        String userPrompt = buildUserPrompt(req);
        log.info("开始视觉识别，mode={}, imageLength={}", req.getMode(), req.getImage().length());

        JsonNode schema = buildOutputSchema();

        // 构建多模态消息
        List<Map<String, Object>> messages = buildVisionMessages(systemPrompt, userPrompt, req.getImage());

        JsonNode aiOutput;
        try {
            aiOutput = aiTextClient.generateStructuredOutputWithVision(messages, schema);
        } catch (AiProviderException e) {
            log.error("AI 供应商调用失败: provider={}, errorCode={}, retryable={}", e.getProvider(), e.getErrorCode(), e
                .isRetryable(), e);
            throw mapAiProviderException(e);
        } catch (Exception e) {
            log.error("调用 AI 视觉识别服务时发生未预期异常", e);
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        AutomationAiVisionRecognizeResp recognizeResp = parseAiOutput(aiOutput);

        log.info("视觉识别成功: text={}, confidence={}", recognizeResp.getText(), recognizeResp.getConfidence());

        return recognizeResp;
    }

    private String loadSystemPrompt() {
        ClassPathResource resource = new ClassPathResource(SYSTEM_PROMPT_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("加载系统提示词失败: resource={}", SYSTEM_PROMPT_RESOURCE, e);
            throw new BusinessException("系统提示词加载失败");
        }
    }

    private String buildUserPrompt(AutomationAiVisionRecognizeReq req) {
        StringBuilder prompt = new StringBuilder();

        String mode = req.getMode() != null ? req.getMode() : "text";
        if ("captcha".equals(mode)) {
            prompt.append("请识别这个验证码图片中的文本。");
        } else {
            prompt.append("请识别这个图片中的文本。");
        }

        if (req.getPrompt() != null && !req.getPrompt().isEmpty()) {
            prompt.append("\n\n额外提示：").append(req.getPrompt());
        }

        return prompt.toString();
    }

    private List<Map<String, Object>> buildVisionMessages(String systemPrompt, String userPrompt, String imageData) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 系统消息
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        // 用户消息（包含文本和图片）
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> contentParts = new ArrayList<>();

        // 文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", userPrompt);
        contentParts.add(textPart);

        // 图片部分
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", imageData);
        imagePart.put("image_url", imageUrl);
        contentParts.add(imagePart);

        userMessage.put("content", contentParts);
        messages.add(userMessage);

        return messages;
    }

    private JsonNode buildOutputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();
        properties.putObject("text").put("type", "string");
        properties.putObject("confidence").put("type", "number");

        schema.set("properties", properties);
        schema.putArray("required").add("text").add("confidence");

        return schema;
    }

    private AutomationAiVisionRecognizeResp parseAiOutput(JsonNode aiOutput) {
        if (aiOutput == null || !aiOutput.has("text")) {
            log.error("AI 输出格式无效: 缺少必需字段 text");
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        String text = aiOutput.get("text").asText();
        Double confidence = aiOutput.has("confidence") ? aiOutput.get("confidence").asDouble() : null;

        return AutomationAiVisionRecognizeResp.builder().text(text).confidence(confidence).build();
    }

    private BusinessException mapAiProviderException(AiProviderException e) {
        String errorCode = e.getErrorCode();

        if (errorCode != null) {
            if (errorCode.contains("rate_limit") || errorCode.contains("429")) {
                return new BusinessException(AiVariableExtractErrorCode.AI_RATE_LIMITED.getMessage());
            }
            if (errorCode.contains("timeout") || errorCode.contains("504")) {
                return new BusinessException(AiVariableExtractErrorCode.AI_PROVIDER_TIMEOUT.getMessage());
            }
            if (errorCode.contains("unavailable") || errorCode.contains("503")) {
                return new BusinessException(AiVariableExtractErrorCode.AI_PROVIDER_UNAVAILABLE.getMessage());
            }
        }

        return new BusinessException(AiVariableExtractErrorCode.AI_PROVIDER_UNAVAILABLE.getMessage() + ": " + e
            .getMessage());
    }
}
