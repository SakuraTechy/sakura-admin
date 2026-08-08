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
import cn.dev33.satoken.exception.NotWebContextException;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.req.AutomationUiSceneExecReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;
import top.continew.admin.test.model.exception.TestPlanDispatchException;
import top.continew.admin.test.model.query.TestPlanQuery;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestPlanReq;
import top.continew.admin.test.model.req.TestPlanSceneRelationReq;
import top.continew.admin.test.model.resp.TestPlanDetailResp;
import top.continew.admin.test.model.resp.TestPlanResp;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.service.TestPlanService;
import top.continew.admin.test.service.TestTimedTaskService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl extends BaseServiceImpl<TestPlanMapper, TestPlanDO, TestPlanResp, TestPlanDetailResp, TestPlanQuery, TestPlanReq> implements TestPlanService {

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final AutomationUiExecutionRecordService executionRecordService;
    private final AutomationUiSceneService automationUiSceneService;
    private final TestReportMapper testReportMapper;
    private final TestTimedTaskService testTimedTaskService;
    private final TestPlanExecutionDispatchService executionDispatchService;
    private final TestReportSceneSnapshotService reportSceneSnapshotService;
    private final TransactionTemplate transactionTemplate;

    @Override
    protected void beforeCreate(TestPlanReq req) {
        validateAndResolvePlanScope(req);
    }

    @Override
    protected void beforeUpdate(TestPlanReq req, Long id) {
        validateAndResolvePlanScope(req);
    }

    @Override
    public List<TestPlanDetailResp> selectByIds(List<Long> ids) {
        return BeanUtil.copyToList(baseMapper.selectBatchIds(ids), TestPlanDetailResp.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        testTimedTaskService.deleteByPlanIds(ids);
        baseMapper.update(null, Wrappers.<TestPlanDO>update().in("id", ids).set("del_flag", StatusTypeEnum.ABNORMAL));
        testReportMapper.update(null, Wrappers.<TestReportDO>update()
            .in("test_plan_id", ids)
            .set("del_flag", StatusTypeEnum.ABNORMAL));
    }

    @Override
    public boolean isExists(String name, Long projectId, Long id) {
        return baseMapper.lambdaQuery()
            .eq(TestPlanDO::getProjectId, projectId)
            .eq(TestPlanDO::getName, name)
            .eq(TestPlanDO::getDelFlag, StatusTypeEnum.NORMAL)
            .ne(id != null, TestPlanDO::getId, id)
            .exists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void relateScenes(Long id, TestPlanSceneRelationReq req) {
        TestPlanDO plan = baseMapper.selectById(id);
        CheckUtils.throwIfNull(plan, "测试计划不存在");
        LinkedHashSet<Long> merged = new LinkedHashSet<>(plan.getUiTestScene() == null
            ? List.of()
            : plan.getUiTestScene());
        merged.addAll(req.getSceneIds());
        List<Long> sceneIds = new ArrayList<>(merged);
        automationUiSceneMapper.lambdaUpdate()
            .in(AutomationUiSceneDO::getId, req.getSceneIds())
            .set(AutomationUiSceneDO::getReportId, null)
            .update();
        List<AutomationUiSceneDO> sceneList = automationUiSceneMapper.selectBatchIds(req.getSceneIds());
        for (AutomationUiSceneDO scene : sceneList) {
            List<Object> planIds = scene.getTestPlanId() == null
                ? new ArrayList<>()
                : new ArrayList<>(scene.getTestPlanId());
            planIds.removeIf(item -> Objects.equals(String.valueOf(item), String.valueOf(id)));
            planIds.add(id);
            scene.setTestPlanId(planIds);
            automationUiSceneMapper.updateTestPlanIds(scene.getId(), planIds);
        }
        updatePlanSceneStats(plan, sceneIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeScenes(Long id, TestPlanSceneRelationReq req) {
        TestPlanDO plan = baseMapper.selectById(id);
        CheckUtils.throwIfNull(plan, "测试计划不存在");
        List<Long> sceneIds = new ArrayList<>(plan.getUiTestScene() == null ? List.of() : plan.getUiTestScene());
        sceneIds.removeIf(req.getSceneIds()::contains);
        List<AutomationUiSceneDO> sceneList = automationUiSceneMapper.selectBatchIds(req.getSceneIds());
        for (AutomationUiSceneDO scene : sceneList) {
            List<Object> planIds = scene.getTestPlanId() == null
                ? new ArrayList<>()
                : new ArrayList<>(scene.getTestPlanId());
            planIds.removeIf(item -> Objects.equals(String.valueOf(item), String.valueOf(id)));
            scene.setTestPlanId(planIds);
            automationUiSceneMapper.updateTestPlanIds(scene.getId(), planIds);
            executionRecordService.removeTestPlanRecords(scene.getId(), String.valueOf(id));
        }
        updatePlanSceneStats(plan, sceneIds);
    }

    @Override
    public TestPlanExecuteResp execute(Long id, TestPlanExecuteReq req) {
        TestPlanDO plan = baseMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("测试计划不存在");
        }
        if (!StatusTypeEnum.NORMAL.equals(plan.getDelFlag())) {
            throw new BusinessException("测试计划已删除");
        }
        reconcilePlanSceneReferences(plan);
        CheckUtils.throwIf(plan.getUiTestScene() == null || plan.getUiTestScene().isEmpty(), "测试计划未关联 UI 场景");
        List<Long> executionSceneIds = resolveExecutionSceneIds(plan, req.getSceneIds());
        List<AutomationUiSceneDO> executionScenes = reportSceneSnapshotService.loadAndValidate(plan.getProjectId(), plan
            .getVersionId(), executionSceneIds);
        Long versionId = reportSceneSnapshotService.resolveVersionId(plan.getProjectId(), plan
            .getVersionId(), executionScenes);
        if (!Objects.equals(plan.getVersionId(), versionId)) {
            plan.setVersionId(versionId);
            baseMapper.updateById(plan);
        }

        TestExecutionEngineEnum engine = req.getExecutionEngine() == null
            ? TestExecutionEngineEnum.SELENIUM
            : req.getExecutionEngine();
        CheckUtils.throwIfNull(req.getProjectEnvironmentId(), "项目环境 ID 不能为空");
        if (TestExecutionEngineEnum.SELENIUM.equals(engine)) {
            CheckUtils.throwIfNull(req.getAutomationEnvironmentId(), "Selenium 执行的自动化环境 ID 不能为空");
        }

        TestReportDO report = new TestReportDO();
        AutomationUiSceneDO firstScene = executionScenes.get(0);
        report.setProjectId(plan.getProjectId());
        report.setVersionId(versionId);
        report.setProjectName(plan.getProjectName());
        report.setVersionName(firstScene == null ? null : firstScene.getVersionName());
        report.setTestPlanId(plan.getId());
        report.setTestPlanName(plan.getName());
        report.setTriggerMode(StringUtils.defaultIfBlank(req.getTriggerMode(), "MANUAL"));
        report.setExecuteMode("PLAN");
        report.setReportType(engine.name());
        report.setStatus("RUNNING");
        report.setStartedAt(LocalDateTime.now());
        report.setName(StringUtils.abbreviate(plan.getName() + "_" + reportTypeLabel(engine) + "_" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")), 128));
        report.setProjectConfig(plan.getProjectConfig());
        report.setAutomationConfig(plan.getAutomationConfig());
        report.setRuntimeEnvironment(buildRuntimeEnvironment(req, engine, executionSceneIds));
        report.setRunTime(0L);
        transactionTemplate.executeWithoutResult(status -> {
            testReportMapper.insert(report);
            reportSceneSnapshotService.saveSnapshot(report, executionScenes);
            automationUiSceneMapper.lambdaUpdate()
                .in(AutomationUiSceneDO::getId, executionSceneIds)
                .set(AutomationUiSceneDO::getReportId, report.getId())
                .update();
            plan.setStatus("RUNNING");
            baseMapper.updateById(plan);
        });
        try {
            return switch (engine) {
                case SELENIUM -> executeSelenium(plan, report, req, executionSceneIds);
                case PLAYWRIGHT_RUNNER, CHROME_DEVTOOLS_PROTOCOL ->
                    executePlaywrightPlan(plan, report, req, engine, executionSceneIds);
            };
        } catch (RuntimeException e) {
            report.setStatus("FAILED");
            report.setFinishedAt(LocalDateTime.now());
            Map<String, Object> runtime = new LinkedHashMap<>(report.getRuntimeEnvironment());
            runtime.put("dispatchError", e.getMessage());
            report.setRuntimeEnvironment(runtime);
            testReportMapper.updateById(report);
            plan.setStatus("COMPLETED");
            baseMapper.updateById(plan);
            throw new TestPlanDispatchException(report.getId(), e);
        }
    }

    @Override
    public void cancelExecution(Long id, Long reportId) {
        TestReportDO report = testReportMapper.selectById(reportId);
        CheckUtils.throwIfNull(report, "测试报告不存在");
        CheckUtils.throwIf(!Objects.equals(report.getTestPlanId(), id), "测试报告不属于当前测试计划");
        CheckUtils.throwIf(TestExecutionEngineEnum.SELENIUM.name()
            .equalsIgnoreCase(report.getReportType()), "Selenium/Jenkins 执行请在 Jenkins 中取消");
        // 报告聚合状态可能先于 Runner 进程进入终态，取消请求仍需下发到调度器和进程树。
        executionDispatchService.cancel(String.valueOf(id), String.valueOf(reportId));
    }

    private TestPlanExecuteResp executeSelenium(TestPlanDO plan,
                                                TestReportDO report,
                                                TestPlanExecuteReq req,
                                                List<Long> executionSceneIds) {
        AutomationUiSceneExecReq execReq = new AutomationUiSceneExecReq();
        execReq.setSceneIds(executionSceneIds);
        execReq.setProjectEnvironmentId(req.getProjectEnvironmentId());
        execReq.setAutomationEnvironmentId(req.getAutomationEnvironmentId());
        execReq.setExecuteName(req.getExecuteName());
        execReq.setExecuteEmail(req.getExecuteEmail());
        execReq.setTestPlanId(String.valueOf(plan.getId()));
        execReq.setTestReportId(String.valueOf(report.getId()));
        AutomationUiSceneExecResp seleniumResp = automationUiSceneService.exec(execReq);
        if (seleniumResp != null) {
            String buildNumber = seleniumResp.getBuildNumber() == null
                ? null
                : String.valueOf(seleniumResp.getBuildNumber());
            report.setBuildNumber(buildNumber);
            report.setConsoleUrl(seleniumResp.getConsoleUrl());
            report.setReportUrl(seleniumResp.getTestReportUrl());
            report.setName(buildNumber != null
                ? StringUtils.abbreviate(plan.getName() + "_Selenium自动化报告_" + buildNumber, 128)
                : report.getName());
            testReportMapper.updateById(report);
        }
        TestPlanExecuteResp resp = baseExecuteResp(report, TestExecutionEngineEnum.SELENIUM);
        if (seleniumResp != null) {
            resp.setBuildNumber(seleniumResp.getBuildNumber());
            resp.setConsoleUrl(seleniumResp.getConsoleUrl());
            resp.setTestReportUrl(seleniumResp.getTestReportUrl());
        }
        return resp;
    }

    private TestPlanExecuteResp executePlaywrightPlan(TestPlanDO plan,
                                                      TestReportDO report,
                                                      TestPlanExecuteReq req,
                                                      TestExecutionEngineEnum engine,
                                                      List<Long> executionSceneIds) {
        List<TestPlanExecuteResp.SceneExecution> manifest = executionDispatchService
            .initialize(plan, executionSceneIds, String.valueOf(report.getId()), engine, req);
        if (TestExecutionEngineEnum.PLAYWRIGHT_RUNNER.equals(engine)) {
            executionDispatchService.dispatchRunner(plan, String.valueOf(report
                .getId()), req, manifest, currentTokenValue());
        }
        TestPlanExecuteResp resp = baseExecuteResp(report, engine);
        resp.setSceneExecutions(manifest);
        if (manifest.stream().allMatch(item -> item.getCaseIds() == null || item.getCaseIds().isEmpty())) {
            resp.setStatus("FAILED");
        }
        return resp;
    }

    private String currentTokenValue() {
        try {
            return StpUtil.getTokenValue();
        } catch (SaTokenContextException | NotWebContextException e) {
            return null;
        }
    }

    private TestPlanExecuteResp baseExecuteResp(TestReportDO report, TestExecutionEngineEnum engine) {
        TestPlanExecuteResp resp = new TestPlanExecuteResp();
        resp.setTestReportId(String.valueOf(report.getId()));
        resp.setReportType(engine.name());
        resp.setDispatchMode(engine.getDispatchMode());
        resp.setStatus("RUNNING");
        return resp;
    }

    private Map<String, Object> buildRuntimeEnvironment(TestPlanExecuteReq req,
                                                        TestExecutionEngineEnum engine,
                                                        List<Long> executionSceneIds) {
        Map<String, Object> runtimeEnvironment = new LinkedHashMap<>();
        runtimeEnvironment.put("projectEnvironmentId", req.getProjectEnvironmentId());
        runtimeEnvironment.put("automationEnvironmentId", req.getAutomationEnvironmentId());
        runtimeEnvironment.put("executionEngine", engine.name());
        runtimeEnvironment.put("runnerOptions", req.getRunnerOptions());
        runtimeEnvironment.put("cdpOptions", req.getCdpOptions());
        runtimeEnvironment.put("executeName", req.getExecuteName());
        runtimeEnvironment.put("executeEmail", req.getExecuteEmail());
        runtimeEnvironment.put("executionSceneIds", executionSceneIds);
        runtimeEnvironment.put("startedAtEpochMs", System.currentTimeMillis());
        return runtimeEnvironment;
    }

    /**
     * 请求子集只决定本次执行范围，顺序始终以计划关联顺序为准。
     */
    private List<Long> resolveExecutionSceneIds(TestPlanDO plan, List<Long> requestedSceneIds) {
        List<Long> planSceneIds = plan.getUiTestScene() == null ? List.of() : plan.getUiTestScene();
        List<Long> executionSceneIds;
        if (requestedSceneIds == null) {
            executionSceneIds = new ArrayList<>(planSceneIds);
        } else {
            ensureCondition(requestedSceneIds.isEmpty(), "执行场景不能为空");
            ensureCondition(requestedSceneIds.stream().anyMatch(Objects::isNull), "执行场景 ID 不能为空");
            LinkedHashSet<Long> requestedSet = new LinkedHashSet<>(requestedSceneIds);
            ensureCondition(requestedSet.size() != requestedSceneIds.size(), "执行场景不能重复");
            ensureCondition(!planSceneIds.containsAll(requestedSet), "执行场景不属于当前测试计划");
            executionSceneIds = planSceneIds.stream().filter(requestedSet::contains).toList();
        }
        ensureCondition(executionSceneIds.isEmpty(), "测试计划没有可执行的关联场景");
        return executionSceneIds;
    }

    private void reconcilePlanSceneReferences(TestPlanDO plan) {
        List<Long> configuredSceneIds = plan.getUiTestScene() == null
            ? List.of()
            : plan.getUiTestScene().stream().filter(Objects::nonNull).distinct().toList();
        List<AutomationUiSceneDO> existingScenes = configuredSceneIds.isEmpty()
            ? List.of()
            : automationUiSceneMapper.selectBatchIds(configuredSceneIds);
        List<Long> existingSceneIds = existingScenes.stream().map(AutomationUiSceneDO::getId).toList();
        List<Long> validSceneIds = configuredSceneIds.stream().filter(existingSceneIds::contains).toList();
        if (Objects.equals(plan.getUiTestScene(), validSceneIds)) {
            return;
        }
        plan.setUiTestScene(validSceneIds);
        plan.setSceneCount(validSceneIds.size());
        if (validSceneIds.isEmpty()) {
            plan.setVersionId(null);
        }
        baseMapper.updateById(plan);
    }

    private void validateAndResolvePlanScope(TestPlanReq req) {
        List<AutomationUiSceneDO> scenes = reportSceneSnapshotService.loadAndValidate(req.getProjectId(), req
            .getVersionId(), req.getUiTestScene());
        req.setVersionId(reportSceneSnapshotService.resolveVersionId(req.getProjectId(), req.getVersionId(), scenes));
    }

    private void ensureCondition(boolean condition, String message) {
        if (condition) {
            throw new BusinessException(message);
        }
    }

    private String reportTypeLabel(TestExecutionEngineEnum engine) {
        return switch (engine) {
            case SELENIUM -> "Selenium自动化报告";
            case PLAYWRIGHT_RUNNER -> "PlaywrightRunner自动化报告";
            case CHROME_DEVTOOLS_PROTOCOL -> "ChromeDevToolsProtocol自动化报告";
        };
    }

    private void updatePlanSceneStats(TestPlanDO plan, List<Long> sceneIds) {
        List<AutomationUiSceneDO> scenes = reportSceneSnapshotService.loadAndValidate(plan.getProjectId(), plan
            .getVersionId(), sceneIds);
        plan.setVersionId(reportSceneSnapshotService.resolveVersionId(plan.getProjectId(), plan
            .getVersionId(), scenes));
        int sceneCount = scenes.size();
        int executedCount = 0;
        int passedCount = 0;
        for (AutomationUiSceneDO scene : scenes) {
            Map<String, Object> planRecord = resolvePlanRecord(scene, plan.getId());
            if (planRecord == null) {
                continue;
            }
            String executeStatus = String.valueOf(planRecord.get("executeStatus"));
            String executeResult = String.valueOf(planRecord.get("executeResult"));
            if (executeStatus != null && !executeStatus.isBlank()) {
                executedCount++;
            }
            if (top.continew.admin.automation.util.AutomationUiSceneStatusCodes.isPassedResult(executeResult)) {
                passedCount++;
            }
        }
        plan.setUiTestScene(sceneIds);
        plan.setSceneCount(sceneCount);
        plan.setExecutedCount(executedCount);
        plan.setPassedCount(passedCount);
        plan.setTestProgress(sceneCount == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(executedCount * 100.0 / sceneCount).setScale(2, RoundingMode.HALF_UP));
        baseMapper.updateById(plan);
    }

    private Map<String, Object> resolvePlanRecord(AutomationUiSceneDO scene, Long planId) {
        if (scene == null || planId == null || scene.getTestRecord() == null) {
            return null;
        }
        for (Object item : scene.getTestRecord()) {
            if (item instanceof Map<?, ?> map && Objects.equals(String.valueOf(map.get("testPlanId")), String
                .valueOf(planId))) {
                @SuppressWarnings("unchecked") Map<String, Object> target = (Map<String, Object>)map;
                return target;
            }
        }
        return null;
    }
}
