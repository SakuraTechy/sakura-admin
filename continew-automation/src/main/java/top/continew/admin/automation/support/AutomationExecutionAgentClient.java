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

package top.continew.admin.automation.support;

import java.net.URI;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

/**
 * admin 到本机执行 Agent 的唯一敏感传输边界。
 *
 * <p>仅允许回环地址，凭据只在本次 HTTPS/loopback 请求内存中存在，禁止写入任务表、日志和响应 DTO。</p>
 */
@Component
public class AutomationExecutionAgentClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${automation.execution-agent.base-url:http://127.0.0.1:19091}")
    private String baseUrl;
    @Value("${automation.execution-agent.token:}")
    private String token;

    public AutomationExecutionAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> submit(Map<String, Object> payload) {
        return exchange("POST", "/v1/tasks", payload);
    }

    public Map<String, Object> get(String taskId) {
        return exchange("GET", "/v1/tasks/" + taskId, null);
    }

    public Map<String, Object> cancel(String taskId) {
        return exchange("DELETE", "/v1/tasks/" + taskId, null);
    }

    public Map<String, Object> health() {
        return exchange("GET", "/health", null);
    }

    public ArtifactDownload downloadArtifact(String taskId) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new BusinessException("基础设施结果附件任务 ID 非法");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException("执行 Agent 令牌未配置");
        }
        URI uri = requireLoopbackUri("/v1/tasks/" + taskId + "/artifact");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("执行 Agent 附件请求失败：HTTP状态=" + response
                    .statusCode() + " 原因=" + responseError(new String(response
                        .body(), java.nio.charset.StandardCharsets.UTF_8)));
            }
            byte[] bytes = response.body();
            String expectedDigest = response.headers().firstValue("X-Content-Sha256").orElse("");
            String actualDigest = sha256(bytes);
            if (!expectedDigest.matches("[0-9a-fA-F]{64}") || !MessageDigest.isEqual(expectedDigest.toLowerCase()
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII), actualDigest
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new BusinessException("执行 Agent 附件摘要校验失败");
            }
            String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream")
                .split(";", 2)[0];
            return new ArtifactDownload(taskId + ".json", contentType, bytes, actualDigest);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("执行 Agent 附件不可用：" + diagnosticMessage(e));
        }
    }

    private Map<String, Object> exchange(String method, String path, Object body) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("执行 Agent 令牌未配置");
        }
        URI uri = requireLoopbackUri(path);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)));
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException firstFailure) {
                // Agent 重启或连接半关闭时 Java 可能报“header parser received no bytes”。
                // submit 使用 taskId 幂等，短暂重试不会重复执行；其它请求也只重试一次。
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("执行 Agent 请求失败：method=" + method + " path=" + path + " uri=" + uri + " HTTP状态=" + response
                    .statusCode() + " 原因=" + responseError(response.body()));
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("执行 Agent 不可用：method=" + method + " path=" + path + " uri=" + uri + " 异常类型=" + e
                .getClass()
                .getSimpleName() + " 原因=" + diagnosticMessage(e));
        }
    }

    private URI requireLoopbackUri(String path) {
        URI uri = URI.create(baseUrl + path);
        if (!"127.0.0.1".equalsIgnoreCase(uri.getHost()) && !"localhost".equalsIgnoreCase(uri.getHost())) {
            throw new BusinessException("执行 Agent 必须使用本机回环地址");
        }
        return uri;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    public record ArtifactDownload(String fileName, String contentType, byte[] bytes, String sha256) {
    }

    private String responseError(String body) {
        if (body == null || body.isBlank()) {
            return "Agent未返回错误内容";
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
            for (String key : new String[] {"message", "error", "msg"}) {
                Object value = payload.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return localizeAgentError(String.valueOf(value));
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 响应继续使用截断后的原文，便于定位反向代理或进程异常。
        }
        return sanitize(body);
    }

    /** 兼容旧版 Agent 仅返回英文错误码的响应，避免执行日志直接暴露内部协议标识。 */
    private String localizeAgentError(String value) {
        return switch (value) {
            case "UNAUTHORIZED" -> "执行 Agent 请求认证失败：Bearer 令牌缺失、格式错误或与 Agent 配置不匹配";
            case "TASK_NOT_FOUND" -> "执行 Agent 任务不存在";
            case "NOT_FOUND" -> "执行 Agent 接口不存在";
            case "INVALID_REQUEST" -> "执行 Agent 请求参数无效";
            case "AGENT_INTERNAL_ERROR" -> "执行 Agent 内部错误";
            default -> sanitize(value);
        };
    }

    private String diagnosticMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "未返回异常描述";
        }
        return sanitize(message);
    }

    private String sanitize(String value) {
        String normalized = value.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        normalized = normalized
            .replaceAll("(?i)(password|passwd|pwd|token|secret|authorization)\\s*([=:])\\s*[^\\s,;]+", "$1$2***")
            .replaceAll("(?i)(mongodb(?:\\+srv)?://)[^\\s/@]+@", "$1***@")
            .replaceAll("(?i)(jdbc:[^\\s]*://)[^\\s/@]+@", "$1***@");
        return normalized.length() <= 800 ? normalized : normalized.substring(0, 800) + "...[truncated]";
    }
}
