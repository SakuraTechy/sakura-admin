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
import top.continew.admin.automation.model.req.ai.AutomationAiStepPlanReq;
import top.continew.admin.automation.model.resp.ai.AutomationAiStepPlanResp;
import top.continew.admin.automation.service.AutomationAiStepPlanService;
import top.continew.admin.automation.support.ai.AiProviderException;
import top.continew.admin.automation.support.ai.AiTextClient;
import top.continew.starter.core.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 步骤规划服务实现
 *
 * @author Codex
 * @since 2026/09/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationAiStepPlanServiceImpl implements AutomationAiStepPlanService {

    private static final String SYSTEM_PROMPT_RESOURCE = "ai/step-plan-system-prompt.txt";

    private final AiTextClient aiTextClient;
    private final ObjectMapper objectMapper;

    @Value("${automation.ai.enabled:false}")
    private boolean aiEnabled;

    @Override
    public AutomationAiStepPlanResp generateStepPlan(AutomationAiStepPlanReq req) {
        // 检查 AI 能力是否启用
        if (!aiEnabled) {
            log.warn("AI 能力未启用，无法生成步骤规划");
            throw new BusinessException(AiVariableExtractErrorCode.AI_DISABLED.getMessage());
        }

        // 检查 AI 客户端是否配置
        if (aiTextClient == null) {
            log.error("AI 服务未配置，无法生成步骤规划");
            throw new BusinessException(AiVariableExtractErrorCode.AI_NOT_CONFIGURED.getMessage());
        }

        // 加载系统提示词
        String systemPrompt = loadSystemPrompt();

        // 构建用户内容
        String userContent = buildUserContent(req);
        log.info("开始生成步骤规划，instruction={}, pageStructureLength={}", req.getInstruction(), req.getPageStructure()
            .length());

        // 构建输出 Schema
        JsonNode schema = buildOutputSchema();

        // 调用 AI 模型生成步骤规划
        JsonNode aiOutput;
        try {
            aiOutput = aiTextClient.generateStructuredOutput(systemPrompt, userContent, schema);
        } catch (AiProviderException e) {
            log.error("AI 供应商调用失败: provider={}, errorCode={}, retryable={}", e.getProvider(), e.getErrorCode(), e
                .isRetryable(), e);
            throw mapAiProviderException(e);
        } catch (Exception e) {
            log.error("调用 AI 步骤规划服务时发生未预期异常", e);
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        // 解析 AI 输出
        AutomationAiStepPlanResp planResp = parseAiOutput(aiOutput);

        log.info("步骤规划生成成功: operationCount={}", planResp.getOperations() != null ? planResp.getOperations().size() : 0);

        return planResp;
    }

    /**
     * 从 classpath 加载系统提示词
     */
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

    /**
     * 构建用户内容
     */
    private String buildUserContent(AutomationAiStepPlanReq req) {
        StringBuilder content = new StringBuilder();
        content.append("## 用户指令\n\n");
        content.append(req.getInstruction());
        content.append("\n\n## 页面结构\n\n");
        content.append("```json\n");
        content.append(req.getPageStructure());
        content.append("\n```");
        return content.toString();
    }

    /**
     * 构建输出 JSON Schema
     */
    private JsonNode buildOutputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        // operations 数组
        ObjectNode operationsNode = objectMapper.createObjectNode();
        operationsNode.put("type", "array");
        ObjectNode operationItem = operationsNode.putObject("items");
        operationItem.put("type", "object");
        operationItem.put("additionalProperties", false);

        ObjectNode operationProps = operationItem.putObject("properties");
        operationProps.putObject("type").put("type", "string");
        operationProps.putObject("target").put("type", "string");
        operationProps.putObject("value").put("type", "string");
        operationProps.putObject("description").put("type", "string");

        operationItem.putArray("required").add("type").add("target").add("description");

        properties.set("operations", operationsNode);

        // reason 字段
        ObjectNode reasonNode = objectMapper.createObjectNode();
        reasonNode.put("type", "string");
        properties.set("reason", reasonNode);

        schema.set("properties", properties);
        schema.putArray("required").add("operations").add("reason");

        return schema;
    }

    /**
     * 解析 AI 输出为响应对象
     */
    private AutomationAiStepPlanResp parseAiOutput(JsonNode aiOutput) {
        if (aiOutput == null || !aiOutput.has("operations")) {
            log.error("AI 输出格式无效: 缺少必需字段 operations");
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        String reason = aiOutput.has("reason") ? aiOutput.get("reason").asText() : null;
        List<AutomationAiStepPlanResp.Operation> operations = new ArrayList<>();

        JsonNode operationsNode = aiOutput.get("operations");
        if (operationsNode.isArray()) {
            for (JsonNode opNode : operationsNode) {
                AutomationAiStepPlanResp.Operation operation = AutomationAiStepPlanResp.Operation.builder()
                    .type(opNode.has("type") ? opNode.get("type").asText() : null)
                    .target(opNode.has("target") ? opNode.get("target").asText() : null)
                    .value(opNode.has("value") ? opNode.get("value").asText() : null)
                    .description(opNode.has("description") ? opNode.get("description").asText() : null)
                    .build();
                operations.add(operation);
            }
        }

        return AutomationAiStepPlanResp.builder().operations(operations).reason(reason).build();
    }

    /**
     * 映射 AI 供应商异常到业务异常
     */
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