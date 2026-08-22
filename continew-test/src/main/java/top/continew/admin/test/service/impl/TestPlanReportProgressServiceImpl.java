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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.service.AutomationPlanReportProgressService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 从场景 testRecord 聚合正式测试报告和测试计划进度。
 */
@Service
@RequiredArgsConstructor
public class TestPlanReportProgressServiceImpl implements AutomationPlanReportProgressService {

    private static final int MAX_ARTIFACT_ITEMS = 100;

    private final AutomationUiExecutionRecordService executionRecordService;
    private final TestPlanMapper testPlanMapper;
    private final TestReportMapper testReportMapper;
    private final TestTimedTaskRunService timedTaskRunService;

    @Override
    public void validateBinding(String testPlanId, String testReportId, String sceneKey, String executionType) {
        Long planId = parseId(testPlanId, "测试计划 ID 无效");
        Long reportId = parseId(testReportId, "测试报告 ID 无效");
        Long sceneId = parseId(sceneKey, "UI 场景 ID 无效");
        TestReportDO report = testReportMapper.selectById(reportId);
        if (report == null || !Objects.equals(report.getTestPlanId(), planId)) {
            throw new BusinessException("测试报告不属于当前测试计划，testReportId=" + testReportId);
        }
        String expectedType = reportType(executionType);
        if (!expectedType.equalsIgnoreCase(report.getReportType())) {
            throw new BusinessException("测试报告类型与执行方式不一致，reportType=" + report.getReportType());
        }
        if (!"RUNNING".equalsIgnoreCase(report.getStatus())) {
            throw new BusinessException("测试报告已结束，不能继续写入执行结果，testReportId=" + testReportId);
        }
        TestPlanDO plan = testPlanMapper.selectById(planId);
        if (plan == null || !resolveExecutionSceneIds(report, plan).contains(sceneId)) {
            throw new BusinessException("UI 场景不属于当前报告执行范围，sceneKey=" + sceneKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onProgressChanged(String testPlanId, String testReportId) {
        Long planId = parseId(testPlanId, "测试计划 ID 无效");
        Long reportId = parseId(testReportId, "测试报告 ID 无效");
        TestPlanDO plan = testPlanMapper.selectById(planId);
        TestReportDO report = testReportMapper.selectById(reportId);
        if (plan == null || report == null || !Objects.equals(report.getTestPlanId(), planId)) {
            return;
        }
        List<Long> executionSceneIds = resolveExecutionSceneIds(report, plan);
        Map<Long, Map<String, Object>> records = executionRecordService
            .findReportRecords(executionSceneIds, testReportId);
        Aggregate aggregate = aggregate(executionSceneIds, records);
        updateReport(report, plan, aggregate);
        updatePlan(plan, aggregate);
    }

    /**
     * Runner/CDP 依赖进程或浏览器会话，服务重启后不能伪装为仍在运行。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedReports() {
        List<TestReportDO> runningReports = testReportMapper.lambdaQuery()
            .eq(TestReportDO::getStatus, "RUNNING")
            .ne(TestReportDO::getReportType, TestExecutionEngineEnum.SELENIUM.name())
            .list();
        for (TestReportDO report : runningReports) {
            markIncompleteRecordsFailed(report);
            onProgressChanged(String.valueOf(report.getTestPlanId()), String.valueOf(report.getId()));
        }
    }

    private Aggregate aggregate(List<Long> sceneIds, Map<Long, Map<String, Object>> records) {
        Aggregate result = new Aggregate();
        result.sceneTotal = sceneIds.size();
        for (Long sceneId : sceneIds) {
            Map<String, Object> record = records.get(sceneId);
            if (record == null) {
                continue;
            }
            String status = value(record.get("executeStatus")).toLowerCase();
            String executeResult = value(record.get("executeResult")).toLowerCase();
            boolean terminal = isTerminalStatus(status);
            if (terminal) {
                result.sceneCompleted++;
            }
            if (isPassed(executeResult)) {
                result.scenePass++;
            } else if (isCancelled(executeResult)) {
                result.sceneCancelled++;
            } else if (isSkipped(executeResult)) {
                result.sceneSkip++;
            } else if (isFailed(executeResult) || terminal && !List.of("not_executed", "pending")
                .contains(executeResult)) {
                result.sceneFail++;
            }
            result.caseTotal += number(record.get("caseTotal"));
            result.casePass += number(record.get("casePass"));
            result.caseFail += number(record.get("caseFail"));
            result.caseSkip += number(record.get("caseSkip"));
            result.caseCancelled += number(record.get("caseCancelled"));
            result.stepTotal += number(record.get("stepTotal"));
            result.stepPass += number(record.get("stepPass"));
            result.stepFail += number(record.get("stepFail"));
            result.stepSkip += number(record.get("stepSkip"));
            if (isCancelled(executeResult) || "cancelled"
                .equals(status) || AutomationUiSceneStatusCodes.STATUS_CANCELLED.equals(status)) {
                result.hasCancellation = true;
            }
            if (terminal && (isFailed(executeResult) || "blocked".equals(status))) {
                result.hasFailure = true;
            }
            collectArtifacts(result, record);
        }
        result.terminal = result.sceneCompleted >= result.sceneTotal;
        result.allSkipped = result.sceneTotal == 0 || result.sceneSkip >= result.sceneTotal;
        return result;
    }

    private void updateReport(TestReportDO report, TestPlanDO plan, Aggregate aggregate) {
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("testPlanId", String.valueOf(plan.getId()));
        ui.put("testReportId", String.valueOf(report.getId()));
        ui.put("reportType", report.getReportType());
        ui.put("sceneTotal", aggregate.sceneTotal);
        ui.put("scenePass", aggregate.scenePass);
        ui.put("sceneFail", aggregate.sceneFail);
        ui.put("sceneSkip", aggregate.sceneSkip);
        ui.put("sceneCancelled", aggregate.sceneCancelled);
        ui.put("scenePassRate", rate(aggregate.scenePass, aggregate.sceneTotal));
        ui.put("caseTotal", aggregate.caseTotal);
        ui.put("casePass", aggregate.casePass);
        ui.put("caseFail", aggregate.caseFail);
        ui.put("caseSkip", aggregate.caseSkip);
        ui.put("caseCancelled", aggregate.caseCancelled);
        ui.put("casePassRate", rate(aggregate.casePass, aggregate.caseTotal));
        ui.put("stepTotal", aggregate.stepTotal);
        ui.put("stepPass", aggregate.stepPass);
        ui.put("stepFail", aggregate.stepFail);
        ui.put("stepSkip", aggregate.stepSkip);
        ui.put("stepPassRate", rate(aggregate.stepPass, aggregate.stepTotal));
        if (aggregate.allSkipped) {
            ui.put("failureReason", "无可执行用例");
        }
        Map<String, Object> statistic = new LinkedHashMap<>();
        statistic.put("ui", ui);
        if (!aggregate.artifactUrls.isEmpty() || !aggregate.artifactFileIds.isEmpty() || !aggregate.artifactUploadErrors
            .isEmpty()) {
            Map<String, Object> artifacts = new LinkedHashMap<>();
            artifacts.put("urls", aggregate.artifactUrls);
            artifacts.put("fileIds", aggregate.artifactFileIds);
            artifacts.put("uploadErrors", aggregate.artifactUploadErrors);
            artifacts.put("count", artifactCount(aggregate));
            statistic.put("playwrightArtifacts", artifacts);
            if (StringUtils.isBlank(report.getReportUrl())) {
                report.setReportUrl(firstArtifact(aggregate.artifactUrls, "report_html"));
            }
            if (StringUtils.isBlank(report.getVideoUrl())) {
                report.setVideoUrl(firstArtifact(aggregate.artifactUrls, "video"));
            }
        }
        report.setStatisticAnalysis(statistic);
        report.setRunTime(resolveRunTime(report));
        report.setStatus(aggregate.terminal
            ? aggregate.hasCancellation
                ? "CANCELLED"
                : aggregate.hasFailure || aggregate.allSkipped ? "FAILED" : "PASSED"
            : "RUNNING");
        testReportMapper.updateById(report);
        timedTaskRunService.completeByReport(report);
    }

    private void updatePlan(TestPlanDO plan, Aggregate aggregate) {
        int planSceneTotal = plan.getUiTestScene() == null ? 0 : plan.getUiTestScene().size();
        plan.setSceneCount(planSceneTotal);
        plan.setExecutedCount(aggregate.sceneCompleted);
        plan.setPassedCount(aggregate.scenePass);
        plan.setTestProgress(planSceneTotal == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(aggregate.sceneCompleted * 100.0 / planSceneTotal).setScale(2, RoundingMode.HALF_UP));
        plan.setRunTime(resolveRunTime(plan));
        plan.setActualEndTime(aggregate.terminal ? LocalDateTime.now() : null);
        plan.setStatus(aggregate.terminal ? "COMPLETED" : "RUNNING");
        testPlanMapper.updateById(plan);
    }

    private long resolveRunTime(TestPlanDO plan) {
        if (plan.getActualStartTime() == null) {
            return plan.getRunTime() == null ? 0 : plan.getRunTime();
        }
        return Math.max(0, java.time.Duration.between(plan.getActualStartTime(), LocalDateTime.now()).toMillis());
    }

    private void markIncompleteRecordsFailed(TestReportDO report) {
        TestPlanDO plan = report.getTestPlanId() == null ? null : testPlanMapper.selectById(report.getTestPlanId());
        if (plan == null || plan.getUiTestScene() == null) {
            report.setStatus("FAILED");
            testReportMapper.updateById(report);
            return;
        }
        executionRecordService.markReportIncompleteFailed(String.valueOf(report.getId()), "服务重启导致执行中断");
    }

    /**
     * 新报告按本次执行子集聚合；旧报告没有范围字段时继续按计划全部场景聚合。
     */
    private List<Long> resolveExecutionSceneIds(TestReportDO report, TestPlanDO plan) {
        Map<String, Object> runtimeEnvironment = report.getRuntimeEnvironment();
        if (runtimeEnvironment == null || !runtimeEnvironment.containsKey("executionSceneIds")) {
            return plan.getUiTestScene() == null ? List.of() : plan.getUiTestScene();
        }
        Object rawIds = runtimeEnvironment.get("executionSceneIds");
        if (!(rawIds instanceof List<?> values)) {
            return plan.getUiTestScene() == null ? List.of() : plan.getUiTestScene();
        }
        java.util.ArrayList<Long> result = new java.util.ArrayList<>();
        for (Object value : values) {
            try {
                result.add(value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value)));
            } catch (Exception ignored) {
                // 无效范围值不回退全量，避免子集报告意外聚合其他场景。
            }
        }
        return result;
    }

    private long resolveRunTime(TestReportDO report) {
        Object startedAt = report.getRuntimeEnvironment() == null
            ? null
            : report.getRuntimeEnvironment().get("startedAtEpochMs");
        if (startedAt instanceof Number number) {
            return Math.max(0, System.currentTimeMillis() - number.longValue());
        }
        return report.getRunTime() == null ? 0 : report.getRunTime();
    }

    private Long parseId(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            throw new BusinessException(message + "：" + value);
        }
    }

    private String reportType(String executionType) {
        return switch (value(executionType).toLowerCase()) {
            case "playwright-runner" -> TestExecutionEngineEnum.PLAYWRIGHT_RUNNER.name();
            case "extension-cdp" -> TestExecutionEngineEnum.CHROME_DEVTOOLS_PROTOCOL.name();
            default -> TestExecutionEngineEnum.SELENIUM.name();
        };
    }

    private boolean isTerminalStatus(String value) {
        return List
            .of("completed", "cancelled", "failed", "blocked", "skipped", AutomationUiSceneStatusCodes.STATUS_COMPLETED, AutomationUiSceneStatusCodes.STATUS_CANCELLED)
            .contains(value.toLowerCase());
    }

    private boolean isPassed(String value) {
        return "passed".equals(value) || AutomationUiSceneStatusCodes.RESULT_PASSED.equals(value);
    }

    private boolean isSkipped(String value) {
        return List.of("skipped", AutomationUiSceneStatusCodes.RESULT_SKIPPED).contains(value);
    }

    private boolean isCancelled(String value) {
        return "cancelled".equals(value) || AutomationUiSceneStatusCodes.RESULT_CANCELLED.equals(value);
    }

    private boolean isFailed(String value) {
        return List.of("failed", "blocked", AutomationUiSceneStatusCodes.RESULT_FAILED).contains(value);
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String rate(int passed, int total) {
        return total <= 0
            ? "0%"
            : BigDecimal.valueOf(passed * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void collectArtifacts(Aggregate aggregate, Map<String, Object> record) {
        collectArtifactMap(aggregate.artifactUrls, record.get("artifactUrls"));
        collectArtifactMap(aggregate.artifactUrls, record.get("playwrightArtifacts"));
        collectArtifactMap(aggregate.artifactFileIds, record.get("artifactFileIds"));
        collectArtifactMap(aggregate.artifactFileIds, record.get("artifact_file_ids"));
        Object caseResults = record.get("caseResults");
        if (caseResults instanceof List<?> cases) {
            for (Object item : cases) {
                if (!(item instanceof Map<?, ?> caseResult)) {
                    continue;
                }
                collectArtifactMap(aggregate.artifactUrls, caseResult.get("artifact_urls"));
                collectArtifactMap(aggregate.artifactUrls, caseResult.get("artifactUrls"));
                collectArtifactMap(aggregate.artifactFileIds, caseResult.get("artifact_file_ids"));
                collectArtifactMap(aggregate.artifactFileIds, caseResult.get("artifactFileIds"));
                collectErrors(aggregate, caseResult.get("artifact_upload_errors"));
                collectErrors(aggregate, caseResult.get("artifactUploadErrors"));
            }
        }
        collectErrors(aggregate, record.get("artifact_upload_errors"));
        collectErrors(aggregate, record.get("artifactUploadErrors"));
    }

    private void collectArtifactMap(Map<String, Set<String>> target, Object raw) {
        if (!(raw instanceof Map<?, ?> values)) {
            return;
        }
        values.forEach((key, value) -> {
            if (target.values().stream().mapToInt(Set::size).sum() >= MAX_ARTIFACT_ITEMS) {
                return;
            }
            String item = value(value);
            // 报告只允许保存 admin 受鉴权 URL，禁止把 Runner 节点本地路径暴露到主记录。
            if (!isSafeArtifactReference(item)) {
                return;
            }
            target.computeIfAbsent(String.valueOf(key), ignored -> new LinkedHashSet<>()).add(item);
        });
    }

    private void collectErrors(Aggregate aggregate, Object raw) {
        if (!(raw instanceof List<?> values)) {
            return;
        }
        for (Object value : values) {
            if (aggregate.artifactUploadErrors.size() >= MAX_ARTIFACT_ITEMS) {
                return;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> error = new LinkedHashMap<>();
                map.forEach((key, item) -> error.put(String.valueOf(key), item));
                aggregate.artifactUploadErrors.add(error);
            } else if (value != null) {
                aggregate.artifactUploadErrors.add(Map.of("error", value(value)));
            }
        }
    }

    private int artifactCount(Aggregate aggregate) {
        return aggregate.artifactUrls.values().stream().mapToInt(Set::size).sum();
    }

    private String firstArtifact(Map<String, Set<String>> artifacts, String type) {
        Set<String> values = artifacts.get(type);
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    private boolean isSafeArtifactReference(String value) {
        if (value == null || value.isBlank() || value.startsWith("file:")) {
            return false;
        }
        if (value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\\\\\")) {
            return false;
        }
        return value.startsWith("/") || value.matches("^[a-z][a-z0-9+.-]*://.*");
    }

    private static final class Aggregate {
        private int sceneTotal;
        private int sceneCompleted;
        private int scenePass;
        private int sceneFail;
        private int sceneSkip;
        private int sceneCancelled;
        private int caseTotal;
        private int casePass;
        private int caseFail;
        private int caseSkip;
        private int caseCancelled;
        private int stepTotal;
        private int stepPass;
        private int stepFail;
        private int stepSkip;
        private boolean terminal;
        private boolean allSkipped;
        private boolean hasFailure;
        private boolean hasCancellation;
        private final Map<String, Set<String>> artifactUrls = new LinkedHashMap<>();
        private final Map<String, Set<String>> artifactFileIds = new LinkedHashMap<>();
        private final List<Map<String, Object>> artifactUploadErrors = new ArrayList<>();
    }
}
