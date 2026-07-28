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

package top.continew.admin.config.log;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import top.continew.admin.auth.enums.AuthTypeEnum;
import top.continew.admin.auth.model.req.AccountLoginReq;
import top.continew.admin.auth.model.req.EmailLoginReq;
import top.continew.admin.auth.model.req.LoginReq;
import top.continew.admin.auth.model.req.PhoneLoginReq;
import top.continew.admin.common.constant.SysConstants;
import top.continew.admin.system.enums.LogStatusEnum;
import top.continew.admin.system.mapper.LogMapper;
import top.continew.admin.system.model.entity.LogDO;
import top.continew.admin.system.service.UserService;
import top.continew.starter.core.constant.StringConstants;
import top.continew.starter.core.util.ExceptionUtils;
import top.continew.starter.core.util.StrUtils;
import top.continew.starter.log.dao.LogDao;
import top.continew.starter.log.model.LogRecord;
import top.continew.starter.log.model.LogRequest;
import top.continew.starter.log.model.LogResponse;
import top.continew.starter.trace.autoconfigure.TraceProperties;
import top.continew.starter.web.model.R;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志持久层接口本地实现类
 *
 * @author Charles7c
 * @since 2023/12/16 23:55
 */
@RequiredArgsConstructor
public class LogDaoLocalImpl implements LogDao {

    private static final Logger LOG = LoggerFactory.getLogger(LogDaoLocalImpl.class);
    private static final long LOG_PERSIST_FAILURE_WARNING_INTERVAL_MS = 60_000L;
    private static final AtomicLong LAST_LOG_PERSIST_FAILURE_WARNING = new AtomicLong();

    private static final int MAX_PERSISTED_BODY_LENGTH = 8 * 1024;
    private static final String TRUNCATED_SUFFIX = "...[已截断]";
    private static final Set<String> SENSITIVE_HEADER_NAMES = Set
        .of("authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token");
    private static final Set<String> IGNORED_AUTOMATION_PATH_PARTS = Set
        .of("/automation/playwright/artifacts", "/live-frame");
    private static final Set<String> COMPACT_AUTOMATION_PATH_PARTS = Set
        .of("/automation/automationUiScene", "/automation/playwright/testcases", "/automation/playwright/jobs", "/automation/playwright/runner/jobs");

    private final UserService userService;
    private final LogMapper logMapper;
    private final TraceProperties traceProperties;

    @Async
    @Override
    public void add(LogRecord logRecord) {
        if (logRecord == null || logRecord.getRequest() == null || logRecord.getRequest().getUrl() == null) {
            return;
        }
        LogRequest logRequest = logRecord.getRequest();
        String requestPath = URLUtil.getPath(logRequest.getUrl().toString());
        // JPEG 实时帧和二进制产物属于传输流，不是审计事件；高频落库只会制造无价值数据。
        if (IGNORED_AUTOMATION_PATH_PARTS.stream().anyMatch(requestPath::contains)) {
            return;
        }
        boolean compactAutomationLog = COMPACT_AUTOMATION_PATH_PARTS.stream().anyMatch(requestPath::contains);
        try {
            LogDO logDO = new LogDO();
            // 设置请求信息
            this.setRequest(logDO, logRequest, compactAutomationLog);
            // 设置响应信息
            LogResponse logResponse = logRecord.getResponse();
            this.setResponse(logDO, logResponse, compactAutomationLog);
            // 设置基本信息
            logDO.setDescription(logRecord.getDescription());
            logDO.setModule(StrUtils.blankToDefault(logRecord.getModule(), null, m -> m
                .replace("API", StringConstants.EMPTY)
                .trim()));
            logDO.setTimeTaken(logRecord.getTimeTaken().toMillis());
            logDO.setCreateTime(LocalDateTime.ofInstant(logRecord.getTimestamp(), ZoneId.systemDefault()));
            // 设置操作人
            this.setCreateUser(logDO, logRequest, logResponse);
            logMapper.insert(logDO);
        } catch (RuntimeException ex) {
            // 审计日志采集或入库异常不能让异步线程持续抛错放大业务故障。
            long now = System.currentTimeMillis();
            long lastWarning = LAST_LOG_PERSIST_FAILURE_WARNING.get();
            if (now - lastWarning >= LOG_PERSIST_FAILURE_WARNING_INTERVAL_MS && LAST_LOG_PERSIST_FAILURE_WARNING
                .compareAndSet(lastWarning, now)) {
                LOG.warn("系统审计日志入库失败，已降级为不阻塞业务请求，requestPath={}", requestPath, ex);
            }
        }
    }

    /**
     * 设置请求信息
     *
     * @param logDO      日志信息
     * @param logRequest 请求信息
     */
    private void setRequest(LogDO logDO, LogRequest logRequest, boolean compactAutomationLog) {
        logDO.setRequestMethod(logRequest.getMethod());
        logDO.setRequestUrl(logRequest.getUrl().toString());
        logDO.setRequestHeaders(JSONUtil.toJsonStr(this.sanitizeHeaders(logRequest.getHeaders())));
        logDO.setRequestBody(compactAutomationLog ? null : this.truncateBody(logRequest.getBody()));
        logDO.setIp(logRequest.getIp());
        logDO.setAddress(logRequest.getAddress());
        logDO.setBrowser(logRequest.getBrowser());
        logDO.setOs(StrUtil.subBefore(logRequest.getOs(), " or", false));
    }

    /**
     * 设置响应信息
     *
     * @param logDO       日志信息
     * @param logResponse 响应信息
     */
    private void setResponse(LogDO logDO, LogResponse logResponse, boolean compactAutomationLog) {
        Map<String, String> responseHeaders = logResponse == null ? Map.of() : logResponse.getHeaders();
        logDO.setResponseHeaders(JSONUtil.toJsonStr(this.sanitizeHeaders(responseHeaders)));
        logDO.setTraceId(MapUtil.isEmpty(responseHeaders)
            ? null
            : responseHeaders.get(traceProperties.getTraceIdName()));
        String responseBody = logResponse == null ? null : logResponse.getBody();
        logDO.setResponseBody(compactAutomationLog ? null : this.truncateBody(responseBody));
        // 状态
        Integer statusCode = logResponse == null ? null : logResponse.getStatus();
        logDO.setStatusCode(statusCode);
        logDO.setStatus(statusCode == null || statusCode >= HttpStatus.HTTP_BAD_REQUEST
            ? LogStatusEnum.FAILURE
            : LogStatusEnum.SUCCESS);
        if (!compactAutomationLog && StrUtil.isNotBlank(responseBody)) {
            try {
                R result = JSONUtil.toBean(responseBody, R.class);
                if (result != null && !result.isSuccess()) {
                    logDO.setStatus(LogStatusEnum.FAILURE);
                    logDO.setErrorMsg(result.getMsg());
                }
            } catch (RuntimeException ex) {
                // 非 JSON 响应仍需保留日志正文，不能因解析失败丢弃整条审计记录。
                logDO.setStatus(LogStatusEnum.FAILURE);
                logDO.setErrorMsg("响应体不是合法业务 JSON");
            }
        }
    }

    /**
     * 审计日志不能保存认证凭据，避免数据库泄露后令牌被直接复用。
     */
    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (MapUtil.isEmpty(headers)) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>(headers.size());
        headers.forEach((name, value) -> sanitized.put(name, SENSITIVE_HEADER_NAMES.contains(String.valueOf(name)
            .toLowerCase(Locale.ROOT)) ? "[REDACTED]" : value));
        return sanitized;
    }

    /**
     * 大请求和大响应只保留诊断前缀，防止自动化结果、场景 JSON 或 base64 撑大 sys_log。
     */
    private String truncateBody(String body) {
        if (body == null || body.length() <= MAX_PERSISTED_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_PERSISTED_BODY_LENGTH) + TRUNCATED_SUFFIX;
    }

    /**
     * 设置操作人
     *
     * @param logDO       日志信息
     * @param logRequest  请求信息
     * @param logResponse 响应信息
     */
    private void setCreateUser(LogDO logDO, LogRequest logRequest, LogResponse logResponse) {
        String requestUri = URLUtil.getPath(logDO.getRequestUrl());
        // 解析退出接口信息
        String responseBody = logResponse == null ? null : logResponse.getBody();
        if (requestUri.startsWith(SysConstants.LOGOUT_URI) && StrUtil.isNotBlank(responseBody)) {
            R result = JSONUtil.toBean(responseBody, R.class);
            logDO.setCreateUser(Convert.toLong(result.getData(), null));
            return;
        }
        // 解析登录接口信息
        if (requestUri.startsWith(SysConstants.LOGIN_URI) && LogStatusEnum.SUCCESS.equals(logDO.getStatus())) {
            String requestBody = logRequest.getBody();
            if (StrUtil.isBlank(requestBody)) {
                return;
            }
            logDO.setDescription(JSONUtil.toBean(requestBody, LoginReq.class).getAuthType().getDescription() + "登录");
            // 解析账号登录用户为操作人
            if (requestBody.contains(AuthTypeEnum.ACCOUNT.getValue())) {
                AccountLoginReq authReq = JSONUtil.toBean(requestBody, AccountLoginReq.class);
                logDO.setCreateUser(ExceptionUtils.exToNull(() -> userService.getByUsername(authReq.getUsername())
                    .getId()));
                return;
            } else if (requestBody.contains(AuthTypeEnum.EMAIL.getValue())) {
                EmailLoginReq authReq = JSONUtil.toBean(requestBody, EmailLoginReq.class);
                logDO.setCreateUser(ExceptionUtils.exToNull(() -> userService.getByEmail(authReq.getEmail()).getId()));
                return;
            } else if (requestBody.contains(AuthTypeEnum.PHONE.getValue())) {
                PhoneLoginReq authReq = JSONUtil.toBean(requestBody, PhoneLoginReq.class);
                logDO.setCreateUser(ExceptionUtils.exToNull(() -> userService.getByPhone(authReq.getPhone()).getId()));
                return;
            }
        }
        // 解析 Token 信息
        Map<String, String> requestHeaders = logRequest.getHeaders();
        String headerName = HttpHeaders.AUTHORIZATION;
        if (MapUtil.isNotEmpty(requestHeaders) && CollUtil.containsAny(requestHeaders.keySet(), Set
            .of(headerName, headerName.toLowerCase()))) {
            String authorization = requestHeaders.getOrDefault(headerName, requestHeaders.get(headerName
                .toLowerCase()));
            if (StrUtil.isBlank(authorization)) {
                return;
            }
            String token = authorization.replace(SaManager.getConfig()
                .getTokenPrefix() + StringConstants.SPACE, StringConstants.EMPTY);
            logDO.setCreateUser(Convert.toLong(StpUtil.getLoginIdByToken(token)));
        }
    }
}
