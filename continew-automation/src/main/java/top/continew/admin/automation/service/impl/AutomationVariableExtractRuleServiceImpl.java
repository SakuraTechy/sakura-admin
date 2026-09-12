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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import top.continew.admin.automation.enums.AiVariableExtractErrorCode;
import top.continew.admin.automation.model.req.ai.AutomationVariableExtractRuleReq;
import top.continew.admin.automation.model.resp.ai.AutomationVariableExtractRuleResp;
import top.continew.admin.automation.service.AutomationVariableExtractRuleService;
import top.continew.admin.automation.support.ai.AiProviderException;
import top.continew.admin.automation.support.ai.AiTextClient;
import top.continew.admin.automation.support.ai.VariableExtractRuleValidator;
import top.continew.starter.core.exception.BusinessException;

/**
 * 变量提取规则生成服务实现
 *
 * @author liuzhi
 * @since 2026-09-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationVariableExtractRuleServiceImpl implements AutomationVariableExtractRuleService {

    private static final String SYSTEM_PROMPT_RESOURCE = "ai/variable-extract-rule-system-prompt.txt";
    private static final String FIXED_REASON = "规则已通过校验，请确认本地变量值预览。";

    private final AiTextClient aiTextClient;
    private final VariableExtractRuleValidator validator;
    private final ObjectMapper objectMapper;

    @Value("${automation.ai.enabled:false}")
    private boolean aiEnabled;

    @Override
    public AutomationVariableExtractRuleResp generateRule(AutomationVariableExtractRuleReq req) {
        // 检查 AI 能力是否启用
        if (!aiEnabled) {
            log.warn("AI 能力未启用，无法生成变量提取规则");
            throw new BusinessException(AiVariableExtractErrorCode.AI_DISABLED.getMessage());
        }

        // 检查 AI 客户端是否配置
        if (aiTextClient == null) {
            log.error("AI 服务未配置，无法生成变量提取规则");
            throw new BusinessException(AiVariableExtractErrorCode.AI_NOT_CONFIGURED.getMessage());
        }

        // 加载系统提示词
        String systemPrompt = loadSystemPrompt();

        // 构建用户提示词（不记录敏感的 rawValue）
        String userContent = buildUserContent(req);
        log.info("开始生成变量提取规则，instruction={}", req.getInstruction());

        // 构建输出 Schema
        JsonNode schema = buildOutputSchema();

        // 调用 AI 模型生成规则
        JsonNode aiOutput;
        try {
            aiOutput = aiTextClient.generateStructuredOutput(systemPrompt, userContent, schema);
        } catch (AiProviderException e) {
            log.error("AI 供应商调用失败: provider={}, errorCode={}, retryable={}", e.getProvider(), e.getErrorCode(), e
                .isRetryable(), e);
            throw mapAiProviderException(e);
        } catch (Exception e) {
            log.error("调用 AI 服务时发生未预期异常", e);
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        // 解析 AI 输出
        AutomationVariableExtractRuleResp ruleResp = parseAiOutput(aiOutput);

        // 校验正则表达式
        validateRule(ruleResp, req.getRawValue());

        // 设置固定的 reason
        ruleResp.setReason(FIXED_REASON);

        log.info("变量提取规则生成成功: mode={}, pattern=[REDACTED], group={}", ruleResp.getMode(), ruleResp.getGroup());

        return ruleResp;
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
     * 构建用户提示词内容
     */
    private String buildUserContent(AutomationVariableExtractRuleReq req) {
        return String.format("**原文数据**：\n%s\n\n**抽取指令**：\n%s", req.getRawValue(), req.getInstruction());
    }

    /**
     * 构建输出 JSON Schema
     */
    private JsonNode buildOutputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // mode 字段
        ObjectNode modeNode = objectMapper.createObjectNode();
        modeNode.put("type", "string");
        modeNode.putArray("enum").add("regex");
        properties.set("mode", modeNode);

        // pattern 字段
        ObjectNode patternNode = objectMapper.createObjectNode();
        patternNode.put("type", "string");
        patternNode.put("minLength", 1);
        patternNode.put("maxLength", 300);
        properties.set("pattern", patternNode);

        // group 字段
        ObjectNode groupNode = objectMapper.createObjectNode();
        groupNode.put("type", "integer");
        groupNode.put("minimum", 0);
        properties.set("group", groupNode);

        schema.set("properties", properties);
        schema.putArray("required").add("mode").add("pattern").add("group");

        return schema;
    }

    /**
     * 解析 AI 输出为响应对象
     */
    private AutomationVariableExtractRuleResp parseAiOutput(JsonNode aiOutput) {
        if (aiOutput == null || !aiOutput.has("mode") || !aiOutput.has("pattern") || !aiOutput.has("group")) {
            log.error("AI 输出格式无效: 缺少必需字段");
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        String mode = aiOutput.get("mode").asText();
        String pattern = aiOutput.get("pattern").asText();
        int group = aiOutput.get("group").asInt();

        // 验证 mode
        if (!"regex".equals(mode)) {
            log.error("AI 输出的 mode 无效: mode={}", mode);
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        // 验证 pattern 长度
        if (pattern.isEmpty() || pattern.length() > 300) {
            log.error("AI 输出的 pattern 长度无效: length={}", pattern.length());
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        // 验证 group
        if (group < 0) {
            log.error("AI 输出的 group 无效: group={}", group);
            throw new BusinessException(AiVariableExtractErrorCode.AI_OUTPUT_INVALID.getMessage());
        }

        return AutomationVariableExtractRuleResp.builder().mode(mode).pattern(pattern).group(group).build();
    }

    /**
     * 校验生成的规则
     */
    private void validateRule(AutomationVariableExtractRuleResp ruleResp, String rawValue) {
        VariableExtractRuleValidator.ValidationResult result = validator.validatePattern(ruleResp.getPattern(), ruleResp
            .getGroup(), rawValue);

        if (!result.isValid()) {
            log.warn("生成的正则表达式校验失败: pattern=[REDACTED], group={}, error={}", ruleResp.getGroup(), result
                .getErrorMessage());
            throw new BusinessException(AiVariableExtractErrorCode.AI_RULE_INVALID.getMessage() + ": " + result
                .getErrorMessage());
        }

        // 检查是否有提取值
        if (result.getExtractedValue() == null) {
            log.warn("正则表达式匹配成功但未提取到值: pattern=[REDACTED], group={}", ruleResp.getGroup());
            throw new BusinessException(AiVariableExtractErrorCode.AI_RULE_NO_MATCH.getMessage());
        }

        log.debug("正则表达式校验成功: extractedValue=[REDACTED]");
    }

    /**
     * 映射 AI 供应商异常到业务异常
     */
    private BusinessException mapAiProviderException(AiProviderException e) {
        String errorCode = e.getErrorCode();

        // 根据错误码映射到具体的业务错误
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

        // 默认返回服务不可用
        return new BusinessException(AiVariableExtractErrorCode.AI_PROVIDER_UNAVAILABLE.getMessage() + ": " + e
            .getMessage());
    }
}
