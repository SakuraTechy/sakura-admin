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

package top.continew.admin.test.service.impl;

import cn.hutool.core.bean.BeanUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCaseStatusReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;
import top.continew.admin.automation.service.AutomationPlanReportProgressService;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 测试计划级 Playwright Runner/CDP 调度器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPlanExecutionDispatchService {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> TERMINAL_JOB_STATUSES = List.of("passed", "failed", "cancelled");

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final AutomationUiExecutionRecordService executionRecordService;
    private final AutomationPlaywrightCaseService playwrightCaseService;
    private final AutomationPlaywrightRunnerJobService runnerJobService;
    private final AutomationPlanReportProgressService reportProgressService;
    private final TestPlanMapper testPlanMapper;
    private final ExecutorService planExecutor = Executors.newCachedThreadPool();
    private final Map<String, ExecutionControl> activeExecutions = new ConcurrentHashMap<>();

    /**
     * 为计划内每个场景建立报告占位记录，并返回按计划顺序排列的可执行清单。
     */
    public List<TestPlanExecuteResp.SceneExecution> initialize(TestPlanDO plan,
                                                               List<Long> executionSceneIds,
                                                               String reportId,
                                                               TestExecutionEngineEnum engine,
                                                               TestPlanExecuteReq req) {
        if (req.getCaseIds() != null && executionSceneIds.size() != 1) {
            throw new BusinessException("指定用例范围时只能执行一个场景");
        }
        List<AutomationUiSceneDO> scenes = automationUiSceneMapper.selectBatchIds(executionSceneIds);
        Map<Long, AutomationUiSceneDO> sceneMap = new LinkedHashMap<>();
        scenes.forEach(scene -> sceneMap.put(scene.getId(), scene));
        List<TestPlanExecuteResp.SceneExecution> manifest = new ArrayList<>();
        for (Long sceneId : executionSceneIds) {
            AutomationUiSceneDO scene = sceneMap.get(sceneId);
            if (scene == null) {
                continue;
            }
            List<String> caseIds = resolveExecutionCaseIds(scene, req.getCaseIds());
            TestPlanExecuteResp.SceneExecution item = new TestPlanExecuteResp.SceneExecution();
            item.setSceneKey(String.valueOf(scene.getId()));
            item.setSceneId(scene.getSceneId());
            item.setSceneName(scene.getName());
            item.setCaseIds(caseIds);
            item.setStatus(caseIds.isEmpty() ? "SKIPPED" : "WAITING");
            item.setReason(caseIds.isEmpty() ? "无启用且包含有效步骤的用例" : null);
            manifest.add(item);
            storePlaceholder(scene, plan, reportId, engine, req, caseIds);
        }
        reportProgressService.onProgressChanged(String.valueOf(plan.getId()), reportId);
        return manifest;
    }

    public void dispatchRunner(TestPlanDO plan,
                               String reportId,
                               TestPlanExecuteReq req,
                               List<TestPlanExecuteResp.SceneExecution> manifest,
                               String token) {
        ExecutionControl control = new ExecutionControl(String.valueOf(plan.getId()), reportId);
        activeExecutions.put(reportId, control);
        planExecutor.submit(() -> runPlan(control, req, manifest, token));
    }

    public void cancel(String testPlanId, String reportId) {
        ExecutionControl control = activeExecutions.get(reportId);
        if (control != null) {
            control.cancelled = true;
            if (StringUtils.isNotBlank(control.currentJobId)) {
                try {
                    runnerJobService.cancel(control.currentJobId);
                } catch (Exception e) {
                    log.warn("取消 Runner 任务失败，jobId={}", control.currentJobId, e);
                }
            }
        }
        cancelSceneRecords(testPlanId, reportId);
        reportProgressService.onProgressChanged(testPlanId, reportId);
    }

    @PreDestroy
    public void shutdown() {
        activeExecutions.values().forEach(control -> {
            control.cancelled = true;
            cancelSceneRecords(control.testPlanId, control.reportId);
        });
        planExecutor.shutdownNow();
    }

    private void runPlan(ExecutionControl control,
                         TestPlanExecuteReq req,
                         List<TestPlanExecuteResp.SceneExecution> manifest,
                         String token) {
        try {
            for (TestPlanExecuteResp.SceneExecution scene : manifest) {
                if (control.cancelled) {
                    break;
                }
                if (scene.getCaseIds() == null || scene.getCaseIds().isEmpty()) {
                    continue;
                }
                runScene(control, req, scene, token);
            }
        } catch (Exception e) {
            log.error("测试计划 Runner 调度失败，reportId={}", control.reportId, e);
            markWaitingRecordsFailed(control.testPlanId, control.reportId, StringUtils.defaultIfBlank(e
                .getMessage(), "Playwright Runner 计划调度失败"));
        } finally {
            if (control.cancelled) {
                cancelSceneRecords(control.testPlanId, control.reportId);
            }
            reportProgressService.onProgressChanged(control.testPlanId, control.reportId);
            activeExecutions.remove(control.reportId);
        }
    }

    private void runScene(ExecutionControl control,
                          TestPlanExecuteReq req,
                          TestPlanExecuteResp.SceneExecution scene,
                          String token) {
        AutomationPlaywrightBatchCreateReq batchReq = new AutomationPlaywrightBatchCreateReq();
        batchReq.setSceneKey(scene.getSceneKey());
        batchReq.setExecutionType(TestExecutionEngineEnum.PLAYWRIGHT_RUNNER.getExecutionType());
        batchReq.setCaseIds(scene.getCaseIds());
        batchReq.setProjectEnvironmentId(req.getProjectEnvironmentId());
        batchReq.setExecuteName(req.getExecuteName());
        batchReq.setExecuteEmail(req.getExecuteEmail());
        batchReq.setTestPlanId(control.testPlanId);
        batchReq.setTestReportId(control.reportId);
        AutomationPlaywrightRunnerOptionsReq options = req.getRunnerOptions() == null
            ? new AutomationPlaywrightRunnerOptionsReq()
            : req.getRunnerOptions();
        batchReq.setExecutionConfig(BeanUtil.beanToMap(options));
        AutomationPlaywrightBatchResp batch;
        try {
            batch = playwrightCaseService.createBatch(batchReq);
        } catch (Exception e) {
            markSceneRecordFailed(scene.getSceneKey(), control.reportId, StringUtils.defaultIfBlank(e
                .getMessage(), "创建 Runner 批次失败"));
            return;
        }
        Map<String, AutomationPlaywrightBatchResp.CaseExecution> executions = new LinkedHashMap<>();
        batch.getCases().forEach(item -> executions.put(item.getCaseId(), item));
        for (String caseId : scene.getCaseIds()) {
            AutomationPlaywrightCaseCancellationResp cancellation = playwrightCaseService.getCaseCancellation(scene
                .getSceneKey(), batch.getBatchId(), caseId);
            if (control.cancelled || cancellation.isBatchCancelRequested()) {
                control.cancelled = true;
                runnerJobService.cancelBatch(batch.getBatchId());
                playwrightCaseService.cancelBatch(scene.getSceneKey(), batch.getBatchId());
                return;
            }
            if (cancellation.isCaseCancelRequested()) {
                continue;
            }
            AutomationPlaywrightBatchResp.CaseExecution execution = executions.get(caseId);
            if (execution == null || !"queued".equalsIgnoreCase(execution.getStatus())) {
                // 只有 queued 用例允许启动 Runner；禁用用例不会进入批次执行范围。
                continue;
            }
            AutomationPlaywrightRunnerOptionsReq effectiveOptions = toRunnerOptions(execution
                .getEffectiveExecutionConfig(), options);
            runCase(control, req, scene, batch.getBatchId(), batch
                .getExecutionCapability(), caseId, execution, effectiveOptions, token);
        }
    }

    private AutomationPlaywrightRunnerOptionsReq toRunnerOptions(Map<String, Object> effectiveConfig,
                                                                 AutomationPlaywrightRunnerOptionsReq fallback) {
        AutomationPlaywrightRunnerOptionsReq options = fallback == null
            ? new AutomationPlaywrightRunnerOptionsReq()
            : BeanUtil.copyProperties(fallback, AutomationPlaywrightRunnerOptionsReq.class);
        if (effectiveConfig == null || effectiveConfig.isEmpty() || effectiveConfig.containsKey("error")) {
            return options;
        }
        // 使用批次冻结的配置，避免测试计划入口与调试入口各自合并出不同执行事实。
        setIfPresent(effectiveConfig, "browser", value -> options.setBrowser(String.valueOf(value)));
        setIfPresent(effectiveConfig, "live_frame_quality", value -> options.setLiveFrameQuality(String
            .valueOf(value)));
        setIfPresent(effectiveConfig, "session_mode", value -> options.setSessionMode(String.valueOf(value)));
        setIfPresent(effectiveConfig, "headed", value -> options.setHeaded(booleanValue(value)));
        setIfPresent(effectiveConfig, "ignore_https_errors", value -> options
            .setIgnoreHttpsErrors(booleanValue(value)));
        setIfPresent(effectiveConfig, "page_error_check_enabled", value -> options
            .setPageErrorCheckEnabled(booleanValue(value)));
        setIfPresent(effectiveConfig, "trace", value -> options.setTrace(String.valueOf(value)));
        setIfPresent(effectiveConfig, "video", value -> options.setVideo(String.valueOf(value)));
        setIfPresent(effectiveConfig, "step_timeout_ms", value -> options.setStepTimeoutMs(intValue(value)));
        setIfPresent(effectiveConfig, "case_timeout_ms", value -> options.setCaseTimeoutMs(intValue(value)));
        setIfPresent(effectiveConfig, "slow_mo_ms", value -> options.setSlowMoMs(intValue(value)));
        setIfPresent(effectiveConfig, "finish_delay_ms", value -> options.setFinishDelayMs(intValue(value)));
        return options;
    }

    private void setIfPresent(Map<String, Object> config, String key, java.util.function.Consumer<Object> setter) {
        Object value = config.get(key);
        if (value != null) {
            setter.accept(value);
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean ? (Boolean)value : Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number)value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private void runCase(ExecutionControl control,
                         TestPlanExecuteReq req,
                         TestPlanExecuteResp.SceneExecution scene,
                         String batchId,
                         String executionCapability,
                         String caseId,
                         AutomationPlaywrightBatchResp.CaseExecution execution,
                         AutomationPlaywrightRunnerOptionsReq options,
                         String token) {
        AutomationPlaywrightBatchCaseStatusReq statusReq = new AutomationPlaywrightBatchCaseStatusReq();
        statusReq.setStatus("starting");
        statusReq.setStartedAt(now());
        playwrightCaseService.updateBatchCaseStatus(scene.getSceneKey(), batchId, caseId, statusReq);
        try {
            AutomationPlaywrightRunnerJobReq jobReq = new AutomationPlaywrightRunnerJobReq();
            jobReq.setCaseKey(scene.getSceneKey() + ":" + caseId);
            jobReq.setBatchId(batchId);
            jobReq.setExecutionId(execution == null ? null : execution.getExecutionId());
            jobReq.setExecutionCapability(executionCapability);
            jobReq.setProjectEnvironmentId(req.getProjectEnvironmentId());
            jobReq.setOptions(options);
            AutomationPlaywrightRunnerJobResp job = runnerJobService.create(jobReq, token);
            control.currentJobId = job.getJobId();
            updateRunningStatus(scene.getSceneKey(), batchId, caseId, job.getJobId());
            waitForJob(control, scene.getSceneKey(), batchId, caseId, job, options);
        } catch (Exception e) {
            updateFailedStatus(scene.getSceneKey(), batchId, caseId, StringUtils.defaultIfBlank(e
                .getMessage(), "Runner 用例调度失败"));
        } finally {
            control.currentJobId = null;
        }
    }

    private void waitForJob(ExecutionControl control,
                            String sceneKey,
                            String batchId,
                            String caseId,
                            AutomationPlaywrightRunnerJobResp initial,
                            AutomationPlaywrightRunnerOptionsReq options) throws InterruptedException {
        AutomationPlaywrightRunnerJobResp job = initial;
        long timeout = Math.max(10_000L, options.getCaseTimeoutMs() == null
            ? 600_000L
            : options.getCaseTimeoutMs()) + 60_000L;
        long deadline = System.currentTimeMillis() + timeout;
        while (!TERMINAL_JOB_STATUSES.contains(job.getStatus()) && System.currentTimeMillis() < deadline) {
            AutomationPlaywrightCaseCancellationResp cancellation = playwrightCaseService
                .getCaseCancellation(sceneKey, batchId, caseId);
            if (control.cancelled || cancellation.isBatchCancelRequested() || cancellation.isCaseCancelRequested()) {
                runnerJobService.cancel(job.getJobId());
                if (control.cancelled || cancellation.isBatchCancelRequested()) {
                    control.cancelled = true;
                    playwrightCaseService.cancelBatch(sceneKey, batchId);
                }
                return;
            }
            Thread.sleep(500L);
            job = runnerJobService.get(job.getJobId());
        }
        if (!TERMINAL_JOB_STATUSES.contains(job.getStatus())) {
            runnerJobService.cancel(job.getJobId());
            updateFailedStatus(sceneKey, batchId, caseId, "Runner 用例等待超时");
        } else if ("failed".equals(job.getStatus())) {
            updateFailedStatus(sceneKey, batchId, caseId, StringUtils.defaultIfBlank(job.getError(), "Runner 用例执行失败"));
        } else if ("cancelled".equals(job.getStatus())) {
            AutomationPlaywrightBatchCaseStatusReq statusReq = new AutomationPlaywrightBatchCaseStatusReq();
            statusReq.setStatus("cancelled");
            statusReq.setFinishedAt(now());
            statusReq.setError("Runner 用例已取消");
            playwrightCaseService.updateBatchCaseStatus(sceneKey, batchId, caseId, statusReq);
        }
        // passed 由 Runner 结果回传形成完整步骤事实，不用任务状态覆盖详细结果。
    }

    private void updateRunningStatus(String sceneKey, String batchId, String caseId, String jobId) {
        AutomationPlaywrightBatchCaseStatusReq req = new AutomationPlaywrightBatchCaseStatusReq();
        req.setStatus("running");
        req.setJobId(jobId);
        playwrightCaseService.updateBatchCaseStatus(sceneKey, batchId, caseId, req);
    }

    private void updateFailedStatus(String sceneKey, String batchId, String caseId, String error) {
        AutomationPlaywrightBatchCaseStatusReq req = new AutomationPlaywrightBatchCaseStatusReq();
        req.setStatus("failed");
        req.setFinishedAt(now());
        req.setError(error);
        playwrightCaseService.updateBatchCaseStatus(sceneKey, batchId, caseId, req);
    }

    private void storePlaceholder(AutomationUiSceneDO scene,
                                  TestPlanDO plan,
                                  String reportId,
                                  TestExecutionEngineEnum engine,
                                  TestPlanExecuteReq req,
                                  List<String> caseIds) {
        boolean skipped = caseIds.isEmpty();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordType", "plan-execution-placeholder");
        record.put("testPlanId", String.valueOf(plan.getId()));
        record.put("testReportId", reportId);
        record.put("executionType", engine.getExecutionType());
        record.put("executionId", reportId + "-" + scene.getId());
        record.put("executeName", StringUtils.defaultIfBlank(req.getExecuteName(), "-"));
        record.put("executeEmail", StringUtils.defaultIfBlank(req.getExecuteEmail(), "-"));
        record.put("startedAt", now());
        record.put("finishedAt", skipped ? now() : null);
        record.put("executeStatus", skipped ? "completed" : "queued");
        record.put("executeResult", skipped ? "skipped" : "not_executed");
        record.put("error", skipped ? "无可执行用例" : null);
        record.put("projectEnvironmentId", req.getProjectEnvironmentId());
        record.put("caseTotal", caseIds.size());
        record.put("casePass", 0);
        record.put("caseFail", 0);
        record.put("caseSkip", skipped ? caseIds.size() : 0);
        record.put("stepTotal", executableStepTotal(scene, caseIds));
        record.put("stepPass", 0);
        record.put("stepFail", 0);
        record.put("stepSkip", 0);
        record.put("duration", 0);
        scene.setReportId(Long.valueOf(reportId));
        scene.setExecuteStatus(skipped ? "completed" : "queued");
        scene.setExecuteResult(skipped ? "skipped" : "not_executed");
        scene.setCaseTotal(caseIds.size());
        scene.setCasePass(0);
        scene.setCaseFail(0);
        scene.setCaseSkip(skipped ? caseIds.size() : 0);
        scene.setStepTotal(executableStepTotal(scene, caseIds));
        scene.setStepPass(0);
        scene.setStepFail(0);
        scene.setStepSkip(0);
        executionRecordService.saveRecord(scene, record, null);
    }

    private List<String> executableCaseIds(AutomationUiSceneDO scene) {
        List<String> result = new ArrayList<>();
        if (scene.getCaseList() == null) {
            return result;
        }
        scene.getCaseList()
            .stream()
            .filter(Objects::nonNull)
            .filter(item -> item.getStatus() == null || StatusTypeEnum.ENABLE.equals(item.getStatus()))
            .filter(this::hasSteps)
            .sorted((left, right) -> Integer.compare(Objects.requireNonNullElse(left
                .getOrder(), Integer.MAX_VALUE), Objects.requireNonNullElse(right.getOrder(), Integer.MAX_VALUE)))
            .map(CaseDO::getId)
            .filter(StringUtils::isNotBlank)
            .forEach(result::add);
        return result;
    }

    /**
     * 单场景执行保留用户选择的用例范围，同时按场景定义顺序调度。
     */
    private List<String> resolveExecutionCaseIds(AutomationUiSceneDO scene, List<String> requestedCaseIds) {
        List<String> executableIds = executableCaseIds(scene);
        if (requestedCaseIds == null) {
            return executableIds;
        }
        if (requestedCaseIds.isEmpty() || requestedCaseIds.stream().anyMatch(StringUtils::isBlank)) {
            throw new BusinessException("执行用例不能为空");
        }
        LinkedHashSet<String> requestedSet = new LinkedHashSet<>(requestedCaseIds);
        if (requestedSet.size() != requestedCaseIds.size()) {
            throw new BusinessException("执行用例不能重复");
        }
        if (!executableIds.containsAll(requestedSet)) {
            throw new BusinessException("执行用例不存在、已禁用或没有有效步骤");
        }
        return executableIds.stream().filter(requestedSet::contains).toList();
    }

    private int executableStepTotal(AutomationUiSceneDO scene, List<String> caseIds) {
        if (scene.getCaseList() == null) {
            return 0;
        }
        return scene.getCaseList()
            .stream()
            .filter(Objects::nonNull)
            .filter(item -> caseIds.contains(item.getId()))
            .mapToInt(this::executableStepTotal)
            .sum();
    }

    private boolean hasSteps(CaseDO item) {
        return executableStepTotal(item) > 0;
    }

    private int executableStepTotal(CaseDO item) {
        if (item.getStepList() != null) {
            return (int)item.getStepList()
                .stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStatus() == null || StatusTypeEnum.ENABLE.equals(step.getStatus()))
                .count();
        }
        return item.getStep() != null && (item.getStep().getStatus() == null || StatusTypeEnum.ENABLE.equals(item
            .getStep()
            .getStatus())) ? 1 : 0;
    }

    private void cancelSceneRecords(String testPlanId, String reportId) {
        TestPlanDO plan = findPlan(testPlanId);
        if (plan == null || plan.getUiTestScene() == null) {
            return;
        }
        for (AutomationUiSceneDO scene : automationUiSceneMapper.selectBatchIds(plan.getUiTestScene())) {
            Map<String, Object> record = findRecord(scene, reportId);
            if (record == null) {
                continue;
            }
            String batchId = record.get("batchId") == null ? "" : String.valueOf(record.get("batchId")).trim();
            if (StringUtils.isNotBlank(batchId)) {
                try {
                    // 先固化取消事实，Runner 退出时产生的迟到回调只能补充诊断，不能恢复终态。
                    playwrightCaseService.cancelBatch(String.valueOf(scene.getId()), batchId);
                } catch (Exception e) {
                    log.warn("写入计划场景取消终态失败，batchId={}", batchId, e);
                }
                try {
                    runnerJobService.cancelBatch(batchId);
                } catch (Exception e) {
                    log.warn("停止计划场景 Runner 进程失败，batchId={}", batchId, e);
                }
                continue;
            }
            if (terminal(record)) {
                continue;
            }
            record.put("executeStatus", "cancelled");
            record.put("executeResult", "cancelled");
            record.put("finishedAt", now());
            record.put("error", "测试计划执行已取消");
            executionRecordService.saveRecord(scene, record, null);
        }
    }

    private void markWaitingRecordsFailed(String testPlanId, String reportId, String error) {
        TestPlanDO plan = findPlan(testPlanId);
        if (plan == null || plan.getUiTestScene() == null) {
            return;
        }
        for (Long sceneId : plan.getUiTestScene()) {
            markSceneRecordFailed(String.valueOf(sceneId), reportId, error);
        }
    }

    private void markSceneRecordFailed(String sceneKey, String reportId, String error) {
        AutomationUiSceneDO scene = automationUiSceneMapper.selectById(Long.valueOf(sceneKey));
        Map<String, Object> record = findRecord(scene, reportId);
        if (record == null || terminal(record)) {
            return;
        }
        record.put("executeStatus", "completed");
        record.put("executeResult", "failed");
        record.put("finishedAt", now());
        record.put("error", error);
        record.put("caseFail", Math.max(1, number(record.get("caseTotal"))));
        Object caseResults = record.get("caseResults");
        if (caseResults instanceof List<?> results) {
            for (Object item : results) {
                if (item instanceof Map<?, ?> rawCase) {
                    @SuppressWarnings("unchecked") Map<String, Object> caseResult = (Map<String, Object>)rawCase;
                    String status = String.valueOf(caseResult.get("status"));
                    if (!List.of("passed", "failed", "cancelled", "blocked", "skipped").contains(status)) {
                        caseResult.put("status", "failed");
                        caseResult.put("finished_at", now());
                        caseResult.put("error", error);
                    }
                }
            }
        }
        executionRecordService.saveRecord(scene, record, null);
        reportProgressService.onProgressChanged(String.valueOf(record.get("testPlanId")), reportId);
    }

    private TestPlanDO findPlan(String testPlanId) {
        try {
            return testPlanMapper.selectById(Long.valueOf(testPlanId));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> findRecord(AutomationUiSceneDO scene, String reportId) {
        if (scene == null) {
            return null;
        }
        Map<String, Object> normalized = executionRecordService.findReportRecord(scene.getId(), reportId);
        if (normalized != null) {
            return normalized;
        }
        if (scene.getTestRecord() == null) {
            return null;
        }
        for (Object item : scene.getTestRecord()) {
            if (item instanceof Map<?, ?> map && reportId.equals(String.valueOf(map.get("testReportId")))) {
                @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>)map;
                return result;
            }
        }
        return null;
    }

    private boolean terminal(Map<String, Object> record) {
        return List.of("completed", "cancelled", "failed", "blocked", "skipped", "12")
            .contains(String.valueOf(record.get("executeStatus")).toLowerCase());
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String now() {
        return LocalDateTime.now(PLATFORM_ZONE_ID).format(DATE_TIME_FORMATTER);
    }

    private static final class ExecutionControl {
        private final String testPlanId;
        private final String reportId;
        private volatile boolean cancelled;
        private volatile String currentJobId;

        private ExecutionControl(String testPlanId, String reportId) {
            this.testPlanId = testPlanId;
            this.reportId = reportId;
        }
    }
}
