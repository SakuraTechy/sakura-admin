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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.model.req.ai.AutomationVariableExtractRuleReq;
import top.continew.admin.automation.model.resp.ai.AutomationVariableExtractRuleResp;
import top.continew.admin.automation.support.ai.MockAiTextClient;
import top.continew.admin.automation.support.ai.VariableExtractRuleValidator;
import top.continew.starter.core.exception.BusinessException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AutomationVariableExtractRuleServiceImpl 集成测试
 *
 * <p>使用 MockAiTextClient 模拟 AI 调用，验证业务逻辑、参数校验、规则校验和错误处理。</p>
 *
 * @author Claude Opus 5
 * @since 2026-09-13
 */
class AutomationVariableExtractRuleServiceImplTest {

    private AutomationVariableExtractRuleServiceImpl service;
    private MockAiTextClient mockAiClient;
    private VariableExtractRuleValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockAiClient = new MockAiTextClient();
        validator = new VariableExtractRuleValidator();
        objectMapper = new ObjectMapper();

        service = new AutomationVariableExtractRuleServiceImpl(mockAiClient, validator, objectMapper);
        // 通过反射设置 aiEnabled 为 true
        ReflectionTestUtils.setField(service, "aiEnabled", true);
    }

    /**
     * 测试 AI 功能未启用时抛出异常
     */
    @Test
    void testGenerateRule_AiDisabled_ThrowsException() {
        // Given: AI 功能关闭
        ReflectionTestUtils.setField(service, "aiEnabled", false);

        AutomationVariableExtractRuleReq req = new AutomationVariableExtractRuleReq();
        req.setRawValue("订单号：ORD001");
        req.setInstruction("提取订单号");

        // When & Then: 应该抛出 AI_DISABLED 异常
        assertThatThrownBy(() -> service.generateRule(req)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("AI 能力未启用");
    }

    /**
     * 测试成功生成订单号提取规则
     */
    @Test
    void testGenerateRule_OrderNumber_Success() {
        // Given: 订单号提取请求
        AutomationVariableExtractRuleReq req = new AutomationVariableExtractRuleReq();
        req.setRawValue("订单创建成功，订单号：ORD20240315001，请及时支付");
        req.setInstruction("提取订单号");

        // When: 调用生成规则
        AutomationVariableExtractRuleResp resp = service.generateRule(req);

        // Then: 应该返回有效的正则规则
        assertThat(resp).isNotNull();
        assertThat(resp.getMode()).isEqualTo("regex");
        assertThat(resp.getPattern()).isNotBlank();
        assertThat(resp.getGroup()).isEqualTo(1);
        assertThat(resp.getReason()).isNotBlank();

        // 验证规则可以提取订单号
        Pattern pattern = Pattern.compile(resp.getPattern());
        Matcher matcher = pattern.matcher(req.getRawValue());
        assertThat(matcher.find()).isTrue();
        String extracted = matcher.group(resp.getGroup());
        assertThat(extracted).isEqualTo("ORD20240315001");
    }

    /**
     * 测试成功生成 JSON 字段提取规则
     */
    @Test
    void testGenerateRule_JsonField_Success() {
        // Given: JSON userId 提取请求
        AutomationVariableExtractRuleReq req = new AutomationVariableExtractRuleReq();
        req.setRawValue("{\"code\":200,\"data\":{\"userId\":\"U123456\",\"username\":\"张三\"},\"message\":\"success\"}");
        req.setInstruction("提取 userId 的值");

        // When: 调用生成规则
        AutomationVariableExtractRuleResp resp = service.generateRule(req);

        // Then: 应该返回有效的正则规则
        assertThat(resp).isNotNull();
        assertThat(resp.getMode()).isEqualTo("regex");
        assertThat(resp.getPattern()).isNotBlank();

        // 验证规则可以提取 userId
        Pattern pattern = Pattern.compile(resp.getPattern());
        Matcher matcher = pattern.matcher(req.getRawValue());
        assertThat(matcher.find()).isTrue();
        String extracted = matcher.group(resp.getGroup());
        assertThat(extracted).isEqualTo("U123456");
    }

    /**
     * 测试成功生成 HTML 验证码提取规则
     */
    @Test
    void testGenerateRule_HtmlVerificationCode_Success() {
        // Given: HTML 验证码提取请求
        AutomationVariableExtractRuleReq req = new AutomationVariableExtractRuleReq();
        req.setRawValue("<div class=\"verify-code\">您的验证码是：<span id=\"code\">8847</span>，5分钟内有效</div>");
        req.setInstruction("提取验证码");

        // When: 调用生成规则
        AutomationVariableExtractRuleResp resp = service.generateRule(req);

        // Then: 应该返回有效的正则规则
        assertThat(resp).isNotNull();
        assertThat(resp.getMode()).isEqualTo("regex");

        // 验证规则可以提取验证码
        Pattern pattern = Pattern.compile(resp.getPattern());
        Matcher matcher = pattern.matcher(req.getRawValue());
        assertThat(matcher.find()).isTrue();
        String extracted = matcher.group(resp.getGroup());
        assertThat(extracted).isEqualTo("8847");
    }

    /**
     * 测试 MockAiTextClient 返回不安全正则时被拒绝
     */
    @Test
    void testGenerateRule_UnsafeRegex_ThrowsException() {
        // Given: MockAiTextClient 配置为返回包含回溯引用的不安全正则
        mockAiClient.setNextResponse("{\"mode\":\"regex\",\"pattern\":\"(\\\\d+)-\\\\1\",\"group\":1}");

        AutomationVariableExtractRuleReq req = new AutomationVariableExtractRuleReq();
        req.setRawValue("订单号：ORD001-ORD001");
        req.setInstruction("提取重复的订单号");

        // When & Then: 应该抛出规则无效异常
        assertThatThrownBy(() -> service.generateRule(req)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("不安全");
    }
}
