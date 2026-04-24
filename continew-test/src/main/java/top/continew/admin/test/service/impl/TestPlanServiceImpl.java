package top.continew.admin.test.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.req.AutomationUiSceneExecReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.query.TestPlanQuery;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestPlanReq;
import top.continew.admin.test.model.req.TestPlanSceneRelationReq;
import top.continew.admin.test.model.resp.TestPlanDetailResp;
import top.continew.admin.test.model.resp.TestPlanResp;
import top.continew.admin.test.service.TestPlanService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AutomationUiSceneService automationUiSceneService;
    private final TestReportMapper testReportMapper;
    private final TestTimedTaskMapper testTimedTaskMapper;

    @Override
    public List<TestPlanDetailResp> selectByIds(List<Long> ids) {
        return BeanUtil.copyToList(baseMapper.selectBatchIds(ids), TestPlanDetailResp.class);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        ids.forEach(id -> {
            baseMapper.lambdaUpdate().eq(TestPlanDO::getId, id).set(TestPlanDO::getDelFlag, StatusTypeEnum.ABNORMAL).update();
            testReportMapper.lambdaUpdate().eq(TestReportDO::getTestPlanId, id).set(TestReportDO::getDelFlag, StatusTypeEnum.ABNORMAL).update();
            testTimedTaskMapper.lambdaUpdate().eq(top.continew.admin.test.model.entity.TestTimedTaskDO::getTestPlanId, id)
                .set(top.continew.admin.test.model.entity.TestTimedTaskDO::getDelFlag, StatusTypeEnum.ABNORMAL).update();
        });
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
    public void relateScenes(Long id, TestPlanSceneRelationReq req) {
        TestPlanDO plan = baseMapper.selectById(id);
        CheckUtils.throwIfNull(plan, "测试计划不存在");
        LinkedHashSet<Long> merged = new LinkedHashSet<>(plan.getUiTestScene() == null ? List.of() : plan.getUiTestScene());
        merged.addAll(req.getSceneIds());
        List<Long> sceneIds = new ArrayList<>(merged);
        automationUiSceneMapper.lambdaUpdate()
            .in(AutomationUiSceneDO::getId, req.getSceneIds())
            .set(AutomationUiSceneDO::getReportId, null)
            .update();
        List<AutomationUiSceneDO> sceneList = automationUiSceneMapper.selectBatchIds(req.getSceneIds());
        for (AutomationUiSceneDO scene : sceneList) {
            List<Object> planIds = scene.getTestPlanId() == null ? new ArrayList<>() : new ArrayList<>(scene.getTestPlanId());
            if (!planIds.contains(id)) {
                planIds.add(id);
            }
            scene.setTestPlanId(planIds);
            automationUiSceneMapper.updateById(scene);
        }
        updatePlanSceneStats(plan, sceneIds);
    }

    @Override
    public void removeScenes(Long id, TestPlanSceneRelationReq req) {
        TestPlanDO plan = baseMapper.selectById(id);
        CheckUtils.throwIfNull(plan, "测试计划不存在");
        List<Long> sceneIds = new ArrayList<>(plan.getUiTestScene() == null ? List.of() : plan.getUiTestScene());
        sceneIds.removeIf(req.getSceneIds()::contains);
        List<AutomationUiSceneDO> sceneList = automationUiSceneMapper.selectBatchIds(req.getSceneIds());
        for (AutomationUiSceneDO scene : sceneList) {
            List<Object> planIds = scene.getTestPlanId() == null ? new ArrayList<>() : new ArrayList<>(scene.getTestPlanId());
            planIds.removeIf(item -> Objects.equals(String.valueOf(item), String.valueOf(id)));
            scene.setTestPlanId(planIds);
            automationUiSceneMapper.updateById(scene);
        }
        updatePlanSceneStats(plan, sceneIds);
    }

    @Override
    public AutomationUiSceneExecResp execute(Long id, TestPlanExecuteReq req) {
        TestPlanDO plan = baseMapper.selectById(id);
        CheckUtils.throwIfNull(plan, "测试计划不存在");
        CheckUtils.throwIf(plan.getUiTestScene() == null || plan.getUiTestScene().isEmpty(), "测试计划未关联 UI 场景");

        TestReportDO report = new TestReportDO();
        AutomationUiSceneDO firstScene = automationUiSceneMapper.selectById(plan.getUiTestScene().get(0));
        report.setProjectId(plan.getProjectId());
        report.setProjectName(plan.getProjectName());
        report.setVersionName(firstScene == null ? null : firstScene.getVersionName());
        report.setTestPlanId(plan.getId());
        report.setTestPlanName(plan.getName());
        report.setName(plan.getName() + "-执行报告");
        report.setTriggerMode("MANUAL");
        report.setExecuteMode("PLAN");
        report.setStatus("RUNNING");
        report.setProjectConfig(plan.getProjectConfig());
        report.setAutomationConfig(plan.getAutomationConfig());
        report.setRuntimeEnvironment(buildRuntimeEnvironment(req));
        testReportMapper.insert(report);

        automationUiSceneMapper.lambdaUpdate()
            .in(AutomationUiSceneDO::getId, plan.getUiTestScene())
            .set(AutomationUiSceneDO::getReportId, report.getId())
            .update();

        AutomationUiSceneExecReq execReq = new AutomationUiSceneExecReq();
        execReq.setSceneIds(plan.getUiTestScene());
        execReq.setProjectEnvironmentId(req.getProjectEnvironmentId());
        execReq.setAutomationEnvironmentId(req.getAutomationEnvironmentId());
        execReq.setExecuteName(req.getExecuteName());
        execReq.setExecuteEmail(req.getExecuteEmail());
        execReq.setTestPlanId(String.valueOf(plan.getId()));
        execReq.setTestReportId(String.valueOf(report.getId()));
        AutomationUiSceneExecResp resp = automationUiSceneService.exec(execReq);

        if (resp != null) {
            report.setBuildNumber(resp.getBuildNumber() == null ? null : String.valueOf(resp.getBuildNumber()));
            report.setConsoleUrl(resp.getConsoleUrl());
            report.setReportUrl(resp.getTestReportUrl());
            testReportMapper.updateById(report);
        }
        plan.setStatus("RUNNING");
        baseMapper.updateById(plan);
        return resp;
    }

    private Map<String, Object> buildRuntimeEnvironment(TestPlanExecuteReq req) {
        Map<String, Object> runtimeEnvironment = new LinkedHashMap<>();
        runtimeEnvironment.put("projectEnvironmentId", req.getProjectEnvironmentId());
        runtimeEnvironment.put("automationEnvironmentId", req.getAutomationEnvironmentId());
        runtimeEnvironment.put("executeName", req.getExecuteName());
        runtimeEnvironment.put("executeEmail", req.getExecuteEmail());
        return runtimeEnvironment;
    }

    private void updatePlanSceneStats(TestPlanDO plan, List<Long> sceneIds) {
        List<AutomationUiSceneDO> scenes = sceneIds.isEmpty() ? List.of() : automationUiSceneMapper.selectBatchIds(sceneIds);
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
            if ("PASSED".equalsIgnoreCase(executeResult) || "全部通过".equals(executeResult)) {
                passedCount++;
            }
        }
        plan.setUiTestScene(sceneIds);
        plan.setSceneCount(sceneCount);
        plan.setExecutedCount(executedCount);
        plan.setPassedCount(passedCount);
        plan.setTestProgress(sceneCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(executedCount * 100.0 / sceneCount).setScale(2, RoundingMode.HALF_UP));
        baseMapper.updateById(plan);
    }

    private Map<String, Object> resolvePlanRecord(AutomationUiSceneDO scene, Long planId) {
        if (scene == null || planId == null || scene.getTestRecord() == null) {
            return null;
        }
        for (Object item : scene.getTestRecord()) {
            if (item instanceof Map<?, ?> map && Objects.equals(String.valueOf(map.get("testPlanId")), String.valueOf(planId))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> target = (Map<String, Object>) map;
                return target;
            }
        }
        return null;
    }
}
