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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.mapper.AutomationNodeConfigMapper;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorCapabilityReq;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.admin.automation.support.AutomationExecutionAgentClient;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final AutomationUiSceneMapper sceneMapper;
    private final ProjectEnvironmentConfigMapper environmentMapper;
    private final ProjectConfigMapper projectConfigMapper;
    private final AutomationNodeConfigMapper nodeConfigMapper;
    private final AutomationExecutionAgentClient executionAgentClient;

    @Operation(summary = "读取操作能力目录")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping
    public R<AutomationOperationCatalog> getCatalog(@RequestParam Long sceneId,
                                                    @RequestParam(required = false) Long projectEnvironmentId,
                                                    @RequestParam(required = false) String executorInstanceId,
                                                    @RequestParam(required = false) String sessionId) {
        validateSceneEnvironment(sceneId, projectEnvironmentId);
        AgentHealth agentHealth = readAgentHealth();
        AutomationOperationCatalog catalog = catalogService
            .getCatalog(sceneId, projectEnvironmentId, executorInstanceId, principalScope(), sessionId, StpUtil
                .hasPermission("automation:automationUiScene:addStep"), StpUtil
                    .hasPermission("automation:automationUiScene:execute-infrastructure"), agentHealth
                        .agentTypes(), agentHealth.features());
        ProjectConfigDO project = projectConfigMapper.selectById(sceneMapper.selectById(sceneId).getProjectId());
        catalog.setV2Enabled(project == null || !Boolean.FALSE.equals(project.getAutomationOperationCatalogV2()));
        return R.ok(catalog);
    }

    @Operation(summary = "上报执行器 action 能力")
    @SaCheckPermission("automation:executor:capability:report")
    @PostMapping("/capabilities/{executorType:playwright|cuecast}")
    public R<Void> registerCapabilities(@PathVariable String executorType,
                                        @Valid @RequestBody AutomationExecutorCapabilityReq req) {
        validateEnvironment(req.getProjectEnvironmentId());
        if ("playwright".equalsIgnoreCase(executorType) && !isRegisteredExecutor(req.getExecutorInstanceId())) {
            throw new BusinessException("EXECUTOR_INSTANCE_NOT_REGISTERED：Playwright 执行器实例未绑定 Admin 节点配置");
        }
        catalogService.registerCapabilities(executorType, principalScope(), req);
        return R.ok();
    }

    private boolean isRegisteredExecutor(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        return nodeConfigMapper.selectCount(new LambdaQueryWrapper<AutomationNodeConfigDO>()
            .eq(AutomationNodeConfigDO::getName, instanceId)) > 0;
    }

    private void validateSceneEnvironment(Long sceneId, Long projectEnvironmentId) {
        AutomationUiSceneDO scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new BusinessException("操作目录所属场景不存在");
        }
        if (projectEnvironmentId == null) {
            return;
        }
        ProjectEnvironmentConfigDO environment = validateEnvironment(projectEnvironmentId);
        if (!scene.getProjectId().equals(environment.getProjectId())) {
            throw new BusinessException("项目环境不属于当前场景项目");
        }
    }

    private ProjectEnvironmentConfigDO validateEnvironment(Long projectEnvironmentId) {
        ProjectEnvironmentConfigDO environment = environmentMapper.selectById(projectEnvironmentId);
        if (environment == null) {
            throw new BusinessException("项目环境不存在");
        }
        return environment;
    }

    private String principalScope() {
        return "principal:" + StpUtil.getLoginId(-1L);
    }

    private AgentHealth readAgentHealth() {
        try {
            Map<String, Object> health = executionAgentClient.health();
            if (!"ok".equalsIgnoreCase(String.valueOf(health.get("status")))) {
                return AgentHealth.EMPTY;
            }
            return new AgentHealth(readStringSet(health.get("agent_types")), readStringSet(health.get("features")));
        } catch (BusinessException ignored) {
            // Agent 不可达时目录仍应返回；高风险方法由空能力集合安全禁用。
            return AgentHealth.EMPTY;
        }
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
}
