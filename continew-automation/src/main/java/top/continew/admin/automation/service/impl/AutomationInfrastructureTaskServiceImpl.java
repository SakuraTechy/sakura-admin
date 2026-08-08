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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.converter.AutomationInfrastructureRuntimeBindingResolver;
import top.continew.admin.automation.mapper.AutomationInfrastructureTaskLogMapper;
import top.continew.admin.automation.mapper.AutomationInfrastructureTaskMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationInfrastructureTaskDO;
import top.continew.admin.automation.model.entity.AutomationInfrastructureTaskLogDO;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskCreateReq;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskDispositionReq;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureTaskResp;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureTargetResp;
import top.continew.admin.automation.service.AutomationInfrastructureTaskService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.support.AutomationExecutionAgentClient;
import top.continew.admin.automation.support.AutomationExecutionCapability;
import top.continew.admin.automation.support.AutomationInfrastructureResultSanitizer;
import top.continew.admin.automation.support.AutomationInfrastructureRiskPolicy;
import top.continew.admin.automation.support.AutomationInfrastructureRiskPolicy.Assessment;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.admin.project.model.entity.ProjectDataBaseConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * 基础设施任务控制面实现。
 *
 * <p>这里不执行 SSH/JDBC，也不保存凭据；执行节点根据任务身份领取受控执行快照。
 * 这样浏览器回放端只能创建、查询和取消任务，无法篡改或读取真实命令、SQL、连接信息。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationInfrastructureTaskServiceImpl implements AutomationInfrastructureTaskService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final Set<String> INFRASTRUCTURE_ACTIONS = Set
        .of("server_command", "database_sql", "database_native", "host_command", "host_file_lookup", "host_file_delete", "server_file_upload", "global_variable_system_info", "global_variable_available_ip", "global_variable_property", "host_pointer_move", "captcha_ocr");
    private static final Set<String> SERVER_TARGET_ACTIONS = Set.of("server_command", "server_file_upload");
    private static final Set<String> DATABASE_TARGET_ACTIONS = Set.of("database_sql", "database_native");
    private static final Set<String> SAFE_RESULT_ACTIONS = Set
        .of("database_sql", "host_file_lookup", "host_file_delete", "server_file_upload", "global_variable_system_info", "global_variable_available_ip", "global_variable_property", "captcha_ocr");

    private final AutomationInfrastructureTaskMapper taskMapper;
    private final AutomationInfrastructureTaskLogMapper taskLogMapper;
    private final AutomationUiSceneMapper sceneMapper;
    private final ProjectEnvironmentConfigMapper environmentMapper;
    private final ProjectServerConfigMapper serverConfigMapper;
    private final ProjectDataBaseConfigMapper dataBaseConfigMapper;
    private final AutomationPlaywrightStepExtractor stepExtractor;
    private final AutomationInfrastructureRuntimeBindingResolver runtimeBindingResolver;
    private final ObjectMapper objectMapper;
    private final AutomationExecutionAgentClient executionAgentClient;
    private final AutomationUiExecutionRecordService executionRecordService;
    private final JdbcTemplate jdbcTemplate;
    private final AutomationInfrastructureResultSanitizer infrastructureResultSanitizer;
    private final AutomationInfrastructureRiskPolicy infrastructureRiskPolicy;

    @Override
    public List<AutomationInfrastructureTargetResp> listTargets(Long projectId, String kind) {
        if (projectId == null) {
            throw new BusinessException("项目 ID 不能为空");
        }
        return switch (kind == null ? "" : kind.trim().toLowerCase()) {
            case "server" -> serverConfigMapper.selectList(Wrappers.<ProjectServerConfigDO>lambdaQuery()
                .eq(ProjectServerConfigDO::getProjectId, projectId)
                .eq(ProjectServerConfigDO::getStatus, DisEnableStatusEnum.ENABLE)
                .eq(ProjectServerConfigDO::getDelFlag, StatusTypeEnum.NORMAL)
                .orderByDesc(ProjectServerConfigDO::getId)).parallelStream().map(this::toServerTargetResp).toList();
            case "database" -> dataBaseConfigMapper.selectList(Wrappers.<ProjectDataBaseConfigDO>lambdaQuery()
                .eq(ProjectDataBaseConfigDO::getProjectId, projectId)
                .eq(ProjectDataBaseConfigDO::getStatus, DisEnableStatusEnum.ENABLE)
                .eq(ProjectDataBaseConfigDO::getDelFlag, StatusTypeEnum.NORMAL)
                .orderByDesc(ProjectDataBaseConfigDO::getId)).parallelStream().map(this::toDataBaseTargetResp).toList();
            default -> throw new BusinessException("基础设施目标类型仅支持 server 或 database");
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationInfrastructureTaskResp create(AutomationInfrastructureTaskCreateReq req) {
        String stage = "初始化任务参数";
        try {
            Long ownerUserId = UserContextHolder.getUserId();
            String requestedCorrelation = normalizeExecutionCorrelation(req.getExecutionId());
            stage = "解析执行上下文";
            ExecutionContextRef executionContext = findExecutionContext(requestedCorrelation, req.getCaseKey(), req
                .getStepId());
            if (executionContext == null && ownerUserId != null) {
                String interactiveCorrelation = interactiveExecutionCorrelation(ownerUserId, requestedCorrelation);
                executionContext = findExecutionContext(interactiveCorrelation, req.getCaseKey(), req.getStepId());
                if (executionContext == null) {
                    executionContext = createInteractiveExecutionContext(req, interactiveCorrelation, ownerUserId);
                }
            }
            if (executionContext == null) {
                throw new BusinessException("EXECUTION_SCOPE_DENIED：未找到 capability 授权的 ExecutionContext");
            }
            requireExecutionContextAccess(executionContext, ownerUserId, req.getExecutionCapability());
            if (ownerUserId == null) {
                ownerUserId = executionContext.ownerUserId();
            }
            stage = "解析基础设施步骤";
            ResolvedStep resolved = resolveInfrastructureStep(executionContext, req.getStepId());
            // runtimeBindings 只在本次内存执行载荷中展开；任务表和日志均不会接触具体值。
            Map<String, Object> resolvedRawStep = runtimeBindingResolver.resolve(resolved.rawStep(), req
                .getRuntimeBindings());
            resolved = resolved.withRawStep(resolvedRawStep);
            if (req.getDefinitionVersion() != null && !req.getDefinitionVersion()
                .equals(executionContext.definitionVersion())) {
                throw new BusinessException("DEFINITION_REVISION_CONFLICT：请求版本与执行上下文 revision 不一致");
            }
            stage = "校验操作权限";
            Assessment risk = infrastructureRiskPolicy.assess(resolved.actionType(), resolved.rawStep());
            authorizeRisk(resolved.actionType(), risk);
            stage = "校验产品环境";
            validateExecutionEnvironment(executionContext, req.getProjectEnvironmentId());
            stage = "解析目标配置";
            ResolvedTarget target = resolveTarget(executionContext.projectId(), resolved.targetKind(), resolved
                .configId(), resolved.bindingKey());
            int attempt = 1;
            String idempotencyKey = executionContext.executionId() + ":" + executionContext
                .stepExecutionId() + ":" + attempt;
            String payloadDigest = taskPayloadDigest(resolved, req.getProjectEnvironmentId(), req
                .getRuntimeBindings(), req.getRuntimeInput());
            stage = "查询幂等任务";
            AutomationInfrastructureTaskDO existing = findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                requireTaskAccess(existing, req.getExecutionCapability());
                requireMatchingPayload(existing, payloadDigest);
                Map<String, Object> agentResponse = refreshFromAgent(existing);
                return toResp(existing, null, safeAgentResult(existing.getActionType(), agentResponse));
            }

            AutomationInfrastructureTaskDO task = new AutomationInfrastructureTaskDO();
            task.setTaskId("INFRA_" + UUID.randomUUID().toString().replace("-", ""));
            task.setCaseKey(req.getCaseKey());
            task.setStepId(req.getStepId());
            task.setActionType(resolved.actionType());
            task.setExecutionId(String.valueOf(executionContext.executionId()));
            task.setOwnerUserId(ownerUserId);
            task.setProjectEnvironmentId(req.getProjectEnvironmentId());
            task.setSceneId(executionContext.sceneId());
            task.setDefinitionVersion(executionContext.definitionVersion());
            task.setDefinitionRevisionId(executionContext.definitionRevisionId());
            task.setStepExecutionId(executionContext.stepExecutionId());
            task.setTargetKind(resolved.targetKind());
            task.setTargetConfigId(target.configId());
            task.setTargetBindingKey(resolved.bindingKey());
            task.setAttempt(attempt);
            task.setIdempotencyKey(idempotencyKey);
            task.setPayloadDigest(payloadDigest);
            task.setRiskLevel(risk.riskLevel());
            task.setCommandTemplateId(risk.commandTemplateId());
            task.setReadOnlyTransaction(risk.readOnlyTransaction() ? 1 : 0);
            LocalDateTime approvalAt = risk.approvalRequired() ? LocalDateTime.now() : null;
            task.setApprovalAt(approvalAt);
            task.setApprovalDigest(risk.approvalRequired()
                ? approvalDigest(executionContext, ownerUserId, req
                    .getProjectEnvironmentId(), payloadDigest, risk, approvalAt)
                : null);
            task.setStatus("queued");
            // BaseDO 的 updateTime 仅在更新时自动填充；新建任务也必须满足表的非空审计约束。
            task.setUpdateTime(LocalDateTime.now());
            stage = "写入基础设施任务";
            try {
                taskMapper.insert(task);
            } catch (DuplicateKeyException ignored) {
                AutomationInfrastructureTaskDO raced = findByIdempotencyKey(idempotencyKey);
                if (raced == null) {
                    throw new BusinessException("INFRA_TASK_IDEMPOTENCY_CONFLICT：并发创建后未找到任务");
                }
                requireTaskAccess(raced, req.getExecutionCapability());
                requireMatchingPayload(raced, payloadDigest);
                return toResp(raced, logsAfter(raced.getTaskId(), null), Map.of());
            }
            stage = "写入任务审计日志";
            appendLog(task.getTaskId(), "INFO", "任务已创建，等待执行节点领取");
            stage = "提交执行 Agent";
            Map<String, Object> agentResponse = dispatchToAgent(task, resolved.rawStep(), target.config(), req
                .getRuntimeInput());
            return toResp(task, logsAfter(task.getTaskId(), null), safeAgentResult(task
                .getActionType(), agentResponse));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 不输出请求原文，避免 SQL、命令或连接凭据被异常链带入日志。
            log.error("创建基础设施任务失败，stage={}，caseKey={}，stepId={}，errorType={}，message={}", stage, req.getCaseKey(), req
                .getStepId(), e.getClass().getSimpleName(), sanitize(e.getMessage()));
            throw new BusinessException("创建基础设施任务失败，阶段：" + stage);
        }
    }

    @Override
    public AutomationInfrastructureTaskResp get(String taskId, Long afterSequence) {
        return get(taskId, afterSequence, null);
    }

    @Override
    public AutomationInfrastructureTaskResp get(String taskId, Long afterSequence, String executionCapability) {
        AutomationInfrastructureTaskDO task = requireTask(taskId);
        requireTaskAccess(task, executionCapability);
        Map<String, Object> agentResponse = task.getDisposition() == null ? refreshFromAgent(task) : Map.of();
        return toResp(task, logsAfter(taskId, afterSequence), safeAgentResult(task.getActionType(), agentResponse));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationInfrastructureTaskResp cancel(String taskId) {
        return cancel(taskId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationInfrastructureTaskResp cancel(String taskId, String executionCapability) {
        AutomationInfrastructureTaskDO task = requireTask(taskId);
        requireTaskAccess(task, executionCapability);
        if (!isTerminal(task.getStatus())) {
            task.setCancelRequestedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            appendLog(taskId, "INFO", "已请求取消基础设施任务");
            try {
                Map<String, Object> agentResponse = executionAgentClient.cancel(taskId);
                applyAgentResponse(task, agentResponse);
                return toResp(task, logsAfter(taskId, null), safeAgentResult(task.getActionType(), agentResponse));
            } catch (BusinessException e) {
                markUnknownOutcome(task, "执行 Agent 取消响应未确认，任务是否停止未知");
            }
        }
        return toResp(task, logsAfter(taskId, null), Map.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationInfrastructureTaskResp disposeUnknownOutcome(String taskId,
                                                                  AutomationInfrastructureTaskDispositionReq req,
                                                                  String executionCapability) {
        AutomationInfrastructureTaskDO task = requireTask(taskId);
        requireTaskAccess(task, executionCapability);
        if (!"unknown_outcome".equals(task.getStatus()) || task.getDisposition() != null) {
            throw new BusinessException("TASK_DISPOSITION_NOT_ALLOWED：仅允许处置尚未核验的 UNKNOWN_OUTCOME 任务");
        }
        String resolution = req.getResolution();
        String noteDigest = DigestUtil.sha256Hex(req.getVerificationNote().trim());
        Long dispositionUserId = UserContextHolder.getUserId();
        task.setDisposition(resolution);
        task.setDispositionUserId(dispositionUserId);
        task.setDispositionAt(LocalDateTime.now());
        task.setDispositionNoteDigest(noteDigest);
        task.setFinishedAt(LocalDateTime.now());
        if ("confirmed_succeeded".equals(resolution)) {
            task.setStatus("passed");
            task.setErrorCode("TASK_OUTCOME_CONFIRMED_PASSED");
            task.setErrorMessage(null);
        } else if ("confirmed_failed".equals(resolution)) {
            task.setStatus("failed");
            task.setErrorCode("TASK_OUTCOME_CONFIRMED_FAILED");
            task.setErrorMessage("人工核验任务未成功完成，核验说明摘要=" + noteDigest);
        } else {
            throw new BusinessException("TASK_DISPOSITION_INVALID：不支持的人工核验结论");
        }
        taskMapper.updateById(task);
        appendLog(taskId, "WARN", "UNKNOWN_OUTCOME 已由主体 " + dispositionUserId + " 核验为 " + resolution + "，说明摘要=" + noteDigest);
        return toResp(task, logsAfter(taskId, null), Map.of());
    }

    @Override
    public ArtifactDownload downloadArtifact(String taskId, String executionCapability) {
        AutomationInfrastructureTaskDO task = requireTask(taskId);
        requireTaskAccess(task, executionCapability);
        AutomationExecutionAgentClient.ArtifactDownload artifact = executionAgentClient.downloadArtifact(taskId);
        return new ArtifactDownload(artifact.fileName(), artifact.contentType(), artifact.bytes(), artifact.sha256());
    }

    private ExecutionContextRef createInteractiveExecutionContext(AutomationInfrastructureTaskCreateReq req,
                                                                  String executionCorrelation,
                                                                  Long ownerUserId) {
        String[] caseKeyParts = splitCaseKey(req.getCaseKey());
        AutomationUiSceneDO scene = resolveScene(caseKeyParts[0]);
        if (scene == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：未找到交互回放场景");
        }
        CaseDO caseDO = scene.getCaseList() == null
            ? null
            : scene.getCaseList()
                .stream()
                .filter(item -> Objects.equals(caseKeyParts[1], item.getId()))
                .findFirst()
                .orElse(null);
        if (caseDO == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：未找到交互回放用例");
        }
        validateProjectEnvironment(scene, req.getProjectEnvironmentId());

        Map<String, Object> caseResult = new LinkedHashMap<>();
        caseResult.put("case_key", req.getCaseKey());
        caseResult.put("case_id", caseDO.getId());
        caseResult.put("case_name", caseDO.getName());
        caseResult.put("execution_id", executionCorrelation);
        caseResult.put("status", "running");
        caseResult.put("step_total", caseDO.getStepList() == null ? 0 : caseDO.getStepList().size());
        caseResult.put("steps", List.of());

        String contextKey = "interactive-" + ownerUserId + "-" + DigestUtil.sha256Hex(executionCorrelation)
            .substring(0, 32);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordType", "interactive-execution-context");
        record.put("executionId", contextKey);
        record.put("batchId", contextKey);
        record.put("executionType", "extension-cdp");
        record.put("executeUserId", ownerUserId);
        record.put("projectEnvironmentId", req.getProjectEnvironmentId());
        record.put("executeStatus", "running");
        record.put("executeResult", "pending");
        record.put("caseResults", List.of(caseResult));
        executionRecordService.saveRecord(scene, record, null);

        ExecutionContextRef created = findExecutionContext(executionCorrelation, req.getCaseKey(), req.getStepId());
        if (created == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：轻量 ExecutionContext 创建失败");
        }
        return created;
    }

    private ExecutionContextRef findExecutionContext(String executionCorrelation, String caseKey, String stepId) {
        List<ExecutionContextRef> matches = jdbcTemplate
            .query("SELECT e.id AS execution_id," + " e.definition_revision_id, e.execute_user_id, e.project_environment_id, e.scene_id," + " e.execution_capability_digest, e.execution_capability_expires_at," + " scene.project_id, revision.definition_version, revision.definition_json," + " execution_case.id AS execution_case_id, execution_case.case_id," + " execution_step.id AS step_execution_id" + " FROM automation_ui_execution_case execution_case" + " JOIN automation_ui_execution e ON e.id = execution_case.execution_id" + " JOIN automation_ui_scene_definition_revision revision ON revision.id = e.definition_revision_id" + " JOIN automation_ui_scene scene ON scene.id = e.scene_id" + " JOIN automation_ui_execution_step execution_step" + "   ON execution_step.execution_case_id = execution_case.id" + "  AND execution_step.step_id = ? AND execution_step.attempt_no = 1" + " WHERE execution_case.case_execution_key = ? AND execution_case.case_key = ?" + " ORDER BY e.create_time DESC LIMIT 1", (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          rowNum) -> new ExecutionContextRef(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              .getLong("execution_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  .getLong("definition_revision_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      .getLong("execute_user_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          .getLong("project_environment_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              .getLong("scene_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  .getLong("project_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      .getLong("definition_version"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          .getString("definition_json"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              .getLong("execution_case_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  .getString("case_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      .getLong("step_execution_id"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          .getString("execution_capability_digest"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              .getTimestamp("execution_capability_expires_at") == null
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  ? null
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  : rs.getTimestamp("execution_capability_expires_at")
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      .toLocalDateTime()), stepId, executionCorrelation, caseKey);
        return matches.stream().findFirst().orElse(null);
    }

    private void requireExecutionContextAccess(ExecutionContextRef context, Long ownerUserId, String capability) {
        if (Objects.equals(context.ownerUserId(), ownerUserId)) {
            return;
        }
        if (!AutomationExecutionCapability.matches(capability, context.capabilityDigest(), context
            .capabilityExpiresAt())) {
            throw new BusinessException("EXECUTION_SCOPE_DENIED：当前主体不属于该 ExecutionContext");
        }
    }

    private void validateExecutionEnvironment(ExecutionContextRef context, Long requestedEnvironmentId) {
        if (!Objects.equals(context.projectEnvironmentId(), requestedEnvironmentId)) {
            throw new BusinessException("EXECUTION_SCOPE_DENIED：任务环境与 ExecutionContext 不一致");
        }
        ProjectEnvironmentConfigDO environment = environmentMapper.selectById(requestedEnvironmentId);
        if (environment == null || !Objects.equals(context.projectId(), environment.getProjectId())) {
            throw new BusinessException("EXECUTION_SCOPE_DENIED：任务环境不属于执行项目");
        }
        if (environment.getStatus() != DisEnableStatusEnum.ENABLE) {
            throw new BusinessException("产品环境已禁用");
        }
    }

    private String normalizeExecutionCorrelation(String executionId) {
        String value = executionId == null || executionId.isBlank()
            ? "interaction-" + UUID.randomUUID()
            : executionId.trim();
        return value.length() <= 128 ? value : "sha256:" + DigestUtil.sha256Hex(value);
    }

    private String interactiveExecutionCorrelation(Long ownerUserId, String requestedCorrelation) {
        String value = "user:" + ownerUserId + ":" + requestedCorrelation;
        return value.length() <= 128 ? value : "user:" + ownerUserId + ":sha256:" + DigestUtil.sha256Hex(value);
    }

    private String[] splitCaseKey(String caseKey) {
        String[] parts = caseKey == null ? new String[0] : caseKey.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BusinessException("caseKey 必须为 sceneId:caseId 格式");
        }
        return parts;
    }

    private ResolvedStep resolveInfrastructureStep(ExecutionContextRef context, String stepId) {
        List<CaseDO> frozenCases;
        try {
            frozenCases = objectMapper.readValue(context.definitionJson(), new TypeReference<List<CaseDO>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("DEFINITION_REVISION_INVALID：定义 revision JSON 无法解析");
        }
        CaseDO caseDO = frozenCases.stream()
            .filter(item -> Objects.equals(context.caseId(), item.getId()))
            .findFirst()
            .orElse(null);
        if (caseDO == null || caseDO.getStepList() == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：revision 中不存在目标用例");
        }
        StepDO step = caseDO.getStepList()
            .stream()
            .filter(item -> stepId.equals(item.getId()))
            .findFirst()
            .orElse(null);
        if (step == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：revision 中不存在目标步骤");
        }
        Map<String, Object> rawStep = stepExtractor.extract(step, 0);
        runtimeBindingResolver.rejectVariablesInRoutingFields(rawStep);
        String actionType = String.valueOf(rawStep.get("action_type"));
        if (!INFRASTRUCTURE_ACTIONS.contains(actionType)) {
            throw new BusinessException("当前步骤不是基础设施操作");
        }
        if (!SERVER_TARGET_ACTIONS.contains(actionType) && !DATABASE_TARGET_ACTIONS.contains(actionType)) {
            return new ResolvedStep(context, actionType, "agent", null, null, rawStep);
        }
        Map<String, Object> targetRef = readMap(rawStep.get("target_ref"));
        String targetScope = stringValue(targetRef.get("scope"));
        String targetKind = stringValue(targetRef.get("kind"));
        Long configId = configIdValue(targetRef.get("config_id"));
        String bindingKey = stringValue(targetRef.get("binding_key"));
        String expectedKind = SERVER_TARGET_ACTIONS.contains(actionType) ? "server" : "database";
        if (!"project_config".equals(targetScope)) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：target_ref.scope 必须为 project_config");
        }
        if (!expectedKind.equals(targetKind)) {
            throw new BusinessException("INFRA_TARGET_KIND_MISMATCH：当前操作需要 target_ref.kind=" + expectedKind);
        }
        if (configId == null && (bindingKey == null || bindingKey.isBlank())) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：target_ref 必须包含正数 config_id 或 binding_key");
        }
        return new ResolvedStep(context, actionType, targetKind, configId, bindingKey, rawStep);
    }

    /** Runner 的 caseKey 同时兼容场景数据库主键和业务 sceneId。 */
    private AutomationUiSceneDO resolveScene(String sceneKey) {
        AutomationUiSceneDO scene = null;
        try {
            scene = sceneMapper.selectById(Long.valueOf(sceneKey));
        } catch (NumberFormatException ignored) {
            // 非数字键按业务 sceneId 继续查询。
        }
        return scene != null
            ? scene
            : sceneMapper.selectOne(Wrappers.<AutomationUiSceneDO>lambdaQuery()
                .eq(AutomationUiSceneDO::getSceneId, sceneKey));
    }

    private void validateProjectEnvironment(AutomationUiSceneDO scene, Long environmentId) {
        ProjectEnvironmentConfigDO environment = environmentMapper.selectById(environmentId);
        if (environment == null || !scene.getProjectId().equals(environment.getProjectId())) {
            throw new BusinessException("产品环境不存在或不属于当前场景项目");
        }
        if (environment.getStatus() != DisEnableStatusEnum.ENABLE) {
            throw new BusinessException("产品环境已禁用");
        }
    }

    private void authorizeRisk(String actionType, Assessment risk) {
        if (!risk.approvalRequired())
            return;
        if ("host_file_delete".equals(actionType)) {
            requirePermission("automation:automationUiScene:execute-host-file-delete", "本机文件删除");
        } else if ("server_command".equals(actionType) || "host_command".equals(actionType)) {
            requirePermission("automation:automationUiScene:execute-dangerous-command", "服务器或本机命令");
        } else if ("destructive".equals(risk.riskLevel())) {
            requirePermission("automation:automationUiScene:execute-dangerous-sql", "破坏性数据库操作");
        } else {
            requirePermission("automation:automationUiScene:execute-infrastructure-write", "写入型基础设施操作");
        }
    }

    private void requirePermission(String permission, String operation) {
        if (!StpUtil.hasPermission(permission)) {
            throw new BusinessException("执行" + operation + "需要权限：" + permission);
        }
    }

    private ResolvedTarget resolveTarget(Long projectId, String targetKind, Long configId, String legacyBindingKey) {
        if ("agent".equals(targetKind)) {
            return new ResolvedTarget(null, Map.of());
        }
        if (configId != null) {
            return loadTargetById(projectId, targetKind, configId);
        }
        return loadLegacyTargetByBindingKey(projectId, targetKind, legacyBindingKey);
    }

    private ResolvedTarget loadTargetById(Long projectId, String targetKind, Long configId) {
        if ("server".equals(targetKind)) {
            ProjectServerConfigDO config = serverConfigMapper.selectById(configId);
            validateServerTarget(config, projectId);
            return new ResolvedTarget(config.getId(), readMap(config));
        }
        ProjectDataBaseConfigDO config = dataBaseConfigMapper.selectById(configId);
        validateDataBaseTarget(config, projectId);
        return new ResolvedTarget(config.getId(), readMap(config));
    }

    private ResolvedTarget loadLegacyTargetByBindingKey(Long projectId, String targetKind, String bindingKey) {
        if ("server".equals(targetKind)) {
            List<ProjectServerConfigDO> matches = serverConfigMapper.selectList(Wrappers
                .<ProjectServerConfigDO>lambdaQuery()
                .eq(ProjectServerConfigDO::getProjectId, projectId)
                .eq(ProjectServerConfigDO::getBindingKey, bindingKey));
            if (matches.size() != 1) {
                throw new BusinessException("未找到唯一的旧版服务器目标绑定：" + bindingKey);
            }
            ProjectServerConfigDO config = matches.get(0);
            validateServerTarget(config, projectId);
            return new ResolvedTarget(config.getId(), readMap(config));
        }
        List<ProjectDataBaseConfigDO> matches = dataBaseConfigMapper.selectList(Wrappers
            .<ProjectDataBaseConfigDO>lambdaQuery()
            .eq(ProjectDataBaseConfigDO::getProjectId, projectId)
            .eq(ProjectDataBaseConfigDO::getBindingKey, bindingKey));
        if (matches.size() != 1) {
            throw new BusinessException("未找到唯一的旧版数据库目标绑定：" + bindingKey);
        }
        ProjectDataBaseConfigDO config = matches.get(0);
        validateDataBaseTarget(config, projectId);
        return new ResolvedTarget(config.getId(), readMap(config));
    }

    private void validateServerTarget(ProjectServerConfigDO config, Long projectId) {
        if (config == null || !projectId.equals(config.getProjectId())) {
            throw new BusinessException("服务器配置不存在或不属于当前项目");
        }
        if (config.getStatus() != DisEnableStatusEnum.ENABLE || config.getDelFlag() != StatusTypeEnum.NORMAL) {
            throw new BusinessException("服务器配置已禁用或删除");
        }
    }

    private void validateDataBaseTarget(ProjectDataBaseConfigDO config, Long projectId) {
        if (config == null || !projectId.equals(config.getProjectId())) {
            throw new BusinessException("数据库配置不存在或不属于当前项目");
        }
        if (config.getStatus() != DisEnableStatusEnum.ENABLE || config.getDelFlag() != StatusTypeEnum.NORMAL) {
            throw new BusinessException("数据库配置已禁用或删除");
        }
    }

    private AutomationInfrastructureTargetResp toServerTargetResp(ProjectServerConfigDO config) {
        AutomationInfrastructureTargetResp resp = new AutomationInfrastructureTargetResp();
        resp.setId(config.getId());
        resp.setKind("server");
        resp.setType(config.getType());
        resp.setIp(config.getIp());
        resp.setPort(config.getPort());
        resp.setDescription(config.getDescription());
        resp.setOnline(checkEndpointOnline(config.getIp(), config.getPort() == null ? 22 : config.getPort()));
        return resp;
    }

    private AutomationInfrastructureTargetResp toDataBaseTargetResp(ProjectDataBaseConfigDO config) {
        AutomationInfrastructureTargetResp resp = new AutomationInfrastructureTargetResp();
        resp.setId(config.getId());
        resp.setKind("database");
        resp.setType(config.getType());
        resp.setIp(config.getIp());
        resp.setPort(config.getPort());
        resp.setDataBase(config.getDataBase());
        resp.setDescription(config.getDescription());
        resp.setOnline(checkEndpointOnline(config.getIp(), config.getPort()));
        return resp;
    }

    /**
     * 下拉框只做轻量 TCP 探测，避免在打开步骤弹窗时执行真实 SSH/JDBC 登录或暴露凭据。
     * 在线表示端口可达，最终认证和执行仍由 Agent 按目标配置完成。
     */
    private boolean checkEndpointOnline(String host, Integer port) {
        if (host == null || host.isBlank() || port == null || port < 1 || port > 65535) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> dispatchToAgent(AutomationInfrastructureTaskDO task,
                                                Map<String, Object> rawStep,
                                                Map<String, Object> target,
                                                Map<String, Object> runtimeInput) {
        try {
            Map<String, Object> response = executionAgentClient
                .submit(buildAgentPayload(task, rawStep, target, runtimeInput));
            applyAgentResponse(task, response);
            appendLog(task.getTaskId(), "INFO", "基础设施任务已提交至本机执行 Agent");
            return response;
        } catch (BusinessException e) {
            String detail = sanitize(e.getMessage());
            String failureMessage = detail == null || detail.isBlank() ? "未收到 Agent 具体错误" : detail;
            log.warn("基础设施任务提交 Agent 失败，taskId={}，actionType={}，error={}", task.getTaskId(), task
                .getActionType(), failureMessage);
            // POST 连接半关闭时 Agent 可能已经接收并开始执行，不能把未知结果伪装成确定失败后自动重试。
            markUnknownOutcome(task, "执行 Agent 提交响应未确认：" + failureMessage);
            return Map.of();
        }
    }

    private void markUnknownOutcome(AutomationInfrastructureTaskDO task, String message) {
        task.setStatus("unknown_outcome");
        task.setErrorCode("TASK_UNKNOWN_OUTCOME");
        task.setErrorMessage(limit(sanitize(message)));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        appendLog(task.getTaskId(), "WARN", task.getErrorMessage());
    }

    private Map<String, Object> buildAgentPayload(AutomationInfrastructureTaskDO task,
                                                  Map<String, Object> rawStep,
                                                  Map<String, Object> target,
                                                  Map<String, Object> runtimeInput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("actionType", task.getActionType());
        payload.put("timeoutMs", numberValue(rawStep.get("timeout_ms"), 30000));
        payload.put("maxRows", numberValue(rawStep.get("max_rows"), 100));
        addAgentApprovalPayload(payload, task, rawStep);
        switch (task.getActionType()) {
            case "server_command" -> addSshCommandPayload(payload, rawStep, target);
            case "database_sql" -> addJdbcPayload(payload, rawStep, target);
            case "database_native" -> addMongoPayload(payload, rawStep, target);
            case "host_command" -> {
                payload.put("command", rawStep.get("command"));
                // 未指定时由 Agent 按宿主平台选择受控默认 Shell，不能把 Windows 默认强加给 Linux 节点。
                payload.put("shell", rawStep.get("shell"));
                payload.put("workingDirectory", rawStep.get("working_directory"));
            }
            case "host_file_lookup" -> {
                payload.put("filePath", filePathValue(firstValue(rawStep, "path", "file_path", "file_ref")));
                payload.put("filePattern", rawStep.getOrDefault("pattern", "*"));
                payload.put("recursive", booleanValue(rawStep.get("recursive"), false));
                payload.put("maxResults", numberValue(rawStep.get("max_results"), 100));
                payload.put("variableName", rawStep.get("variable_name"));
            }
            case "host_file_delete" -> {
                payload.put("filePath", filePathValue(firstValue(rawStep, "path", "file_path", "file_ref")));
                payload.put("recursive", booleanValue(rawStep.get("recursive"), false));
            }
            case "server_file_upload" -> addSftpPayload(payload, rawStep, target);
            case "global_variable_system_info" -> {
                payload.put("variableName", rawStep.get("variable_name"));
                payload.put("infoType", rawStep.get("info_type"));
            }
            case "global_variable_available_ip" -> {
                payload.put("variableName", rawStep.get("variable_name"));
                payload.put("ipPrefix", rawStep.get("ip_prefix"));
                payload.put("start", rawStep.get("start"));
                payload.put("end", rawStep.get("end"));
            }
            case "global_variable_property" -> {
                payload.put("variableName", rawStep.get("variable_name"));
                payload.put("profile", rawStep.get("profile"));
                payload.put("propertyKey", rawStep.get("property_key"));
            }
            case "host_pointer_move" -> {
                payload.put("x", rawStep.get("x"));
                payload.put("y", rawStep.get("y"));
            }
            case "captcha_ocr" -> {
                payload.put("variableName", rawStep.get("variable_name"));
                String imageBase64 = stringValue(runtimeInput == null
                    ? null
                    : runtimeInput.get("captcha_image_base64"));
                if (imageBase64.isBlank() || imageBase64.length() > 3_000_000) {
                    throw new BusinessException("验证码 OCR 截图缺失或超过大小限制");
                }
                // 截图仅在本次到 Agent 的内存请求中转发，任务表、日志和错误信息都不记录图片内容。
                payload.put("captchaImageBase64", imageBase64);
            }
            default -> throw new BusinessException("不支持的基础设施操作：" + task.getActionType());
        }
        return payload;
    }

    private void addSshCommandPayload(Map<String, Object> payload,
                                      Map<String, Object> rawStep,
                                      Map<String, Object> target) {
        payload.put("command", rawStep.get("command"));
        payload.put("shell", rawStep.getOrDefault("shell", "bash"));
        payload.put("sshTarget", sshTarget(target));
    }

    private void addSftpPayload(Map<String, Object> payload, Map<String, Object> rawStep, Map<String, Object> target) {
        payload.put("sourcePath", filePathValue(firstValue(rawStep, "source_path", "file_path", "file_ref")));
        payload.put("remotePath", rawStep.get("remote_path"));
        payload.put("overwrite", booleanValue(rawStep.get("overwrite"), false));
        payload.put("sshTarget", sshTarget(target));
    }

    private Map<String, Object> sshTarget(Map<String, Object> target) {
        Map<String, Object> sshTarget = new LinkedHashMap<>();
        sshTarget.put("host", target.get("ip"));
        sshTarget.put("port", target.getOrDefault("port", 22));
        sshTarget.put("username", target.get("userName"));
        sshTarget.put("password", target.get("passWord"));
        sshTarget.put("platform", target.get("type"));
        return sshTarget;
    }

    private void addJdbcPayload(Map<String, Object> payload, Map<String, Object> rawStep, Map<String, Object> target) {
        DatabaseDriverSpec driverSpec = databaseDriverSpec(stringValue(target.get("type")));
        if ("mongodb".equals(driverSpec.profile())) {
            throw new BusinessException("MongoDB 仅支持 database_native 操作");
        }
        payload.put("sqlMode", rawStep.getOrDefault("sql_mode", "query"));
        payload.put("sql", rawStep.get("sql"));
        payload.put("parameters", rawStep.getOrDefault("parameters", List.of()));
        // 只有显式 result_binding/variable_name 才允许 Agent 把查询行短暂回传到当前 Runner/CDP。
        payload.put("variableName", firstValue(rawStep, "variable_name", "result_binding"));
        Map<String, Object> jdbcTarget = new LinkedHashMap<>();
        jdbcTarget.put("driverProfile", driverSpec.profile());
        jdbcTarget.put("driverClass", driverSpec.driverClass());
        jdbcTarget.put("jdbcUrl", target.get("url"));
        jdbcTarget.put("username", target.get("userName"));
        jdbcTarget.put("password", target.get("passWord"));
        payload.put("jdbcTarget", jdbcTarget);
    }

    private void addMongoPayload(Map<String, Object> payload, Map<String, Object> rawStep, Map<String, Object> target) {
        if (!"mongodb".equals(databaseDriverSpec(stringValue(target.get("type"))).profile())) {
            throw new BusinessException("database_native 当前仅支持 MongoDB 目标");
        }
        payload.put("mongoOperation", rawStep.get("mongo_operation"));
        payload.put("collection", rawStep.get("collection"));
        payload.put("filter", parseObjectMap(rawStep.get("filter")));
        payload.put("document", parseObjectMap(rawStep.get("document")));
        Map<String, Object> mongoTarget = new LinkedHashMap<>();
        mongoTarget.put("connectionString", target.get("url"));
        mongoTarget.put("database", target.get("dataBase"));
        payload.put("mongoTarget", mongoTarget);
    }

    /** Agent 将 capability/approval 作为第二道防线；审批事实来自刚刚完成的 Admin 权限校验。 */
    private void addAgentApprovalPayload(Map<String, Object> payload,
                                         AutomationInfrastructureTaskDO task,
                                         Map<String, Object> rawStep) {
        payload.put("riskLevel", task.getRiskLevel());
        payload.put("readOnlyEnforced", Objects.equals(task.getReadOnlyTransaction(), 1));
        payload.put("commandTemplateId", task.getCommandTemplateId());
        if (task.getApprovalDigest() != null) {
            payload.put("approvalGranted", true);
            payload.put("approvalId", "admin-approval:" + task.getTaskId());
            payload.put("approvalDigest", task.getApprovalDigest());
        }
        switch (task.getActionType()) {
            case "host_command" -> addCapability(payload, "host_command");
            case "host_file_delete" -> {
                addCapability(payload, "host_file_delete");
            }
            case "host_file_lookup" -> addCapability(payload, "host_file_lookup");
            case "server_file_upload" -> addCapability(payload, "server_file_upload");
            case "host_pointer_move" -> addCapability(payload, "host_pointer_move");
            case "captcha_ocr" -> addCapability(payload, "captcha_ocr");
            default -> {
                // 其他动作不需要附加的主机高危能力声明。
            }
        }
    }

    private void addCapability(Map<String, Object> payload, String capability) {
        payload.put("capability", capability);
        payload.put("capabilities", List.of(capability));
    }

    private Map<String, Object> refreshFromAgent(AutomationInfrastructureTaskDO task) {
        try {
            Map<String, Object> response = executionAgentClient.get(task.getTaskId());
            applyAgentResponse(task, response);
            return response;
        } catch (BusinessException e) {
            String detail = sanitize(e.getMessage());
            // 已终态任务可能因 Agent 重启无法再返回临时 result；此时不反复写入相同告警。
            if (!isTerminal(task.getStatus())) {
                appendLog(task.getTaskId(), "WARN", "读取执行 Agent 状态失败：" + (detail == null ? "未收到具体错误" : detail));
            }
            return Map.of();
        }
    }

    private void applyAgentResponse(AutomationInfrastructureTaskDO task, Map<String, Object> response) {
        String status = stringValue(response.get("status"));
        if (status == null || status.isBlank()) {
            return;
        }
        task.setStatus(status);
        task.setExitCode(intValue(response.get("exitCode")));
        Object affectedRows = response.get("affectedRows");
        task.setAffectedRows(affectedRows == null ? null : Long.valueOf(String.valueOf(affectedRows)));
        task.setErrorCode(limit(stringValue(response.get("errorCode"))));
        task.setErrorMessage(sanitize(stringValue(response.get("error"))));
        task.setResultSummary(sanitize(agentSummary(response)));
        Map<String, Object> safeResult = safeAgentResult(task.getActionType(), response);
        if (safeResult.get("infrastructure") instanceof Map<?, ?> infrastructure) {
            task.setResultJson(infrastructureResultSanitizer.serializePreview(readMap(infrastructure)));
        }
        if (task.getStartedAt() == null && !"queued".equals(status)) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (isTerminal(status) && task.getFinishedAt() == null) {
            task.setFinishedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    private String agentSummary(Map<String, Object> response) {
        Object duration = response.get("durationMs");
        Object rows = response.get("rows");
        String rowSummary = rows instanceof List<?> list ? "rows=" + list.size() : "";
        return "durationMs=" + (duration == null ? 0 : duration) + (rowSummary.isBlank() ? "" : ", " + rowSummary);
    }

    /**
     * 任务记录不保存 Agent result。仅向当前调用方返回动作白名单中的摘要，使全局变量动作可被 Runner/CDP 接续使用。
     */
    private Map<String, Object> safeAgentResult(String actionType, Map<String, Object> response) {
        if (response == null || !(response.get("result") instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = readMap(raw);
        Map<String, Object> safeResult = new LinkedHashMap<>();
        Map<String, Object> infrastructure = infrastructureResultSanitizer.sanitize(result.get("infrastructure"));
        if (!infrastructure.isEmpty()) {
            safeResult.put("infrastructure", infrastructure);
        }
        if (!SAFE_RESULT_ACTIONS.contains(actionType)) {
            return safeResult;
        }
        Map<String, Object> variables = switch (actionType) {
            case "database_sql" -> safeDatabaseVariables(result.get("variables"));
            case "global_variable_system_info", "global_variable_available_ip", "global_variable_property",
                "captcha_ocr" -> safeVariables(result.get("variables"));
            case "host_file_lookup" -> safeFileLookupResult(result);
            case "host_file_delete" -> safeNumberResult(result, "deleted_count", "deletedCount");
            case "server_file_upload" -> safeNumberResult(result, "uploaded_bytes", "uploadedBytes");
            default -> Map.of();
        };
        safeResult.putAll(variables);
        return safeResult;
    }

    private Map<String, Object> safeVariables(Object source) {
        if (!(source instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> || value instanceof List<?>) {
                continue;
            }
            variables.put(key, limit(sanitize(stringValue(value))));
        }
        return variables.isEmpty() ? Map.of() : Map.of("variables", variables);
    }

    /**
     * 数据库变量只在本次任务响应中短暂返回，不能落库或写日志。
     * 行数、字段数和字符串长度均受限，避免一次查询把 case 响应放大为数据导出通道。
     */
    private Map<String, Object> safeDatabaseVariables(Object source) {
        if (!(source instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}") || !(entry
                .getValue() instanceof List<?> rows)) {
                continue;
            }
            List<Map<String, Object>> safeRows = new ArrayList<>();
            for (Object row : rows.stream().limit(100).toList()) {
                if (!(row instanceof Map<?, ?> rawRow)) {
                    continue;
                }
                Map<String, Object> safeRow = new LinkedHashMap<>();
                int columnCount = 0;
                for (Map.Entry<?, ?> column : rawRow.entrySet()) {
                    if (columnCount++ >= 100) {
                        break;
                    }
                    String columnName = stringValue(column.getKey());
                    if (columnName == null || columnName.isBlank()) {
                        continue;
                    }
                    Object value = column.getValue();
                    if (value instanceof Map<?, ?> || value instanceof List<?>) {
                        continue;
                    }
                    safeRow.put(limit(sanitize(columnName)), limit(sanitize(stringValue(value))));
                }
                safeRows.add(safeRow);
            }
            variables.put(key, safeRows);
        }
        return variables.isEmpty() ? Map.of() : Map.of("variables", variables);
    }

    private Map<String, Object> safeFileLookupResult(Map<String, Object> source) {
        Map<String, Object> result = safeNumberResult(source, "match_count", "matchCount");
        Object entries = source.getOrDefault("files", source.get("entries"));
        if (!(entries instanceof List<?> list)) {
            return result;
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (Object entry : list.stream().limit(100).toList()) {
            if (!(entry instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> file = readMap(entry);
            String name = stringValue(file.get("name"));
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
                continue;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("name", limit(sanitize(name)));
            Object size = file.get("size");
            if (size != null) {
                safe.put("size", numberValue(size, 0));
            }
            files.add(safe);
        }
        if (!files.isEmpty()) {
            result = new LinkedHashMap<>(result);
            result.put("files", files);
        }
        Map<String, Object> variableResult = safeDatabaseVariables(source.get("variables"));
        if (!variableResult.isEmpty()) {
            result = new LinkedHashMap<>(result);
            result.putAll(variableResult);
        }
        return result;
    }

    private Map<String, Object> safeNumberResult(Map<String, Object> source, String snakeKey, String camelKey) {
        Object value = source.getOrDefault(snakeKey, source.get(camelKey));
        return value == null ? Map.of() : Map.of(snakeKey, Math.max(0, numberValue(value, 0)));
    }

    private int numberValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !(value instanceof String text && text.isBlank())) {
                return value;
            }
        }
        return null;
    }

    /** file_ref 兼容字符串和表单对象；只把路径传给 Agent，文件内容或上传 token 不进入任务载荷。 */
    private String filePathValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return firstText(stringValue(map.get("path")), stringValue(map.get("file_path")), stringValue(map
                .get("value")));
        }
        return stringValue(value);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        return value == null ? null : numberValue(value, 0);
    }

    /**
     * 数据库类型是 admin 配置事实；步骤不得自带 driverClass、driverProfile 或本地 JAR 路径。
     * 23 种类型均映射到 Agent 受控驱动档案，实际 JAR 是否部署由 Agent 明确失败而非降级执行。
     */
    private DatabaseDriverSpec databaseDriverSpec(String type) {
        String key = type == null ? "" : type.trim().toLowerCase().replace(" ", "");
        return switch (key) {
            case "mysql" -> new DatabaseDriverSpec("mysql", "com.mysql.cj.jdbc.Driver");
            case "oracle" -> new DatabaseDriverSpec("oracle", "oracle.jdbc.OracleDriver");
            case "sqlserver" -> new DatabaseDriverSpec("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
            case "postgresql" -> new DatabaseDriverSpec("postgresql", "org.postgresql.Driver");
            case "greenplum" -> new DatabaseDriverSpec("greenplum", "org.postgresql.Driver");
            case "gaussdb" -> new DatabaseDriverSpec("gaussdb", "org.postgresql.Driver");
            case "sybase" -> new DatabaseDriverSpec("sybase", "com.sybase.jdbc4.jdbc.SybDriver");
            case "hive" -> new DatabaseDriverSpec("hive", "org.apache.hive.jdbc.HiveDriver");
            case "tidb" -> new DatabaseDriverSpec("tidb", "com.mysql.cj.jdbc.Driver");
            case "oceanbase" -> new DatabaseDriverSpec("oceanbase", "com.mysql.cj.jdbc.Driver");
            case "teradata" -> new DatabaseDriverSpec("teradata", "com.teradata.jdbc.TeraDriver");
            case "mariadb" -> new DatabaseDriverSpec("mariadb", "org.mariadb.jdbc.Driver");
            case "kingbase" -> new DatabaseDriverSpec("kingbase", "com.kingbase8.Driver");
            case "iris" -> new DatabaseDriverSpec("iris", "com.intersystems.jdbc.IRISDriver");
            case "informix" -> new DatabaseDriverSpec("informix", "com.informix.jdbc.IfxDriver");
            case "db2" -> new DatabaseDriverSpec("db2", "com.ibm.db2.jcc.DB2Driver");
            case "cache" -> new DatabaseDriverSpec("cache", "com.intersys.jdbc.CacheDriver");
            case "gbase8a" -> new DatabaseDriverSpec("gbase8a", "com.gbase.jdbc.Driver");
            case "gbase8s" -> new DatabaseDriverSpec("gbase8s", "com.gbasedbt.jdbc.Driver");
            case "tdengine" -> new DatabaseDriverSpec("tdengine", "com.taosdata.jdbc.rs.RestfulDriver");
            case "hbase", "phoenix" -> new DatabaseDriverSpec("phoenix", "org.apache.phoenix.jdbc.PhoenixDriver");
            case "dm" -> new DatabaseDriverSpec("dm", "dm.jdbc.driver.DmDriver");
            case "mongodb" -> new DatabaseDriverSpec("mongodb", "");
            default -> throw new BusinessException("不支持的数据库类型：" + type);
        };
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, MAP_TYPE);
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private Map<String, Object> parseObjectMap(Object value) {
        if (value == null || (value instanceof String string && string.isBlank())) {
            return Map.of();
        }
        try {
            return value instanceof String string ? objectMapper.readValue(string, MAP_TYPE) : readMap(value);
        } catch (Exception e) {
            throw new BusinessException("MongoDB filter/document 必须是 JSON 对象");
        }
    }

    private AutomationInfrastructureTaskDO requireTask(String taskId) {
        AutomationInfrastructureTaskDO task = taskMapper.selectOne(Wrappers
            .<AutomationInfrastructureTaskDO>lambdaQuery()
            .eq(AutomationInfrastructureTaskDO::getTaskId, taskId));
        if (task == null) {
            throw new BusinessException("基础设施任务不存在");
        }
        return task;
    }

    private AutomationInfrastructureTaskDO findByIdempotencyKey(String idempotencyKey) {
        return taskMapper.selectOne(Wrappers.<AutomationInfrastructureTaskDO>lambdaQuery()
            .eq(AutomationInfrastructureTaskDO::getIdempotencyKey, idempotencyKey));
    }

    private void requireTaskAccess(AutomationInfrastructureTaskDO task) {
        requireTaskAccess(task, null);
    }

    private void requireTaskAccess(AutomationInfrastructureTaskDO task, String executionCapability) {
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId != null && Objects.equals(task.getOwnerUserId(), currentUserId))
            return;
        if (matchesExecutionCapability(task, executionCapability))
            return;
        if (currentUserId == null) {
            throw new BusinessException("EXECUTION_SCOPE_DENIED：无法识别当前执行主体");
        }
        throw new BusinessException("EXECUTION_SCOPE_DENIED：当前主体无权访问该基础设施任务");
    }

    private boolean matchesExecutionCapability(AutomationInfrastructureTaskDO task, String executionCapability) {
        if (executionCapability == null || executionCapability.isBlank() || task.getExecutionId() == null)
            return false;
        Long executionId;
        try {
            executionId = Long.valueOf(task.getExecutionId());
        } catch (NumberFormatException e) {
            return false;
        }
        List<ExecutionContextRef> contexts = jdbcTemplate
            .query("SELECT execution_capability_digest, execution_capability_expires_at FROM automation_ui_execution WHERE id = ?", (rs,
                                                                                                                                     rowNum) -> new ExecutionContextRef(executionId, null, null, null, null, null, null, null, null, null, null, rs
                                                                                                                                         .getString("execution_capability_digest"), rs
                                                                                                                                             .getTimestamp("execution_capability_expires_at") == null
                                                                                                                                                 ? null
                                                                                                                                                 : rs.getTimestamp("execution_capability_expires_at")
                                                                                                                                                     .toLocalDateTime()), executionId);
        if (contexts.isEmpty())
            return false;
        ExecutionContextRef context = contexts.get(0);
        return AutomationExecutionCapability.matches(executionCapability, context.capabilityDigest(), context
            .capabilityExpiresAt());
    }

    private void requireMatchingPayload(AutomationInfrastructureTaskDO task, String payloadDigest) {
        if (!Objects.equals(task.getPayloadDigest(), payloadDigest)) {
            throw new BusinessException("TASK_PAYLOAD_DIGEST_MISMATCH：相同幂等键对应不同任务输入");
        }
    }

    private String taskPayloadDigest(ResolvedStep resolved,
                                     Long projectEnvironmentId,
                                     Map<String, Object> runtimeBindings,
                                     Map<String, Object> runtimeInput) {
        Map<String, Object> payloadIdentity = new LinkedHashMap<>();
        payloadIdentity.put("execution_id", resolved.context().executionId());
        payloadIdentity.put("step_execution_id", resolved.context().stepExecutionId());
        payloadIdentity.put("definition_revision_id", resolved.context().definitionRevisionId());
        payloadIdentity.put("action_type", resolved.actionType());
        payloadIdentity.put("raw_step", resolved.rawStep());
        payloadIdentity.put("project_environment_id", projectEnvironmentId);
        payloadIdentity.put("runtime_bindings", runtimeBindings == null ? Map.of() : runtimeBindings);
        payloadIdentity.put("runtime_input", runtimeInput == null ? Map.of() : runtimeInput);
        try {
            ObjectMapper canonicalMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return DigestUtil.sha256Hex(canonicalMapper.writeValueAsString(payloadIdentity));
        } catch (Exception e) {
            throw new BusinessException("无法计算基础设施任务 payload 摘要");
        }
    }

    private String approvalDigest(ExecutionContextRef context,
                                  Long ownerUserId,
                                  Long projectEnvironmentId,
                                  String payloadDigest,
                                  Assessment risk,
                                  LocalDateTime approvalAt) {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("principal_id", ownerUserId);
        approval.put("project_id", context.projectId());
        approval.put("project_environment_id", projectEnvironmentId);
        approval.put("definition_revision_id", context.definitionRevisionId());
        approval.put("step_execution_id", context.stepExecutionId());
        approval.put("payload_digest", payloadDigest);
        approval.put("risk_level", risk.riskLevel());
        approval.put("command_template_id", risk.commandTemplateId());
        approval.put("approval_at", approvalAt == null ? null : approvalAt.toString());
        try {
            ObjectMapper canonicalMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return DigestUtil.sha256Hex(canonicalMapper.writeValueAsString(approval));
        } catch (Exception e) {
            throw new BusinessException("无法计算基础设施任务审批摘要");
        }
    }

    private void appendLog(String taskId, String level, String message) {
        AutomationInfrastructureTaskLogDO log = new AutomationInfrastructureTaskLogDO();
        log.setTaskId(taskId);
        Long last = taskLogMapper.selectList(Wrappers.<AutomationInfrastructureTaskLogDO>lambdaQuery()
            .eq(AutomationInfrastructureTaskLogDO::getTaskId, taskId)
            .orderByDesc(AutomationInfrastructureTaskLogDO::getSequence)
            .last("LIMIT 1")).stream().findFirst().map(AutomationInfrastructureTaskLogDO::getSequence).orElse(0L);
        log.setSequence(last + 1);
        log.setLevel(limit(level));
        log.setMessage(sanitize(message));
        // 任务日志同样继承 BaseDO，新增时显式写入 updateTime 以避免审计列非空约束失败。
        log.setUpdateTime(LocalDateTime.now());
        taskLogMapper.insert(log);
    }

    private List<AutomationInfrastructureTaskResp.Log> logsAfter(String taskId, Long afterSequence) {
        List<AutomationInfrastructureTaskLogDO> entities = taskLogMapper.selectList(Wrappers
            .<AutomationInfrastructureTaskLogDO>lambdaQuery()
            .eq(AutomationInfrastructureTaskLogDO::getTaskId, taskId)
            .gt(afterSequence != null, AutomationInfrastructureTaskLogDO::getSequence, afterSequence)
            .orderByAsc(AutomationInfrastructureTaskLogDO::getSequence));
        List<AutomationInfrastructureTaskResp.Log> result = new ArrayList<>();
        for (AutomationInfrastructureTaskLogDO entity : entities) {
            AutomationInfrastructureTaskResp.Log log = new AutomationInfrastructureTaskResp.Log();
            log.setSequence(entity.getSequence());
            log.setLevel(entity.getLevel());
            log.setMessage(entity.getMessage());
            result.add(log);
        }
        return result;
    }

    private AutomationInfrastructureTaskResp toResp(AutomationInfrastructureTaskDO task,
                                                    List<AutomationInfrastructureTaskResp.Log> logs,
                                                    Map<String, Object> result) {
        AutomationInfrastructureTaskResp resp = new AutomationInfrastructureTaskResp();
        resp.setTaskId(task.getTaskId());
        resp.setNextSequence(logs == null || logs.isEmpty() ? 0L : logs.get(logs.size() - 1).getSequence());
        resp.setCaseKey(task.getCaseKey());
        resp.setStepId(task.getStepId());
        resp.setActionType(task.getActionType());
        resp.setStatus(task.getStatus());
        resp.setExecutor(task.getExecutorNode());
        resp.setExitCode(task.getExitCode());
        resp.setAffectedRows(task.getAffectedRows());
        resp.setErrorCode(task.getErrorCode());
        resp.setErrorMessage(task.getErrorMessage());
        resp.setResultSummary(task.getResultSummary());
        Map<String, Object> responseResult = new LinkedHashMap<>(result == null ? Map.of() : result);
        if (!responseResult.containsKey("infrastructure") && task.getResultJson() != null) {
            try {
                responseResult.put("infrastructure", objectMapper.readValue(task.getResultJson(), MAP_TYPE));
            } catch (Exception ignored) {
                // 历史结果损坏时仍返回任务状态，不能阻断执行历史页面。
            }
        }
        resp.setResult(responseResult);
        resp.setStartedAt(format(task.getStartedAt()));
        resp.setFinishedAt(format(task.getFinishedAt()));
        resp.setCancelRequested(task.getCancelRequestedAt() != null);
        resp.setDisposition(task.getDisposition());
        resp.setDispositionAt(format(task.getDispositionAt()));
        resp.setLogs(logs == null ? List.of() : logs);
        return resp;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return limit(value.replaceAll("(?i)(password|passwd|token|secret)\\s*([=:])\\s*[^\\s,;]+", "$1$2***"));
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_LENGTH) + "...[truncated]";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long configIdValue(Object value) {
        String valueAsString = stringValue(value);
        if (valueAsString == null || valueAsString.isBlank()) {
            return null;
        }
        try {
            long configId = Long.parseLong(valueAsString);
            if (configId <= 0) {
                throw new NumberFormatException();
            }
            return configId;
        } catch (NumberFormatException e) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：target_ref.config_id 必须为正数配置 ID");
        }
    }

    private String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private boolean isTerminal(String status) {
        return List.of("passed", "failed", "cancelled", "unknown_outcome").contains(status);
    }

    private record ResolvedStep(ExecutionContextRef context, String actionType, String targetKind, Long configId,
                                String bindingKey, Map<String, Object> rawStep) {
        private ResolvedStep withRawStep(Map<String, Object> newRawStep) {
            return new ResolvedStep(context, actionType, targetKind, configId, bindingKey, newRawStep);
        }
    }

    private record ExecutionContextRef(Long executionId, Long definitionRevisionId, Long ownerUserId,
                                       Long projectEnvironmentId, Long sceneId, Long projectId, Long definitionVersion,
                                       String definitionJson, Long caseExecutionId, String caseId, Long stepExecutionId,
                                       String capabilityDigest, LocalDateTime capabilityExpiresAt) {
    }

    private record ResolvedTarget(Long configId, Map<String, Object> config) {
    }

    private record DatabaseDriverSpec(String profile, String driverClass) {
    }
}
