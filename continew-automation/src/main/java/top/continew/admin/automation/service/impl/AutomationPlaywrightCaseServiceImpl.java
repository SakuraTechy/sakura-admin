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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.AutomationPlaybackUrlRewriter;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.model.entity.AutomationPlaywrightJobDO;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCaseStatusReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlanReportProgressService;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.support.AutomationStoragePressureGuard;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * Playwright Runner admin 数据服务实现。
 *
 * @author Codex
 */
@Service
@RequiredArgsConstructor
public class AutomationPlaywrightCaseServiceImpl implements AutomationPlaywrightCaseService {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PLATFORM_DATE_TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter EXECUTION_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong EXECUTION_ID_SEQUENCE_SECONDS = new AtomicLong();
    private static final List<String> TERMINAL_CASE_STATUSES = List
        .of("passed", "failed", "cancelled", "blocked", "skipped");

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final AutomationPlaywrightStepExtractor stepExtractor;
    private final ProjectEnvironmentConfigMapper projectEnvironmentConfigMapper;
    private final ProjectConfigMapper projectConfigMapper;
    private final AutomationPlaybackUrlRewriter playbackUrlRewriter;
    private final List<AutomationPlanReportProgressService> planReportProgressServices;
    private final AutomationPlaywrightSessionStateService sessionStateService;
    private final AutomationUiExecutionRecordService executionRecordService;

    @Resource
    private AutomationStoragePressureGuard storagePressureGuard;

    @Resource
    private AutomationPlaywrightJobMapper automationPlaywrightJobMapper;

    @Override
    public AutomationPlaywrightCaseResp getCase(String caseKey) {
        return getCase(caseKey, null);
    }

    @Override
    public AutomationPlaywrightCaseResp getCase(String caseKey, Long projectEnvironmentId) {
        ResolvedCase resolved = resolveCase(caseKey);
        CaseDO caseDO = resolved.caseDO();
        AutomationPlaywrightCaseResp resp = new AutomationPlaywrightCaseResp();
        resp.setId(caseKey);
        resp.setSceneDbId(resolved.scene().getId());
        resp.setSceneId(resolved.scene().getSceneId());
        resp.setSceneName(resolved.scene().getName());
        resp.setScene_name(resolved.scene().getName());
        resp.setCaseId(caseDO.getId());
        resp.setName(caseDO.getName());
        fillArtifactPathMetadata(resp, resolved.scene());

        List<Map<String, Object>> steps = new ArrayList<>();
        List<StepDOAdapter> adapters = stepAdapters(caseDO);
        for (int i = 0; i < adapters.size(); i++) {
            steps.add(stepExtractor.extract(adapters.get(i).step(), i));
        }
        resp.setSteps(steps);
        fillCaseRuntimeFields(resp, steps);
        if (projectEnvironmentId != null) {
            applyProjectEnvironment(resp, resolved.scene(), projectEnvironmentId);
        }
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationPlaywrightBatchResp createBatch(AutomationPlaywrightBatchCreateReq req) {
        if (storagePressureGuard != null) {
            storagePressureGuard.assertExecutionAllowed();
        }
        AutomationUiSceneDO scene = resolveScene(req.getSceneKey());
        String executionType = StringUtils.trimToEmpty(req.getExecutionType()).toLowerCase();
        if (!List.of("playwright-runner", "extension-cdp").contains(executionType)) {
            throw new BusinessException("不支持的 Playwright 执行方式：" + executionType);
        }
        String testPlanId = StringUtils.trimToEmpty(req.getTestPlanId());
        String testReportId = StringUtils.trimToEmpty(req.getTestReportId());
        if (StringUtils.isNotBlank(testReportId) && StringUtils.isBlank(testPlanId)) {
            throw new BusinessException("正式报告批次必须同时提供 testPlanId");
        }
        if (StringUtils.isNotBlank(testPlanId) && !containsId(scene.getTestPlanId(), testPlanId)) {
            throw new BusinessException("测试计划未关联当前场景，testPlanId=" + testPlanId);
        }
        if (StringUtils.isNotBlank(testReportId)) {
            validatePlanReportBinding(testPlanId, testReportId, req.getSceneKey(), executionType);
        }
        ProjectEnvironmentConfigDO environment = projectEnvironmentConfigMapper.selectById(req
            .getProjectEnvironmentId());
        if (environment == null) {
            throw new BusinessException("批次产品环境不存在，projectEnvironmentId=" + req.getProjectEnvironmentId());
        }
        if (!Objects.equals(scene.getProjectId(), environment.getProjectId())) {
            throw new BusinessException("批次产品环境与场景所属项目不一致，projectEnvironmentId=" + req.getProjectEnvironmentId());
        }
        if (!DisEnableStatusEnum.ENABLE.equals(environment.getStatus())) {
            throw new BusinessException("批次产品环境未启用，projectEnvironmentId=" + req.getProjectEnvironmentId());
        }
        String batchId = nextExecutionId();
        String startedAt = now();
        String username = StringUtils.defaultString(UserContextHolder.getUsername(), "-");
        String executeName = StringUtils.firstNonBlank(UserContextHolder.getNickname(), "-".equals(username)
            ? null
            : username, req.getExecuteName(), "-");

        List<Object> caseResults = new ArrayList<>();
        List<AutomationPlaywrightBatchResp.CaseExecution> responseCases = new ArrayList<>();
        for (String caseId : req.getCaseIds()) {
            CaseDO caseDO = findCase(scene, caseId);
            if (caseDO == null) {
                throw new BusinessException("批次目标用例不存在，caseId=" + caseId);
            }
            String executionId = nextExecutionId();
            int stepTotal = stepAdapters(caseDO).size();
            Map<String, Object> caseResult = new LinkedHashMap<>();
            caseResult.put("case_key", req.getSceneKey() + ":" + caseId);
            caseResult.put("case_id", caseId);
            caseResult.put("case_name", caseDO.getName());
            caseResult.put("execution_id", executionId);
            caseResult.put("status", "queued");
            applyCaseExecutionState(caseResult);
            caseResult.put("step_total", stepTotal);
            caseResult.put("step_pass", 0);
            caseResult.put("step_fail", 0);
            caseResult.put("step_skip", 0);
            caseResult.put("steps", new ArrayList<>());
            caseResults.add(caseResult);

            AutomationPlaywrightBatchResp.CaseExecution responseCase = new AutomationPlaywrightBatchResp.CaseExecution();
            responseCase.setCaseId(caseId);
            responseCase.setCaseName(caseDO.getName());
            responseCase.setExecutionId(executionId);
            responseCase.setStatus("queued");
            responseCase.setStepTotal(stepTotal);
            responseCases.add(responseCase);
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordType", "playwright-batch");
        record.put("batchId", batchId);
        record.put("executionId", batchId);
        record.put("executionType", executionType);
        record.put("executor", executionType);
        record.put("executeUserId", UserContextHolder.getUserId());
        record.put("executeUsername", username);
        record.put("executeName", executeName);
        record.put("executeEmail", StringUtils.defaultIfBlank(req.getExecuteEmail(), "-"));
        record.put("startedAt", startedAt);
        record.put("executeStatus", "running");
        record.put("executeResult", "pending");
        record.put("duration", 0);
        record.put("projectEnvironmentId", req.getProjectEnvironmentId());
        record.put("projectEnvironmentName", environment.getName());
        record.put("executionConfig", req.getExecutionConfig() == null ? Map.of() : req.getExecutionConfig());
        record.put("caseResults", caseResults);
        if (StringUtils.isNotBlank(testPlanId)) {
            // 测试计划执行必须与调试记录隔离，避免计划历史混入场景详情的 debugRecord。
            record.put("testPlanId", testPlanId);
        }
        if (StringUtils.isNotBlank(testReportId)) {
            record.put("testReportId", testReportId);
        }
        recomputeBatch(record, false);

        applyBatchSummaryToScene(scene, record);
        executionRecordService.saveRecord(scene, record, null);
        notifyPlanReportProgress(record);

        AutomationPlaywrightBatchResp response = new AutomationPlaywrightBatchResp();
        response.setBatchId(batchId);
        response.setExecutionType(executionType);
        response.setExecuteName(executeName);
        response.setExecuteEmail(StringUtils.defaultIfBlank(req.getExecuteEmail(), "-"));
        response.setStartedAt(startedAt);
        response.setCases(responseCases);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatchCaseStatus(String sceneKey,
                                      String batchId,
                                      String caseId,
                                      AutomationPlaywrightBatchCaseStatusReq req) {
        AutomationUiSceneDO scene = resolveScene(sceneKey);
        Map<String, Object> batch = requireBatch(scene, batchId);
        Map<String, Object> caseResult = requireBatchCase(batch, caseId);
        String status = StringUtils.trimToEmpty(req.getStatus()).toLowerCase();
        if (!List.of("starting", "queued", "running", "failed", "cancelled").contains(status)) {
            throw new BusinessException("不支持的批次用例状态：" + status);
        }
        String currentStatus = stringValue(caseResult.get("status"));
        // Runner 可能先回传通过明细、再在认证候选状态提交时失败；此时 Job 失败必须覆盖“通过”摘要。
        boolean allowPassedToFailed = "passed".equals(currentStatus) && "failed".equals(status);
        if (Boolean.TRUE.equals(caseResult.get("result_detailed")) && !allowPassedToFailed) {
            return;
        }
        if (TERMINAL_CASE_STATUSES.contains(currentStatus) && !allowPassedToFailed) {
            return;
        }
        caseResult.put("status", status);
        applyCaseExecutionState(caseResult);
        putIfNotBlank(caseResult, "job_id", req.getJobId());
        putIfNotBlank(caseResult, "started_at", normalizeExecutionDateTime(req.getStartedAt()));
        putIfNotBlank(caseResult, "finished_at", normalizeExecutionDateTime(req.getFinishedAt()));
        putIfNotBlank(caseResult, "error", req.getError());
        if (req.getDurationMs() != null) {
            caseResult.put("duration_ms", Math.max(0, req.getDurationMs()));
        }
        if (TERMINAL_CASE_STATUSES.contains(status) && StringUtils.isBlank(stringValue(caseResult
            .get("finished_at")))) {
            caseResult.put("finished_at", now());
        }
        recomputeBatch(batch, false);
        applyBatchSummaryToScene(scene, batch);
        executionRecordService.saveRecord(scene, batch, caseId);
        notifyPlanReportProgress(batch);
        if (isTerminalBatch(batch)) {
            sessionStateService.cleanupBatch(batchId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBatch(String sceneKey, String batchId) {
        AutomationUiSceneDO scene = resolveScene(sceneKey);
        Map<String, Object> batch = requireBatch(scene, batchId);
        for (Object item : listValue(batch.get("caseResults"))) {
            Map<String, Object> caseResult = asObjectMap(item);
            if (TERMINAL_CASE_STATUSES.contains(stringValue(caseResult.get("status")))) {
                continue;
            }
            caseResult.put("status", "cancelled");
            applyCaseExecutionState(caseResult);
            caseResult.put("finished_at", now());
            caseResult.put("error", "批次已取消");
            markUnexecutedStepsSkipped(scene, stringValue(caseResult.get("case_id")), caseResult);
            replaceListItem(batch, "caseResults", item, caseResult);
        }
        batch.put("cancelRequested", true);
        recomputeBatch(batch, true);
        applyBatchSummaryToScene(scene, batch);
        executionRecordService.saveRecord(scene, batch, null);
        notifyPlanReportProgress(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCase(String sceneKey, String batchId, String caseId) {
        AutomationUiSceneDO scene = resolveScene(sceneKey);
        Map<String, Object> batch = requireBatch(scene, batchId);
        Map<String, Object> caseResult = requireBatchCase(batch, caseId);
        if (TERMINAL_CASE_STATUSES.contains(stringValue(caseResult.get("status")))) {
            return;
        }
        // 单用例取消也必须先写入终态；Runner/CDP 的迟到结果只能补充诊断，不能恢复执行结果。
        caseResult.put("caseCancelRequested", true);
        caseResult.put("status", "cancelled");
        applyCaseExecutionState(caseResult);
        caseResult.put("finished_at", now());
        caseResult.put("error", "用例已取消");
        markUnexecutedStepsSkipped(scene, caseId, caseResult);
        recomputeBatch(batch, false);
        applyBatchSummaryToScene(scene, batch);
        executionRecordService.saveRecord(scene, batch, caseId);
        notifyPlanReportProgress(batch);
        if (isTerminalBatch(batch)) {
            sessionStateService.cleanupBatch(batchId);
        }
    }

    @Override
    public AutomationPlaywrightCaseCancellationResp getCaseCancellation(String sceneKey,
                                                                        String batchId,
                                                                        String caseId) {
        AutomationUiSceneDO scene = resolveScene(sceneKey);
        Map<String, Object> batch = requireBatch(scene, batchId);
        Map<String, Object> caseResult = requireBatchCase(batch, caseId);
        AutomationPlaywrightCaseCancellationResp response = new AutomationPlaywrightCaseCancellationResp();
        response.setBatchCancelRequested(Boolean.TRUE.equals(batch.get("cancelRequested")));
        response.setCaseCancelRequested(Boolean.TRUE.equals(caseResult.get("caseCancelRequested")));
        return response;
    }

    @Override
    public boolean isBatchTerminal(String sceneKey, String batchId) {
        AutomationUiSceneDO scene = resolveScene(sceneKey);
        return isTerminalBatch(requireBatch(scene, batchId));
    }

    @Override
    public void validateReusableBatchCase(String sceneKey, String batchId, String caseId, Long projectEnvironmentId) {
        AutomationUiSceneDO scene = resolveScene(sceneKey);
        Map<String, Object> batch = requireBatch(scene, batchId);
        if (Boolean.TRUE.equals(batch.get("cancelRequested"))) {
            throw new BusinessException("Playwright Runner 批次已取消，不能复用登录态");
        }
        if (!Objects.equals(toLong(batch.get("projectEnvironmentId")), projectEnvironmentId.longValue())) {
            throw new BusinessException("Playwright Runner 批次产品环境不匹配");
        }
        Map<String, Object> caseResult = requireBatchCase(batch, caseId);
        if (TERMINAL_CASE_STATUSES.contains(stringValue(caseResult.get("status")))) {
            throw new BusinessException("Playwright Runner 批次用例已结束，不能重复执行");
        }
        Long ownerUserId = nullableLong(batch.get("executeUserId"));
        Long currentUserId = UserContextHolder.getUserId();
        // 交互请求必须与批次创建账号一致；后台计划没有用户上下文，只能由服务端专用令牌进入。
        if (ownerUserId != null && currentUserId != null && !ownerUserId.equals(currentUserId)) {
            throw new BusinessException("Playwright Runner 批次不属于当前执行账号");
        }
    }

    /**
     * Runner 仅使用这些业务标识生成本地目录，不把节点路径写回场景主数据。
     */
    private void fillArtifactPathMetadata(AutomationPlaywrightCaseResp resp, AutomationUiSceneDO scene) {
        ProjectConfigDO project = scene.getProjectId() == null
            ? null
            : projectConfigMapper.selectById(scene.getProjectId());
        String projectShortName = project == null
            ? StringUtils.firstNonBlank(scene.getProjectName(), "project")
            : StringUtils.firstNonBlank(project.getAbbreviate(), project.getName(), scene.getProjectName(), "project");
        String versionName = StringUtils.firstNonBlank(scene.getVersionName(), "version");
        resp.setProjectShortName(projectShortName);
        resp.setProject_short_name(projectShortName);
        resp.setVersionName(versionName);
        resp.setVersion_name(versionName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(String caseKey, AutomationPlaywrightResultReq req) {
        ResolvedCase resolved = resolveCase(caseKey);
        AutomationUiSceneDO scene = resolved.scene();
        Map<String, Object> record = new LinkedHashMap<>();
        Map<String, Object> rawResult = sanitizeExecutionResult(normalizeExecutionTimes(asObjectMap(req.getRaw())));
        String batchId = stringValue(rawResult.get("batch_id"));
        Map<String, Object> batchRecord = findBatch(scene, batchId);
        Map<String, Object> caseResult = asObjectMap(rawResult.get("case_result"));
        List<Object> rawStepResults = readStepResults(rawResult, caseResult);
        List<Object> stepResults = enrichStepResults(resolved.caseDO(), rawStepResults);
        boolean passed = Boolean.TRUE.equals(req.getSuccess());
        int stepTotal = stepResults.size();
        int stepPass = countStepStatus(stepResults, "passed");
        int stepFail = countStepStatus(stepResults, "failed");
        int stepSkip = countStepStatus(stepResults, "skipped");
        if (rawStepResults.isEmpty()) {
            // 兼容旧 Runner 结果：旧协议只有步骤总数，没有逐步明细。
            Map<String, Object> detail = asObjectMap(rawResult.get("detail"));
            stepTotal = toInt(detail.get("steps"));
            if (stepTotal <= 0) {
                stepTotal = stepResults.size();
            }
            stepPass = passed ? stepTotal : 0;
            stepFail = passed ? 0 : (stepTotal > 0 ? 1 : 0);
            stepSkip = passed ? 0 : Math.max(0, stepTotal - stepFail);
        }
        String executor = String.valueOf(rawResult.getOrDefault("executor", "playwright-runner"));
        String executionType = "extension-cdp".equalsIgnoreCase(executor) ? "extension-cdp" : "playwright-runner";
        String executionId = stringValue(rawResult.get("run_id"));
        if (executionId.isBlank() && batchRecord != null) {
            executionId = stringValue(requireBatchCase(batchRecord, resolved.caseDO().getId()).get("execution_id"));
            rawResult.put("run_id", executionId);
        }
        if (executionId.isBlank()) {
            executionId = nextExecutionId();
            rawResult.put("run_id", executionId);
        }
        String startedAt = normalizeExecutionDateTime(stringValue(rawResult.get("started_at")));
        String finishedAt = normalizeExecutionDateTime(stringValue(rawResult.get("finished_at")));
        if (StringUtils.isNotBlank(startedAt)) {
            rawResult.put("started_at", startedAt);
        }
        if (StringUtils.isNotBlank(finishedAt)) {
            rawResult.put("finished_at", finishedAt);
        }
        Long detailDurationMs = calculateStepDuration(rawStepResults);
        Long stepDurationMs = detailDurationMs == null
            ? parseDuration(rawResult.get("step_duration_ms"))
            : detailDurationMs;
        long wallClockDurationMs = req.getDurationMs() == null
            ? durationBetween(startedAt, finishedAt)
            : Math.max(0, req.getDurationMs());
        long durationMs = wallClockDurationMs;
        String resultCode = passed
            ? AutomationUiSceneStatusCodes.RESULT_PASSED
            : AutomationUiSceneStatusCodes.RESULT_FAILED;
        String caseId = resolved.caseDO().getId();
        String caseName = resolved.caseDO().getName();
        caseResult.putIfAbsent("case_key", caseKey);
        caseResult.putIfAbsent("case_id", caseId);
        caseResult.putIfAbsent("case_name", caseName);
        caseResult.putIfAbsent("status", passed ? "passed" : "failed");
        caseResult.put("execution_id", executionId);
        caseResult.put("steps", stepResults);
        caseResult.put("step_total", stepTotal);
        caseResult.put("step_pass", stepPass);
        caseResult.put("step_fail", stepFail);
        caseResult.put("step_skip", stepSkip);
        caseResult.put("step_pass_rate", formatRate(stepPass, stepTotal));
        // 用例耗时统一表示从执行开始到结束的端到端墙钟耗时；步骤耗时合计单独保存用于诊断。
        caseResult.put("duration_ms", durationMs);
        caseResult.put("step_duration_ms", stepDurationMs);
        caseResult.put("wall_clock_duration_ms", wallClockDurationMs);
        caseResult.put("started_at", startedAt);
        caseResult.put("finished_at", finishedAt);
        caseResult.put("error", StringUtils.firstNonBlank(req.getError(), stringValue(caseResult.get("error")), ""));
        // 结果摘要已经单独保存 artifact 映射；嵌套原始结果不再重复保存同一组路径。
        Map<String, Object> persistedPlaywrightResult = persistedPlaywrightResult(rawResult);
        caseResult.put("playwright_result", persistedPlaywrightResult);
        caseResult.put("artifact_urls", rawResult.get("artifacts"));
        caseResult.put("artifact_file_ids", rawResult.get("artifact_file_ids"));
        caseResult.put("artifact_upload_errors", rawResult.get("artifact_upload_errors"));
        updatePersistedJobArtifacts(rawResult);
        caseResult.put("result_detailed", true);

        if (StringUtils.isNotBlank(batchId) && batchRecord != null) {
            mergeBatchCaseResult(batchRecord, caseId, caseResult);
            recomputeBatch(batchRecord, Boolean.TRUE.equals(batchRecord.get("cancelRequested")));
            applyBatchSummaryToScene(scene, batchRecord);
            executionRecordService.saveRecord(scene, batchRecord, caseId);
            notifyPlanReportProgress(batchRecord);
            return;
        }

        // 旧客户端没有批次标识时仍保存为独立快照，不按时间猜测批次关系。
        String username = StringUtils.defaultString(UserContextHolder.getUsername(), "-");
        record.put("executeUserId", UserContextHolder.getUserId());
        record.put("executeUsername", username);
        record.put("executeName", StringUtils.firstNonBlank(UserContextHolder.getNickname(), username, "-"));
        record.put("executor", executor);
        record.put("executionType", executionType);
        record.put("executionId", executionId);
        record.put("startedAt", startedAt);
        record.put("finishedAt", finishedAt);
        record.put("executeStatus", "completed");
        record.put("executeResult", resultCode);
        record.put("duration", durationMs);
        record.put("stepDuration", stepDurationMs);
        record.put("wallClockDuration", wallClockDurationMs);
        record.put("playwrightCaseKey", caseKey);
        record.put("playwrightStatus", req.getStatus());
        record.put("playwrightError", req.getError());
        // 入库前统一执行时间，避免不同 Runner/CDP 版本把 UTC ISO 与平台时间混存。
        record.put("playwrightResult", persistedPlaywrightResult);
        record.put("caseId", caseId);
        record.put("caseName", caseName);
        record.put("caseTotal", 1);
        record.put("casePass", passed ? 1 : 0);
        record.put("caseFail", passed ? 0 : 1);
        record.put("caseSkip", 0);
        record.put("casePassRate", passed ? "100%" : "0%");
        // 场景列表读取 scenePassRate；单用例回放的场景通过率就是本次用例通过率。
        record.put("scenePassRate", passed ? "100%" : "0%");
        record.put("stepTotal", stepTotal);
        record.put("stepPass", stepPass);
        record.put("stepFail", stepFail);
        record.put("stepSkip", stepSkip);
        record.put("stepPassRate", formatRate(stepPass, stepTotal));
        record.put("caseResults", List.of(caseResult));
        record.put("stepResults", stepResults);
        if (!rawResult.isEmpty()) {
            Object artifacts = rawResult.get("artifacts");
            record.put("playwrightArtifacts", artifacts);
            record.put("artifactUrls", artifacts);
            record.put("artifactUploadErrors", rawResult.get("artifact_upload_errors"));
        }
        scene.setExecuteStatus(AutomationUiSceneStatusCodes.STATUS_COMPLETED);
        scene.setExecuteResult(resultCode);
        scene.setLastResult(resultCode);
        scene.setCaseTotal(1);
        scene.setCasePass(passed ? 1 : 0);
        scene.setCaseFail(passed ? 0 : 1);
        scene.setCaseSkip(0);
        scene.setPassRate(passed ? "100%" : "0%");
        scene.setStepTotal(stepTotal);
        scene.setStepPass(stepPass);
        scene.setStepFail(stepFail);
        scene.setStepSkip(stepSkip);
        executionRecordService.saveRecord(scene, record, null);
    }

    private Map<String, Object> persistedPlaywrightResult(Map<String, Object> rawResult) {
        Map<String, Object> persisted = new LinkedHashMap<>(rawResult);
        // artifact 已提升为用例级索引，嵌套原始结果不再重复保存同一组 URL 和文件 ID。
        persisted.remove("artifacts");
        persisted.remove("artifact_file_ids");
        persisted.remove("artifact_upload_errors");
        persisted.remove("steps");
        return persisted;
    }

    private List<Object> enrichStepResults(CaseDO caseDO, List<Object> rawResults) {
        List<Map<String, Object>> pendingResults = new ArrayList<>();
        for (Object item : rawResults) {
            pendingResults.add(asObjectMap(item));
        }

        List<Object> enriched = new ArrayList<>();
        List<StepDOAdapter> adapters = stepAdapters(caseDO);
        for (int index = 0; index < adapters.size(); index++) {
            StepDO sourceStep = adapters.get(index).step();
            Map<String, Object> source = stepExtractor.extract(sourceStep, index);
            String stepId = StringUtils.firstNonBlank(stringValue(source.get("id")), sourceStep.getId(), "");
            int sourceIndex = source.containsKey("step_index") ? toInt(source.get("step_index")) : index;
            // step_index 是 Runner 的执行序号，优先级高于可能重复的业务 step_id。
            Map<String, Object> result = takeResultByIndex(pendingResults, sourceIndex);
            result = result == null ? takeResultById(pendingResults, stepId) : result;
            Map<String, Object> step = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
            String executionStepId = StringUtils.firstNonBlank(stringValue(step.get("step_id")), stringValue(step
                .get("stepId")), stepId);
            step.put("step_id", executionStepId);
            if (!executionStepId.equals(stepId)) {
                step.put("source_step_id", stepId);
            }
            step.put("step_index", sourceIndex);
            step.put("step_name", StringUtils.firstNonBlank(sourceStep.getName(), stringValue(source
                .get("description")), "-"));
            step.put("description", StringUtils.firstNonBlank(stringValue(step.get("description")), stringValue(source
                .get("description")), sourceStep.getName(), "-"));
            step.put("action_type", StringUtils.firstNonBlank(stringValue(step.get("action_type")), stringValue(source
                .get("action_type")), "unknown"));
            step.putIfAbsent("status", "skipped");
            step.putIfAbsent("duration_ms", 0);
            copySourceField(step, source, "target_selector");
            copySourceField(step, source, "target_xpath");
            copySourceField(step, source, "locator_meta");
            copySourceField(step, source, "value_masked");
            copyActualLocatorField(step, "locator_source", "actual_locator_source");
            copyActualLocatorField(step, "locator_type", "actual_locator_type");
            copyActualLocatorField(step, "locator_value", "actual_locator_value");
            enriched.add(step);
        }
        for (Map<String, Object> remaining : pendingResults) {
            enriched.add(new LinkedHashMap<>(remaining));
        }
        return enriched;
    }

    private Map<String, Object> takeResultByIndex(List<Map<String, Object>> results, int stepIndex) {
        for (int index = 0; index < results.size(); index++) {
            Map<String, Object> result = results.get(index);
            Object rawIndex = result.getOrDefault("step_index", result.get("stepIndex"));
            if (rawIndex != null && StringUtils.isNotBlank(String.valueOf(rawIndex)) && toInt(rawIndex) == stepIndex) {
                return results.remove(index);
            }
        }
        return null;
    }

    private Map<String, Object> takeResultById(List<Map<String, Object>> results, String stepId) {
        if (StringUtils.isBlank(stepId)) {
            return null;
        }
        for (int index = 0; index < results.size(); index++) {
            Map<String, Object> result = results.get(index);
            String resultStepId = stringValue(result.getOrDefault("step_id", result.get("stepId")));
            if (stepId.equals(resultStepId)) {
                return results.remove(index);
            }
        }
        return null;
    }

    private void copySourceField(Map<String, Object> target, Map<String, Object> source, String key) {
        if (!target.containsKey(key) && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void copyActualLocatorField(Map<String, Object> step, String sourceKey, String targetKey) {
        String value = stringValue(step.get(sourceKey));
        if (StringUtils.isNotBlank(value)) {
            step.put(targetKey, value);
        }
    }

    private void mergeBatchCaseResult(Map<String, Object> batch, String caseId, Map<String, Object> detailedResult) {
        Map<String, Object> target = requireBatchCase(batch, caseId);
        String stableExecutionId = stringValue(target.get("execution_id"));
        String stableJobId = stringValue(target.get("job_id"));
        boolean caseCancelRequested = Boolean.TRUE.equals(target.get("caseCancelRequested"));
        boolean cancelled = "cancelled".equals(stringValue(target.get("status"))) && (Boolean.TRUE.equals(batch
            .get("cancelRequested")) || caseCancelRequested);
        target.clear();
        target.putAll(detailedResult);
        if (StringUtils.isBlank(stringValue(target.get("execution_id")))) {
            target.put("execution_id", stableExecutionId);
        }
        if (StringUtils.isNotBlank(stableJobId)) {
            // job_id 用于执行结束后读取结构化日志；详细结果合并时不能丢失该关联。
            target.put("job_id", stableJobId);
        }
        if (caseCancelRequested) {
            target.put("caseCancelRequested", true);
        }
        if (cancelled) {
            // 用户取消是批次/用例业务终态；迟到的引擎失败结果仅补充步骤和诊断，不能把取消改写为失败。
            target.put("status", "cancelled");
            target.put("error", Boolean.TRUE.equals(batch.get("cancelRequested")) ? "批次已取消" : "用例已取消");
        }
        applyCaseExecutionState(target);
    }

    private void markUnexecutedStepsSkipped(AutomationUiSceneDO scene, String caseId, Map<String, Object> caseResult) {
        CaseDO caseDO = findCase(scene, caseId);
        if (caseDO == null) {
            return;
        }
        List<Object> steps = new ArrayList<>();
        int stepIndex = 0;
        for (StepDOAdapter adapter : stepAdapters(caseDO)) {
            Map<String, Object> source = stepExtractor.extract(adapter.step(), stepIndex);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step_id", StringUtils.firstNonBlank(stringValue(source.get("id")), adapter.step().getId(), ""));
            step.put("step_index", stepIndex);
            step.put("step_name", StringUtils.firstNonBlank(adapter.step().getName(), stringValue(source
                .get("description")), "-"));
            step.put("status", "skipped");
            step.put("error", "用例已取消，未执行");
            steps.add(step);
            stepIndex++;
        }
        // 取消时只有尚未回传的步骤才会进入这里；明细到达后仍可补充已执行步骤的诊断。
        List<Object> existingSteps = listValue(caseResult.get("steps"));
        if (existingSteps.isEmpty()) {
            caseResult.put("steps", steps);
        }
        int total = stepAdapters(caseDO).size();
        caseResult.put("step_total", total);
        caseResult.put("step_skip", Math.max(toInt(caseResult.get("step_skip")), total - existingSteps.size()));
    }

    private Map<String, Object> requireBatch(AutomationUiSceneDO scene, String batchId) {
        Map<String, Object> batch = findBatch(scene, batchId);
        if (batch == null) {
            throw new BusinessException("执行批次不存在，batchId=" + batchId);
        }
        return batch;
    }

    private boolean isTerminalBatch(Map<String, Object> batch) {
        List<Object> results = listValue(batch.get("caseResults"));
        return !results.isEmpty() && results.stream()
            .map(this::asObjectMap)
            .allMatch(result -> TERMINAL_CASE_STATUSES.contains(stringValue(result.get("status"))));
    }

    private Map<String, Object> findBatch(AutomationUiSceneDO scene, String batchId) {
        if (StringUtils.isBlank(batchId)) {
            return null;
        }
        Map<String, Object> normalizedBatch = executionRecordService.findBatch(scene.getId(), batchId);
        if (normalizedBatch != null) {
            return normalizedBatch;
        }
        Map<String, Object> debugBatch = findBatch(scene.getDebugRecord(), batchId);
        return debugBatch != null ? debugBatch : findBatch(scene.getTestRecord(), batchId);
    }

    private Map<String, Object> findBatch(List<Object> records, String batchId) {
        if (records == null) {
            return null;
        }
        for (Object item : records) {
            Map<String, Object> record = mapReference(item);
            if (record != null && batchId.equals(stringValue(record.get("batchId")))) {
                return record;
            }
        }
        return null;
    }

    private boolean containsId(List<Object> ids, String expectedId) {
        return ids != null && ids.stream().anyMatch(item -> expectedId.equals(stringValue(item)));
    }

    private void validatePlanReportBinding(String testPlanId,
                                           String testReportId,
                                           String sceneKey,
                                           String executionType) {
        if (planReportProgressServices == null) {
            return;
        }
        planReportProgressServices.forEach(service -> service
            .validateBinding(testPlanId, testReportId, sceneKey, executionType));
    }

    private void notifyPlanReportProgress(Map<String, Object> record) {
        String testPlanId = stringValue(record.get("testPlanId"));
        String testReportId = stringValue(record.get("testReportId"));
        if (StringUtils.isBlank(testPlanId) || StringUtils
            .isBlank(testReportId) || planReportProgressServices == null) {
            return;
        }
        planReportProgressServices.forEach(service -> service.onProgressChanged(testPlanId, testReportId));
    }

    private Map<String, Object> sanitizeExecutionResult(Map<String, Object> rawResult) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        rawResult.forEach((key, value) -> {
            Object safeValue = sanitizeExecutionResultValue(key, value);
            if (safeValue != null) {
                sanitized.put(key, safeValue);
            }
        });
        return sanitized;
    }

    private Object sanitizeExecutionResultValue(String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((childKey, childValue) -> {
                String childName = String.valueOf(childKey);
                Object safeValue = sanitizeExecutionResultValue(childName, childValue);
                if (safeValue != null) {
                    sanitized.put(childName, safeValue);
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : list) {
                Object safeValue = sanitizeExecutionResultValue(key, item);
                if (safeValue != null) {
                    sanitized.add(safeValue);
                }
            }
            return sanitized;
        }
        if (!(value instanceof String text)) {
            return value;
        }
        String normalizedKey = StringUtils.defaultString(key).toLowerCase().replace('-', '_');
        if (normalizedKey.contains("base64") || text.regionMatches(true, 0, "data:image/", 0, 11)) {
            // 截图二进制必须先文件化并只保存 URL，禁止撑大场景 testRecord JSON。
            return null;
        }
        if (looksLikeRunnerLocalPath(text)) {
            // Runner 本地路径对报告查看端不可访问，上传失败时仅保留错误，不持久化该路径。
            return null;
        }
        return text;
    }

    private boolean looksLikeRunnerLocalPath(String value) {
        String text = StringUtils.trimToEmpty(value);
        String lower = text.toLowerCase();
        return text.matches("^[A-Za-z]:[\\\\/].+") || lower.startsWith("file:") || text.startsWith(".\\") || text
            .startsWith("./") || text.startsWith("..\\") || text.startsWith("../") || lower
                .startsWith("playwright-runner-artifacts/") || lower.startsWith("playwright-runner-artifacts\\");
    }

    private Map<String, Object> requireBatchCase(Map<String, Object> batch, String caseId) {
        for (Object item : listValue(batch.get("caseResults"))) {
            Map<String, Object> caseResult = mapReference(item);
            if (caseResult != null && caseId.equals(stringValue(caseResult.get("case_id")))) {
                return caseResult;
            }
        }
        throw new BusinessException("批次目标用例不存在，caseId=" + caseId);
    }

    private void recomputeBatch(Map<String, Object> batch, boolean forceCancelled) {
        List<Object> caseResults = listValue(batch.get("caseResults"));
        int total = caseResults.size();
        int completed = 0;
        int passed = 0;
        int failed = 0;
        int cancelled = 0;
        int blocked = 0;
        int skipped = 0;
        int stepTotal = 0;
        int stepPass = 0;
        int stepFail = 0;
        int stepSkip = 0;
        long durationMs = 0;
        long stepDurationMs = 0;
        for (Object item : caseResults) {
            Map<String, Object> result = mapReference(item);
            if (result == null) {
                result = asObjectMap(item);
            }
            applyCaseExecutionState(result);
            String status = stringValue(result.get("status")).toLowerCase();
            if (TERMINAL_CASE_STATUSES.contains(status))
                completed++;
            if ("passed".equals(status))
                passed++;
            if ("failed".equals(status))
                failed++;
            if ("cancelled".equals(status))
                cancelled++;
            if ("blocked".equals(status))
                blocked++;
            if ("skipped".equals(status))
                skipped++;
            stepTotal += toInt(result.get("step_total"));
            stepPass += toInt(result.get("step_pass"));
            stepFail += toInt(result.get("step_fail"));
            stepSkip += toInt(result.get("step_skip"));
            durationMs += Math.max(0, toLong(result.get("duration_ms")));
            stepDurationMs += Math.max(0, toLong(result.get("step_duration_ms")));
        }
        Long executionLogDurationMs = calculateExecutionLogDuration(caseResults);
        if (executionLogDurationMs != null) {
            durationMs = executionLogDurationMs;
        }
        batch.put("caseTotal", total);
        batch.put("caseCompleted", completed);
        batch.put("casePass", passed);
        batch.put("caseFail", failed);
        batch.put("caseCancelled", cancelled);
        batch.put("caseBlocked", blocked);
        batch.put("caseSkip", skipped);
        batch.put("casePassRate", formatRate(passed, total));
        batch.put("scenePassRate", formatRate(passed, total));
        batch.put("progress", total == 0 ? 0 : Math.round(completed * 10000.0 / total) / 100.0);
        batch.put("stepTotal", stepTotal);
        batch.put("stepPass", stepPass);
        batch.put("stepFail", stepFail);
        batch.put("stepSkip", stepSkip);
        batch.put("stepPassRate", formatRate(stepPass, stepTotal));
        // 批次耗时优先使用完整执行日志的首尾墙钟时间；日志不可用时回退到各用例端到端耗时之和。
        batch.put("duration", durationMs);
        batch.put("stepDuration", stepDurationMs);
        boolean terminal = total > 0 && completed >= total;
        if (!terminal && !forceCancelled) {
            batch.put("executeStatus", "running");
            batch.put("executeResult", "pending");
            return;
        }
        if (StringUtils.isBlank(stringValue(batch.get("finishedAt")))) {
            batch.put("finishedAt", now());
        }
        batch.put("wallClockDuration", durationBetween(stringValue(batch.get("startedAt")), stringValue(batch
            .get("finishedAt"))));
        boolean cancelledOutcome = forceCancelled || cancelled > 0;
        batch.put("executeStatus", cancelledOutcome ? "cancelled" : "completed");
        // 结果优先级与执行历史保持一致：取消 > 阻塞 > 失败 > 跳过 > 通过。
        batch.put("executeResult", cancelledOutcome
            ? "cancelled"
            : blocked > 0 ? "blocked" : failed > 0 ? "failed" : skipped > 0 ? "skipped" : "passed");
    }

    /**
     * Runner 的 status 是兼容字段，历史展示必须拆成生命周期状态与业务结果。
     */
    private void applyCaseExecutionState(Map<String, Object> caseResult) {
        String legacyStatus = stringValue(caseResult.get("status")).toLowerCase();
        switch (legacyStatus) {
            case "waiting", "queued" -> {
                caseResult.put("executeStatus", "queued");
                caseResult.put("executeResult", "not_executed");
            }
            case "starting", "running" -> {
                caseResult.put("executeStatus", legacyStatus);
                caseResult.put("executeResult", "pending");
            }
            case "cancelled" -> {
                caseResult.put("executeStatus", "cancelled");
                caseResult.put("executeResult", "cancelled");
            }
            case "passed", "failed", "blocked", "skipped" -> {
                caseResult.put("executeStatus", "completed");
                caseResult.put("executeResult", legacyStatus);
            }
            default -> {
                caseResult.put("executeStatus", "queued");
                caseResult.put("executeResult", "not_executed");
            }
        }
    }

    private Long calculateExecutionLogDuration(List<Object> caseResults) {
        LocalDateTime firstTimestamp = null;
        LocalDateTime lastTimestamp = null;
        for (Object item : caseResults) {
            Map<String, Object> caseResult = asObjectMap(item);
            Map<String, Object> playwrightResult = asObjectMap(caseResult.get("playwright_result"));
            for (Object logItem : listValue(playwrightResult.get("execution_logs"))) {
                String timestamp = normalizeExecutionDateTime(stringValue(asObjectMap(logItem).get("timestamp")));
                try {
                    LocalDateTime parsed = LocalDateTime.parse(timestamp, PLATFORM_DATE_TIME_FORMATTER);
                    if (firstTimestamp == null || parsed.isBefore(firstTimestamp))
                        firstTimestamp = parsed;
                    if (lastTimestamp == null || parsed.isAfter(lastTimestamp))
                        lastTimestamp = parsed;
                } catch (DateTimeParseException ignored) {
                    // 单条日志时间异常时忽略该条，批次仍可回退到用例耗时累加。
                }
            }
        }
        if (firstTimestamp == null || lastTimestamp == null)
            return null;
        return Math.max(0, java.time.Duration.between(firstTimestamp, lastTimestamp).toMillis());
    }

    private void applyBatchSummaryToScene(AutomationUiSceneDO scene, Map<String, Object> batch) {
        boolean running = "running".equals(stringValue(batch.get("executeStatus")));
        String result = stringValue(batch.get("executeResult"));
        String resultCode = running
            ? AutomationUiSceneStatusCodes.RESULT_NOT_EXECUTED
            : "passed".equals(result)
                ? AutomationUiSceneStatusCodes.RESULT_PASSED
                : "cancelled".equals(result)
                    ? AutomationUiSceneStatusCodes.RESULT_CANCELLED
                    : "skipped".equals(result)
                        ? AutomationUiSceneStatusCodes.RESULT_SKIPPED
                        : AutomationUiSceneStatusCodes.RESULT_FAILED;
        scene.setExecuteStatus(running
            ? AutomationUiSceneStatusCodes.STATUS_RUNNING
            : "cancelled".equals(result)
                ? AutomationUiSceneStatusCodes.STATUS_CANCELLED
                : AutomationUiSceneStatusCodes.STATUS_COMPLETED);
        scene.setExecuteResult(resultCode);
        if (!running) {
            scene.setLastResult(resultCode);
        }
        scene.setCaseTotal(toInt(batch.get("caseTotal")));
        scene.setCasePass(toInt(batch.get("casePass")));
        scene.setCaseFail(toInt(batch.get("caseFail")));
        scene.setCaseSkip(toInt(batch.get("caseSkip")));
        scene.setPassRate(stringValue(batch.get("casePassRate")));
        scene.setStepTotal(toInt(batch.get("stepTotal")));
        scene.setStepPass(toInt(batch.get("stepPass")));
        scene.setStepFail(toInt(batch.get("stepFail")));
        scene.setStepSkip(toInt(batch.get("stepSkip")));
    }

    private long durationBetween(String startedAt, String finishedAt) {
        if (StringUtils.isBlank(startedAt) || StringUtils.isBlank(finishedAt)) {
            return 0;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startedAt, PLATFORM_DATE_TIME_FORMATTER);
            LocalDateTime finish = LocalDateTime.parse(finishedAt, PLATFORM_DATE_TIME_FORMATTER);
            return Math.max(0, java.time.Duration.between(start, finish).toMillis());
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private String now() {
        return LocalDateTime.now(PLATFORM_ZONE_ID).format(PLATFORM_DATE_TIME_FORMATTER);
    }

    /**
     * 批次和运行编号统一为 14 位平台时间格式；同一秒创建多条记录时顺延秒值，避免编号冲突。
     */
    private String nextExecutionId() {
        long epochSecond = EXECUTION_ID_SEQUENCE_SECONDS.updateAndGet(previous -> Math.max(Instant.now()
            .getEpochSecond(), previous + 1));
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), PLATFORM_ZONE_ID)
            .format(EXECUTION_ID_FORMATTER);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapReference(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>)value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> listValue(Object value) {
        return value instanceof List<?> ? (List<Object>)value : new ArrayList<>();
    }

    private void replaceListItem(Map<String, Object> owner, String key, Object oldValue, Object newValue) {
        List<Object> values = listValue(owner.get(key));
        int index = values.indexOf(oldValue);
        if (index >= 0) {
            values.set(index, newValue);
        }
    }

    private Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeExecutionDateTime(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String normalized = value.trim();
        try {
            return Instant.parse(normalized).atZone(PLATFORM_ZONE_ID).format(PLATFORM_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续兼容带偏移量和本地时间格式。
        }
        try {
            return OffsetDateTime.parse(normalized)
                .atZoneSameInstant(PLATFORM_ZONE_ID)
                .format(PLATFORM_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续兼容无时区的 ISO 本地时间。
        }
        for (DateTimeFormatter formatter : List
            .of(DateTimeFormatter.ISO_LOCAL_DATE_TIME, PLATFORM_DATE_TIME_FORMATTER)) {
            try {
                return LocalDateTime.parse(normalized, formatter).format(PLATFORM_DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                // 未识别的旧数据保持原值，避免结果回传失败。
            }
        }
        return normalized;
    }

    private Map<String, Object> normalizeExecutionTimes(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(key, normalizeExecutionTimeValue(key, value)));
        return normalized;
    }

    private Object normalizeExecutionTimeValue(String key, Object value) {
        if (value instanceof CharSequence && isExecutionDateTimeField(key)) {
            return normalizeExecutionDateTime(String.valueOf(value));
        }
        if (value instanceof Map<?, ?>) {
            return normalizeExecutionTimes(asObjectMap(value));
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeExecutionTimeValue("", item));
            }
            return normalized;
        }
        return value;
    }

    private boolean isExecutionDateTimeField(String key) {
        return "timestamp".equals(key) || key.endsWith("_at") || key.endsWith("At");
    }

    private List<Object> readStepResults(Map<String, Object> rawResult, Map<String, Object> caseResult) {
        Object steps = caseResult.get("steps");
        if (!(steps instanceof List<?>)) {
            // Playwright Runner 旧协议直接把逐步骤结果放在 raw.steps，不能只按扩展 case_result 读取。
            steps = rawResult.get("steps");
        }
        if (!(steps instanceof List<?>)) {
            steps = asObjectMap(rawResult.get("detail")).get("step_results");
        }
        if (!(steps instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    private int countStepStatus(List<Object> stepResults, String status) {
        int count = 0;
        for (Object item : stepResults) {
            if (item instanceof Map<?, ?> map && status.equals(String.valueOf(map.get("status")))) {
                count++;
            }
        }
        return count;
    }

    private Long calculateStepDuration(List<Object> stepResults) {
        if (stepResults.isEmpty()) {
            return null;
        }
        long durationMs = 0;
        for (Object item : stepResults) {
            Map<String, Object> step = asObjectMap(item);
            Object value = step.containsKey("duration_ms") ? step.get("duration_ms") : step.get("duration");
            if (value == null || StringUtils.isBlank(String.valueOf(value))) {
                return null;
            }
            Long stepDuration = parseDuration(value);
            if (stepDuration == null) {
                return null;
            }
            durationMs += stepDuration;
        }
        return durationMs;
    }

    private Long parseDuration(Object value) {
        try {
            double duration = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(duration) && duration >= 0 ? Math.round(duration) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updatePersistedJobArtifacts(Map<String, Object> rawResult) {
        if (automationPlaywrightJobMapper == null) {
            return;
        }
        String jobId = stringValue(rawResult.get("job_id"));
        if (StringUtils.isBlank(jobId)) {
            return;
        }
        automationPlaywrightJobMapper.update(null, Wrappers.<AutomationPlaywrightJobDO>lambdaUpdate()
            .eq(AutomationPlaywrightJobDO::getJobId, jobId)
            .set(AutomationPlaywrightJobDO::getArtifactFileIds, toJson(rawResult.get("artifact_file_ids")))
            .set(AutomationPlaywrightJobDO::getArtifactUrls, toJson(rawResult.get("artifacts"))));
    }

    private String toJson(Object value) {
        return JSONUtil.toJsonStr(value == null ? Map.of() : value);
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long nullableLong(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatRate(int pass, int total) {
        if (total <= 0) {
            return "0%";
        }
        double rate = Math.round(pass * 10000.0 / total) / 100.0;
        return rate == Math.rint(rate) ? String.valueOf((long)rate) + "%" : String.valueOf(rate) + "%";
    }

    private ResolvedCase resolveCase(String caseKey) {
        String[] parts = caseKey == null ? new String[0] : caseKey.split(":", 2);
        if (parts.length != 2) {
            throw new BusinessException("Playwright caseKey 格式必须为 sceneId:caseId");
        }
        AutomationUiSceneDO scene = resolveScene(parts[0]);
        CaseDO caseDO = findCase(scene, parts[1]);
        if (caseDO == null) {
            throw new BusinessException("Playwright 目标用例不存在，caseId=" + parts[1]);
        }
        return new ResolvedCase(scene, caseDO);
    }

    private AutomationUiSceneDO resolveScene(String sceneKey) {
        AutomationUiSceneDO scene;
        try {
            scene = automationUiSceneMapper.selectById(Long.valueOf(sceneKey));
        } catch (NumberFormatException e) {
            scene = automationUiSceneMapper.selectOne(new LambdaQueryWrapper<AutomationUiSceneDO>()
                .eq(AutomationUiSceneDO::getSceneId, sceneKey));
        }
        if (scene == null) {
            throw new BusinessException("Playwright 目标场景不存在，sceneKey=" + sceneKey);
        }
        return scene;
    }

    private CaseDO findCase(AutomationUiSceneDO scene, String caseId) {
        if (scene.getCaseList() == null) {
            return null;
        }
        for (CaseDO caseDO : scene.getCaseList()) {
            if (caseDO != null && caseId.equals(caseDO.getId())) {
                return caseDO;
            }
        }
        return null;
    }

    private List<StepDOAdapter> stepAdapters(CaseDO caseDO) {
        List<StepDOAdapter> adapters = new ArrayList<>();
        if (caseDO.getStepList() == null) {
            return adapters;
        }
        caseDO.getStepList()
            .stream()
            .sorted((a, b) -> Integer.compare(a.getOrder() == null ? 0 : a.getOrder(), b.getOrder() == null
                ? 0
                : b.getOrder()))
            .forEach(step -> adapters.add(new StepDOAdapter(step)));
        return adapters;
    }

    private void fillCaseRuntimeFields(AutomationPlaywrightCaseResp resp, List<Map<String, Object>> steps) {
        String startUrl = firstPageUrl(steps);
        Object windowSizeMode = firstConfigValue(steps, "window_size_mode", "maximized");
        Object screenshotMode = firstConfigValue(steps, "screenshot_mode", "standard");
        Object pageErrorCheckEnabled = firstConfigValue(steps, "page_error_check_enabled", 0);
        Object viewportWidth = firstConfigValue(steps, "viewport_width", null);
        Object viewportHeight = firstConfigValue(steps, "viewport_height", null);
        resp.setStartUrl(startUrl);
        resp.setStart_url(startUrl);
        resp.setWindowSizeMode(String.valueOf(windowSizeMode));
        resp.setWindow_size_mode(String.valueOf(windowSizeMode));
        resp.setScreenshotMode(String.valueOf(screenshotMode));
        resp.setScreenshot_mode(String.valueOf(screenshotMode));
        resp.setPageErrorCheckEnabled(toInteger(pageErrorCheckEnabled));
        resp.setPage_error_check_enabled(resp.getPageErrorCheckEnabled());
        resp.setViewportWidth(toInteger(viewportWidth));
        resp.setViewport_width(resp.getViewportWidth());
        resp.setViewportHeight(toInteger(viewportHeight));
        resp.setViewport_height(resp.getViewportHeight());
    }

    private String firstPageUrl(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps) {
            for (String key : List.of("start_url", "startUrl", "url")) {
                String value = StringUtils.trimToEmpty(Objects.toString(step.get(key), ""));
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private Object firstConfigValue(List<Map<String, Object>> steps, String key, Object fallback) {
        for (Map<String, Object> step : steps) {
            Object value = step.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyProjectEnvironment(AutomationPlaywrightCaseResp resp,
                                         AutomationUiSceneDO scene,
                                         Long projectEnvironmentId) {
        ProjectEnvironmentConfigDO environment = projectEnvironmentConfigMapper.selectById(projectEnvironmentId);
        String context = "sceneId=" + scene.getSceneId() + "，caseId=" + resp
            .getCaseId() + "，projectEnvironmentId=" + projectEnvironmentId;
        if (environment == null) {
            throw new BusinessException("回放产品环境不存在，" + context);
        }
        if (!Objects.equals(scene.getProjectId(), environment.getProjectId())) {
            throw new BusinessException("回放产品环境与场景所属项目不一致，" + context);
        }
        if (!DisEnableStatusEnum.ENABLE.equals(environment.getStatus())) {
            throw new BusinessException("回放产品环境未启用，" + context);
        }

        PlaybackEnvironmentTarget target = resolvePlaybackTarget(environment, context);
        resp.setProjectEnvironmentId(projectEnvironmentId);
        resp.setProject_environment_id(projectEnvironmentId);
        resp.setProjectEnvironmentName(environment.getName());
        resp.setProject_environment_name(environment.getName());
        try {
            playbackUrlRewriter.rewrite(resp, target.address(), target.frontendPort());
        } catch (BusinessException e) {
            throw new BusinessException(e.getMessage() + "，" + context);
        }
        if (StringUtils.isBlank(resp.getStart_url())) {
            throw new BusinessException("回放用例缺少可改写的起始地址，" + context);
        }
    }

    private PlaybackEnvironmentTarget resolvePlaybackTarget(ProjectEnvironmentConfigDO environment, String context) {
        String lastDomain = StringUtils.trimToEmpty(environment.getLastDomain());
        if (StringUtils.isNotBlank(lastDomain)) {
            return new PlaybackEnvironmentTarget(lastDomain, "");
        }

        ProjectServerConfigDO server = resolvePrimaryServer(environment.getServerConfig());
        if (server == null) {
            throw new BusinessException("回放产品环境未配置服务器信息，" + context);
        }
        String frontendDomain = resolveServerConfigParam(server, "前端域名");
        String frontendPort = resolveServerConfigParam(server, "前端端口");
        String address = StringUtils.isNotBlank(frontendDomain)
            ? frontendDomain
            : StringUtils.trimToEmpty(server.getIp());
        if (StringUtils.isBlank(address)) {
            throw new BusinessException("回放产品环境未配置可用的前端域名或服务器 IP，" + context);
        }
        return new PlaybackEnvironmentTarget(address, frontendPort);
    }

    private ProjectServerConfigDO resolvePrimaryServer(List<?> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        ProjectServerConfigDO fallback = null;
        for (Object item : source) {
            ProjectServerConfigDO server = BeanUtil.toBean(item, ProjectServerConfigDO.class);
            if (fallback == null) {
                fallback = server;
            }
            if (DisEnableStatusEnum.ENABLE.equals(server.getStatus())) {
                return server;
            }
        }
        return fallback;
    }

    private String resolveServerConfigParam(ProjectServerConfigDO server, String name) {
        if (server.getConfigList() == null) {
            return "";
        }
        for (Object item : server.getConfigList()) {
            if (!(item instanceof Map<?, ?> map) || !Objects.equals(name, String.valueOf(map.get("paramsName")))) {
                continue;
            }
            Object value = map.get("paramsValue");
            return value == null ? "" : String.valueOf(value).trim();
        }
        return "";
    }

    private record ResolvedCase(AutomationUiSceneDO scene, CaseDO caseDO) {
    }

    private record PlaybackEnvironmentTarget(String address, String frontendPort) {
    }

    private record StepDOAdapter(top.continew.admin.automation.model.entity.ui.StepDO step) {
    }
}
