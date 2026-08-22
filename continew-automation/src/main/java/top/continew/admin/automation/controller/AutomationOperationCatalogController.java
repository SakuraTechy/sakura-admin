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

package top.continew.admin.automation.controller;

import jakarta.validation.Valid;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import cn.hutool.crypto.digest.DigestUtil;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorCapabilityReq;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorRegistrationReq;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.admin.automation.service.AutomationExecutorRegistrationService;
import top.continew.admin.automation.support.AutomationExecutionAgentClient;
import top.continew.admin.automation.support.AutomationUiQueryBaselineRecorder;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.web.model.R;

/**
 * UI 自动化操作能力目录 API。
 *
 * @author Codex
 */
@Tag(name = "自动化管理-操作能力目录 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/operation-catalog")
public class AutomationOperationCatalogController {

    private final AutomationOperationCatalogService catalogService;
    private final AutomationExecutorRegistrationService registrationService;
    private final AutomationUiSceneQueryMapper sceneQueryMapper;
    private final AutomationUiSceneAccessScopeResolver accessScopeResolver;
    private final ProjectEnvironmentConfigMapper environmentMapper;
    private final ProjectConfigMapper projectConfigMapper;
    private final AutomationExecutionAgentClient executionAgentClient;
    private final ObjectMapper objectMapper;

    private final Object agentHealthCacheLock = new Object();
    private volatile CachedAgentHealth cachedAgentHealth = CachedAgentHealth.EXPIRED;

    @Value("${automation.operation-catalog.health-cache-enabled:true}")
    private boolean healthCacheEnabled;

    @Value("${automation.operation-catalog.health-cache-ttl:5s}")
    private Duration healthCacheTtl;

    @Operation(summary = "读取操作能力目录")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping
    public ResponseEntity<R<AutomationOperationCatalog>> getCatalog(@RequestParam Long sceneId,
                                                                    @RequestParam(required = false) Long projectEnvironmentId,
                                                                    @RequestParam(required = false) String executorInstanceId,
                                                                    @RequestParam(required = false) String sessionId,
                                                                    @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Long projectId = validateSceneEnvironment(sceneId, projectEnvironmentId);
        AgentHealth agentHealth = readAgentHealth();
        String requestPrincipalScope = principalScope();
        boolean canAddStep = StpUtil.hasPermission("automation:automationUiScene:addStep");
        boolean canExecuteInfrastructure = StpUtil.hasPermission("automation:automationUiScene:execute-infrastructure");
        AutomationOperationCatalog catalog = catalogService
            .getCatalog(sceneId, projectEnvironmentId, executorInstanceId, requestPrincipalScope, sessionId, canAddStep, canExecuteInfrastructure, agentHealth
                .agentTypes(), agentHealth.features());
        AutomationUiQueryBaselineRecorder.recordSql();
        long queryStartedNanos = AutomationUiQueryBaselineRecorder.startTimedSection();
        ProjectConfigDO project;
        try {
            project = projectConfigMapper.selectById(projectId);
        } finally {
            AutomationUiQueryBaselineRecorder
                .recordTiming(AutomationUiQueryBaselineRecorder.Phase.OTHER_QUERY, queryStartedNanos);
        }
        catalog.setV2Enabled(project == null || !Boolean.FALSE.equals(project.getAutomationOperationCatalogV2()));
        String etag = catalogEtag(catalog, requestPrincipalScope);
        HttpHeaders headers = catalogHeaders(etag);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(R.ok(catalog));
    }

    @Operation(summary = "上报执行器 action 能力")
    @SaCheckPermission("automation:executor:capability:report")
    @PostMapping("/capabilities/{executorType:playwright|cuecast}")
    public R<Void> registerCapabilities(@PathVariable String executorType,
                                        @Valid @RequestBody AutomationExecutorCapabilityReq req) {
        validateEnvironment(req.getProjectEnvironmentId());
        String applicationAccessKey = currentApplicationAccessKey();
        if ("playwright".equalsIgnoreCase(executorType) && !registrationService.isRegistered(executorType, req
            .getExecutorInstanceId(), req.getProjectEnvironmentId(), applicationAccessKey)) {
            throw new BusinessException("EXECUTOR_INSTANCE_NOT_REGISTERED：Playwright 执行器实例未在 Admin 独立注册表中启用");
        }
        catalogService.registerCapabilities(executorType, principalScope(), req);
        if (registrationService.isRegistered(executorType, req.getExecutorInstanceId(), req
            .getProjectEnvironmentId(), applicationAccessKey)) {
            registrationService.recordReport(executorType, req);
        }
        return R.ok();
    }

    @Operation(summary = "注册执行器实例")
    @SaCheckPermission("automation:executor:registration:manage")
    @PostMapping("/executors")
    public R<Void> registerExecutor(@Valid @RequestBody AutomationExecutorRegistrationReq req) {
        requireHumanAdminForRegistrationManagement();
        registrationService.register(req);
        return R.ok();
    }

    @Operation(summary = "查询执行器注册和最近能力上报")
    @SaCheckPermission("automation:executor:registration:manage")
    @GetMapping("/executors/{executorType}/{executorInstanceId}")
    public R<top.continew.admin.automation.model.entity.AutomationExecutorRegistrationDO> getExecutor(@PathVariable String executorType,
                                                                                                      @PathVariable String executorInstanceId) {
        requireHumanAdminForRegistrationManagement();
        return R.ok(registrationService.find(executorType, executorInstanceId)
            .orElseThrow(() -> new BusinessException("执行器注册信息不存在：" + executorInstanceId)));
    }

    @Operation(summary = "禁用执行器实例")
    @SaCheckPermission("automation:executor:registration:manage")
    @PostMapping("/executors/{executorType}/{executorInstanceId}/disable")
    public R<Void> disableExecutor(@PathVariable String executorType, @PathVariable String executorInstanceId) {
        requireHumanAdminForRegistrationManagement();
        registrationService.disable(executorType, executorInstanceId);
        return R.ok();
    }

    private Long validateSceneEnvironment(Long sceneId, Long projectEnvironmentId) {
        AutomationUiQueryBaselineRecorder.recordSql();
        long queryStartedNanos = AutomationUiQueryBaselineRecorder.startTimedSection();
        Long projectId;
        try {
            AutomationUiSceneAccessScopeResolver.AccessScope scope = accessScopeResolver.currentScope();
            projectId = sceneQueryMapper.selectAuthorizedProjectId(sceneId, scope.userId(), scope.admin());
        } finally {
            AutomationUiQueryBaselineRecorder
                .recordTiming(AutomationUiQueryBaselineRecorder.Phase.OTHER_QUERY, queryStartedNanos);
        }
        if (projectId == null) {
            throw new BusinessException("操作目录所属场景不存在或无访问权限");
        }
        if (projectEnvironmentId == null) {
            return projectId;
        }
        ProjectEnvironmentConfigDO environment = validateEnvironment(projectEnvironmentId);
        if (!projectId.equals(environment.getProjectId())) {
            throw new BusinessException("项目环境不属于当前场景项目");
        }
        return projectId;
    }

    private ProjectEnvironmentConfigDO validateEnvironment(Long projectEnvironmentId) {
        AutomationUiQueryBaselineRecorder.recordSql();
        long queryStartedNanos = AutomationUiQueryBaselineRecorder.startTimedSection();
        ProjectEnvironmentConfigDO environment;
        try {
            environment = environmentMapper.selectById(projectEnvironmentId);
        } finally {
            AutomationUiQueryBaselineRecorder
                .recordTiming(AutomationUiQueryBaselineRecorder.Phase.OTHER_QUERY, queryStartedNanos);
        }
        if (environment == null) {
            throw new BusinessException("项目环境不存在");
        }
        return environment;
    }

    private String principalScope() {
        String applicationAccessKey = currentApplicationAccessKey();
        if (applicationAccessKey != null && !applicationAccessKey.isBlank()) {
            // 应用签名请求没有用户登录态；使用 Access Key 摘要隔离不同外部 Runner 的能力快照。
            return "application:" + DigestUtil.sha256Hex(applicationAccessKey);
        }
        return "principal:" + StpUtil.getLoginId(-1L);
    }

    private String currentApplicationAccessKey() {
        return SaHolder.getRequest().getParam("accessKey");
    }

    private void requireHumanAdminForRegistrationManagement() {
        if (currentApplicationAccessKey() != null && !currentApplicationAccessKey().isBlank()) {
            throw new BusinessException("外部应用不能变更执行器注册信息，请由管理员手动完成注册或禁用");
        }
    }

    private AgentHealth readAgentHealth() {
        if (!healthCacheEnabled) {
            return loadAgentHealth();
        }
        long now = System.nanoTime();
        CachedAgentHealth cached = cachedAgentHealth;
        if (cached.expiresAtNanos() > now) {
            return cached.health();
        }
        synchronized (agentHealthCacheLock) {
            now = System.nanoTime();
            cached = cachedAgentHealth;
            if (cached.expiresAtNanos() > now) {
                return cached.health();
            }
            AgentHealth health = loadAgentHealth();
            long ttlNanos = Math.min(TimeUnit.SECONDS.toNanos(10), Math.max(TimeUnit.SECONDS.toNanos(3), healthCacheTtl
                .toNanos()));
            cachedAgentHealth = new CachedAgentHealth(health, now + ttlNanos);
            return health;
        }
    }

    private AgentHealth loadAgentHealth() {
        long startedNanos = AutomationUiQueryBaselineRecorder.startExternalCall();
        try {
            Map<String, Object> health = executionAgentClient.health();
            if (!"ok".equalsIgnoreCase(String.valueOf(health.get("status")))) {
                return AgentHealth.EMPTY;
            }
            return new AgentHealth(readStringSet(health.get("agent_types")), readStringSet(health.get("features")));
        } catch (BusinessException ignored) {
            // Agent 不可达时目录仍应返回；高风险方法由空能力集合安全禁用。
            return AgentHealth.EMPTY;
        } finally {
            AutomationUiQueryBaselineRecorder.recordExternalCall(startedNanos);
        }
    }

    private String catalogEtag(AutomationOperationCatalog catalog, String requestPrincipalScope) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(catalog);
            String scopeDigest = DigestUtil.sha256Hex(requestPrincipalScope);
            return "\"" + DigestUtil.sha256Hex(scopeDigest + ":" + DigestUtil.sha256Hex(body)) + "\"";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("操作目录 ETag 计算失败", e);
        }
    }

    private HttpHeaders catalogHeaders(String etag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl("private, no-cache");
        headers.setVary(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.ACCEPT_ENCODING));
        return headers;
    }

    private Set<String> readStringSet(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (Object item : iterable) {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        }
        return Set.copyOf(result);
    }

    private record AgentHealth(Set<String> agentTypes, Set<String> features) {
        private static final AgentHealth EMPTY = new AgentHealth(Set.of(), Set.of());
    }

    private record CachedAgentHealth(AgentHealth health, long expiresAtNanos) {
        private static final CachedAgentHealth EXPIRED = new CachedAgentHealth(AgentHealth.EMPTY, Long.MIN_VALUE);
    }
}
