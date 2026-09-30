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
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.continew.admin.automation.support.ai.AiProviderException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.web.model.R;

import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * AI 专用异常处理器
 *
 * <p>只作用于 AI 变量提取规则相关接口，处理 JSON 解析、DTO 校验、正则编译、AI 供应商调用等异常。
 * 响应中不包含原值、pattern 片段、rejected value 等敏感信息。</p>
 *
 * @author Codex
 * @since 2026/09/12
 */
@Order(1)
@RestControllerAdvice(basePackages = "top.continew.admin.automation.controller")
public class AutomationAiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AutomationAiExceptionHandler.class);

    /**
     * JSON 解析异常
     */
    @ExceptionHandler(JsonParseException.class)
    public R<Void> handleJsonParseException(JsonParseException e, HttpServletRequest request) {
        log.error("[{}] {} - JSON 解析失败", request.getMethod(), request.getRequestURI(), e);
        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "JSON 格式不正确");
    }

    /**
     * JSON 映射异常（字段类型不匹配等）
     */
    @ExceptionHandler(JsonMappingException.class)
    public R<Void> handleJsonMappingException(JsonMappingException e, HttpServletRequest request) {
        log.error("[{}] {} - JSON 映射失败", request.getMethod(), request.getRequestURI(), e);

        // 提取字段路径，不包含具体值
        String fieldPath = e.getPath()
            .stream()
            .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
            .collect(Collectors.joining("."));

        if (fieldPath.isEmpty()) {
            return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "请求数据格式不正确");
        }

        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), String.format("字段「%s」格式不正确", fieldPath));
    }

    /**
     * @RequestBody 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                         HttpServletRequest request) {
        log.error("[{}] {} - 参数校验失败", request.getMethod(), request.getRequestURI(), e);

        // 收集所有校验错误消息，不包含具体值
        String errorMessage = e.getBindingResult().getFieldErrors().stream().map(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage();
            return String.format("字段「%s」%s", field, message);
        }).collect(Collectors.joining("；"));

        if (errorMessage.isEmpty()) {
            errorMessage = "请求参数校验失败";
        }

        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), errorMessage);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e, HttpServletRequest request) {
        log.error("[{}] {} - 参数绑定失败", request.getMethod(), request.getRequestURI(), e);

        FieldError fieldError = e.getFieldError();
        if (fieldError != null) {
            return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), String.format("字段「%s」%s", fieldError
                .getField(), fieldError.getDefaultMessage()));
        }

        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "参数绑定失败");
    }

    /**
     * 约束违规异常（@Validated 校验失败）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        log.error("[{}] {} - 约束校验失败", request.getMethod(), request.getRequestURI(), e);

        String errorMessage = e.getConstraintViolations().stream().map(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            return String.format("参数「%s」%s", propertyPath, message);
        }).collect(Collectors.joining("；"));

        if (errorMessage.isEmpty()) {
            errorMessage = "参数约束校验失败";
        }

        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), errorMessage);
    }

    /**
     * 正则编译异常
     */
    @ExceptionHandler(PatternSyntaxException.class)
    public R<Void> handlePatternSyntaxException(PatternSyntaxException e, HttpServletRequest request) {
        log.error("[{}] {} - 正则编译失败", request.getMethod(), request.getRequestURI(), e);

        // 不暴露具体 pattern 内容，只提示语法错误
        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "正则表达式语法错误");
    }

    /**
     * 业务异常
     *
     * <p>该处理器优先级高于全局异常处理器，必须显式保留业务层返回的可操作原因。</p>
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("[{}] {} - 业务异常", request.getMethod(), request.getRequestURI(), e);
        return R.fail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), e.getMessage());
    }

    /**
     * AI 供应商调用异常
     */
    @ExceptionHandler(AiProviderException.class)
    public R<Void> handleAiProviderException(AiProviderException e, HttpServletRequest request) {
        log.error("[{}] {} - AI 供应商调用失败: provider={}, errorCode={}, retryable={}", request.getMethod(), request
            .getRequestURI(), e.getProvider(), e.getErrorCode(), e.isRetryable(), e);

        // 根据是否可重试返回不同提示
        String message = e.isRetryable() ? "AI 服务暂时不可用，请稍后重试" : "AI 服务调用失败";

        return R.fail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), message);
    }

    /**
     * HTTP 消息不可读异常（JSON 解析失败的顶层异常）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
                                                         HttpServletRequest request) {
        log.error("[{}] {} - HTTP 消息不可读", request.getMethod(), request.getRequestURI(), e);

        // 如果是字段类型不匹配，提取字段路径
        if (e.getCause() instanceof InvalidFormatException invalidFormatException) {
            String fieldPath = invalidFormatException.getPath()
                .stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));

            if (!fieldPath.isEmpty()) {
                return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), String.format("字段「%s」格式不正确", fieldPath));
            }
        }

        return R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "请求数据格式不正确");
    }

    /**
     * 未知异常兜底
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[{}] {} - 未知异常", request.getMethod(), request.getRequestURI(), e);
        return R.fail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "系统处理异常，请稍后重试");
    }
}
