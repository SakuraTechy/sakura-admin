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

import java.util.HashMap;
import java.util.Map;

/**
 * Mock AI 文本客户端，用于测试。
 *
 * <p>根据用户输入内容返回预定义的变量提取规则响应，支持配置延迟和失败场景。</p>
 *
 * @author liuzhi
 * @since 2026-09-13
 */
public class MockAiTextClient implements AiTextClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 预定义的测试用例规则
    private static final Map<String, TestCaseRule> PREDEFINED_RULES = new HashMap<>();

    static {
        // 订单号提取
        PREDEFINED_RULES.put("订单号", new TestCaseRule("regex", "订单号：([A-Z0-9]+)", 1, "从文本中提取订单号，匹配冒号后的大写字母和数字组合"));

        // JSON userId 提取
        PREDEFINED_RULES
            .put("userId", new TestCaseRule("regex", "\"userId\":\\s*\"([^\"]+)\"", 1, "从 JSON 响应中提取 userId 字段值"));

        // 验证码提取
        PREDEFINED_RULES
            .put("验证码", new TestCaseRule("regex", "<span id=\"code\">(\\d+)</span>", 1, "从 HTML 中提取 id 为 code 的 span 标签内的数字验证码"));

        // 邮箱提取
        PREDEFINED_RULES.put("邮箱", new TestCaseRule("regex", "邮箱：([^\\s]+@[^\\s]+)", 1, "从文本中提取邮箱地址，匹配冒号后的邮箱格式"));

        // 初始密码提取
        PREDEFINED_RULES.put("初始密码", new TestCaseRule("regex", "初始密码\\s*[:：]\\s*(\\S+)", 1, "从文本中提取初始密码，支持中英文冒号"));
    }

    private long delayMillis = 0;
    private boolean shouldFail = false;
    private String failureMessage = null;
    private String failureErrorCode = null;
    private boolean failureRetryable = false;
    private JsonNode nextResponse = null;

    @Override
    public JsonNode generateStructuredOutput(String systemPrompt,
                                             String userContent,
                                             JsonNode schema) throws AiProviderException {
        // 如果设置了特定响应，返回并清除
        if (nextResponse != null) {
            JsonNode response = nextResponse;
            nextResponse = null;
            return response;
        }

        // 模拟延迟
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("mock", "模拟延迟被中断", e);
            }
        }

        // 模拟失败
        if (shouldFail) {
            throw new AiProviderException("mock", failureMessage != null
                ? failureMessage
                : "模拟 AI 调用失败", failureErrorCode, failureRetryable);
        }

        // 根据用户输入内容匹配预定义规则
        TestCaseRule matchedRule = findMatchingRule(userContent);
        if (matchedRule == null) {
            // 未匹配到预定义规则，返回默认通用规则
            matchedRule = new TestCaseRule("regex", "(\\S+)", 1, "默认规则：提取第一个非空字符序列");
        }

        // 构造响应 JSON
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("mode", matchedRule.mode);
        response.put("pattern", matchedRule.pattern);
        response.put("group", matchedRule.group);
        response.put("reason", matchedRule.reason);

        return response;
    }

    /**
     * 根据用户输入内容查找匹配的预定义规则。
     * 通过关键词匹配来决定返回哪个规则。
     */
    private TestCaseRule findMatchingRule(String userContent) {
        if (userContent == null || userContent.isEmpty()) {
            return null;
        }

        // 按关键词优先级匹配
        for (Map.Entry<String, TestCaseRule> entry : PREDEFINED_RULES.entrySet()) {
            if (userContent.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 配置模拟延迟时间（毫秒）
     */
    public MockAiTextClient withDelay(long millis) {
        this.delayMillis = millis;
        return this;
    }

    /**
     * 设置下一次调用返回的特定响应
     *
     * @param responseJson JSON 响应字符串
     */
    public void setNextResponse(String responseJson) {
        try {
            this.nextResponse = OBJECT_MAPPER.readTree(responseJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON response: " + responseJson, e);
        }
    }

    /**
     * 配置模拟失败场景
     */
    public MockAiTextClient withFailure(String message, String errorCode, boolean retryable) {
        this.shouldFail = true;
        this.failureMessage = message;
        this.failureErrorCode = errorCode;
        this.failureRetryable = retryable;
        return this;
    }

    /**
     * 重置为成功状态
     */
    public MockAiTextClient reset() {
        this.delayMillis = 0;
        this.shouldFail = false;
        this.failureMessage = null;
        this.failureErrorCode = null;
        this.failureRetryable = false;
        this.nextResponse = null;
        return this;
    }

    /**
     * 测试用例规则封装
     */
    private static class TestCaseRule {
        final String mode;
        final String pattern;
        final int group;
        final String reason;

        TestCaseRule(String mode, String pattern, int group, String reason) {
            this.mode = mode;
            this.pattern = pattern;
            this.group = group;
            this.reason = reason;
        }
    }
}
