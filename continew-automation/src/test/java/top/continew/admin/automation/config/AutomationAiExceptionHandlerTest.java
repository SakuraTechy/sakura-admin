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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import top.continew.admin.automation.support.ai.AiProviderException;
import top.continew.starter.web.model.R;

import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AutomationAiExceptionHandler 单元测试
 *
 * <p>验证异常映射到错误响应的正确性，以及响应中不包含敏感数据（原值、pattern、API key、栈信息）。</p>
 *
 * @author Codex
 * @since 2026/09/13
 */
class AutomationAiExceptionHandlerTest {

    private AutomationAiExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new AutomationAiExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/automation/ai/test");
    }

    /**
     * 测试 JsonParseException 映射为 400
     */
    @Test
    void shouldMapJsonParseExceptionToBadRequest() {
        JsonParseException exception = new JsonParseException(null, "Unexpected character");

        R response = handler.handleJsonParseException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("JSON 格式不正确");
        // 确保不包含敏感数据
        assertThat(response.getMsg()).doesNotContain("Unexpected character");
    }

    /**
     * 测试 JsonMappingException 提取字段路径，不暴露值
     */
    @Test
    void shouldMapJsonMappingExceptionWithFieldPath() {
        JsonMappingException exception = mock(JsonMappingException.class);
        JsonMappingException.Reference ref1 = new JsonMappingException.Reference(null, "extractRule");
        JsonMappingException.Reference ref2 = new JsonMappingException.Reference(null, "pattern");
        when(exception.getPath()).thenReturn(List.of(ref1, ref2));

        R response = handler.handleJsonMappingException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("字段「extractRule.pattern」格式不正确");
        // 确保不包含具体值
        assertThat(response.getMsg()).doesNotContainIgnoringCase("value");
    }

    /**
     * 测试 JsonMappingException 空路径时的兜底消息
     */
    @Test
    void shouldMapJsonMappingExceptionWithEmptyPath() {
        JsonMappingException exception = mock(JsonMappingException.class);
        when(exception.getPath()).thenReturn(List.of());

        R response = handler.handleJsonMappingException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("请求数据格式不正确");
    }

    /**
     * 测试 MethodArgumentNotValidException 收集多个字段错误
     */
    @Test
    void shouldMapMethodArgumentNotValidExceptionWithMultipleErrors() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError error1 = new FieldError("request", "ruleName", "不能为空");
        FieldError error2 = new FieldError("request", "pattern", "格式不正确");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));
        when(exception.getBindingResult()).thenReturn(bindingResult);

        R response = handler.handleMethodArgumentNotValidException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).contains("字段「ruleName」不能为空");
        assertThat(response.getMsg()).contains("字段「pattern」格式不正确");
        assertThat(response.getMsg()).contains("；");
    }

    /**
     * 测试 MethodArgumentNotValidException 空错误时的兜底消息
     */
    @Test
    void shouldMapMethodArgumentNotValidExceptionWithNoErrors() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        when(exception.getBindingResult()).thenReturn(bindingResult);

        R response = handler.handleMethodArgumentNotValidException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("请求参数校验失败");
    }

    /**
     * 测试 BindException 提取字段错误
     */
    @Test
    void shouldMapBindExceptionWithFieldError() {
        BindException exception = new BindException(new Object(), "target");
        exception.addError(new FieldError("target", "ruleType", "不能为空"));

        R response = handler.handleBindException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("字段「ruleType」不能为空");
    }

    /**
     * 测试 BindException 无字段错误时的兜底消息
     */
    @Test
    void shouldMapBindExceptionWithoutFieldError() {
        BindException exception = new BindException(new Object(), "target");

        R response = handler.handleBindException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("参数绑定失败");
    }

    /**
     * 测试 ConstraintViolationException 收集约束违规
     */
    @Test
    void shouldMapConstraintViolationException() {
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        when(violation1.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation1.getPropertyPath().toString()).thenReturn("extractRules[0].pattern");
        when(violation1.getMessage()).thenReturn("不能为空");

        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        when(violation2.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation2.getPropertyPath().toString()).thenReturn("variableName");
        when(violation2.getMessage()).thenReturn("长度必须在 1 到 50 之间");

        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation1, violation2));

        R response = handler.handleConstraintViolationException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg())
            .containsAnyOf("参数「extractRules[0].pattern」不能为空", "参数「variableName」长度必须在 1 到 50 之间");
    }

    /**
     * 测试 PatternSyntaxException 不暴露 pattern 内容
     */
    @Test
    void shouldMapPatternSyntaxExceptionWithoutPattern() {
        // 构造包含敏感 pattern 的异常
        PatternSyntaxException exception = new PatternSyntaxException("Unclosed group", "(?<token>[A-Z]+", 10);

        R response = handler.handlePatternSyntaxException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("正则表达式语法错误");
        // 确保不包含 pattern 片段
        assertThat(response.getMsg()).doesNotContain("(?<token>");
        assertThat(response.getMsg()).doesNotContain("[A-Z]");
    }

    /**
     * 测试 AiProviderException 可重试场景
     */
    @Test
    void shouldMapAiProviderExceptionRetryable() {
        AiProviderException exception = new AiProviderException("openai", "Rate limit exceeded", "rate_limit", true);

        R response = handler.handleAiProviderException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        assertThat(response.getMsg()).isEqualTo("AI 服务暂时不可用,请稍后重试");
        // 确保不包含 provider 信息和 error code
        assertThat(response.getMsg()).doesNotContain("openai");
        assertThat(response.getMsg()).doesNotContain("rate_limit");
    }

    /**
     * 测试 AiProviderException 不可重试场景
     */
    @Test
    void shouldMapAiProviderExceptionNonRetryable() {
        AiProviderException exception = new AiProviderException("anthropic", "Invalid API key", "auth_error", false);

        R response = handler.handleAiProviderException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        assertThat(response.getMsg()).isEqualTo("AI 服务调用失败");
        // 确保不包含 API key 相关信息
        assertThat(response.getMsg()).doesNotContainIgnoringCase("api");
        assertThat(response.getMsg()).doesNotContainIgnoringCase("key");
        assertThat(response.getMsg()).doesNotContain("anthropic");
    }

    /**
     * 测试 HttpMessageNotReadableException 包含 InvalidFormatException 时提取字段路径
     */
    @Test
    void shouldMapHttpMessageNotReadableExceptionWithInvalidFormat() {
        InvalidFormatException cause = mock(InvalidFormatException.class);
        JsonMappingException.Reference ref = new JsonMappingException.Reference(null, "ruleType");
        when(cause.getPath()).thenReturn(List.of(ref));

        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error", cause);

        R response = handler.handleHttpMessageNotReadableException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("字段「ruleType」格式不正确");
    }

    /**
     * 测试 HttpMessageNotReadableException 无 cause 时的兜底消息
     */
    @Test
    void shouldMapHttpMessageNotReadableExceptionWithoutCause() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error");

        R response = handler.handleHttpMessageNotReadableException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(response.getMsg()).isEqualTo("请求数据格式不正确");
    }

    /**
     * 测试通用 Exception 兜底，不暴露栈信息
     */
    @Test
    void shouldMapGenericExceptionWithoutStackTrace() {
        Exception exception = new RuntimeException("Internal error: database connection failed at line 42");

        R response = handler.handleException(exception, request);

        assertThat(response.getCode()).isEqualTo(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        assertThat(response.getMsg()).isEqualTo("系统处理异常，请稍后重试");
        // 确保不包含内部错误细节
        assertThat(response.getMsg()).doesNotContain("database");
        assertThat(response.getMsg()).doesNotContain("line 42");
    }

    /**
     * 测试所有错误响应不包含敏感关键词
     */
    @Test
    void shouldNeverContainSensitiveKeywords() {
        // 构造包含敏感信息的各类异常
        PatternSyntaxException patternEx = new PatternSyntaxException("error", "secret_pattern_(?<key>\\w+)", 5);
        AiProviderException aiEx = new AiProviderException("provider", "API_KEY=sk-1234567890", "auth_failed", false);

        R r1 = handler.handlePatternSyntaxException(patternEx, request);
        R r2 = handler.handleAiProviderException(aiEx, request);

        // 验证敏感关键词不出现
        for (R response : List.of(r1, r2)) {
            String msg = response.getMsg().toLowerCase();
            assertThat(msg).doesNotContain("secret");
            assertThat(msg).doesNotContain("key");
            assertThat(msg).doesNotContain("api_key");
            assertThat(msg).doesNotContain("sk-");
            assertThat(msg).doesNotContain("pattern");
        }
    }
}
