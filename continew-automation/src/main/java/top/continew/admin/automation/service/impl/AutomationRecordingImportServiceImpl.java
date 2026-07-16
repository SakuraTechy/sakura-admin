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

import static top.continew.admin.automation.util.AutomationUiSceneStatusCodes.RESULT_NOT_EXECUTED;
import static top.continew.admin.automation.util.AutomationUiSceneStatusCodes.STATUS_NOT_STARTED;

import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.PlaywrightRecordingAssembler;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.recording.AutomationRecordingImportReq;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedCaseReq;
import top.continew.admin.automation.model.req.recording.RecordingSceneReq;
import top.continew.admin.automation.model.resp.recording.AutomationRecordingImportResp;
import top.continew.admin.automation.service.AutomationRecordingImportService;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.validation.CheckUtils;

/**
 * Playwright 录制导入业务实现。
 *
 * @author Codex
 */
@Service
@RequiredArgsConstructor
public class AutomationRecordingImportServiceImpl implements AutomationRecordingImportService {

    private static final String MODE_CREATE_SCENE = "createScene";
    private static final String MODE_APPEND_CASE = "appendCase";
    private static final String MODE_REPLACE_CASE = "replaceCase";
    private static final String MODE_APPEND_STEP = "appendStep";
    private static final String MODE_REPLACE_STEP = "replaceStep";
    /** Kept for clients deployed before the import modes were split by scope. */
    private static final String MODE_REPLACE_CASE_STEPS = "replaceCaseSteps";
    private static final DateTimeFormatter RECORDING_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final AutomationUiSceneService automationUiSceneService;
    private final PlaywrightRecordingAssembler playwrightRecordingAssembler;
    private final ProjectConfigMapper projectConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationRecordingImportResp importRecording(AutomationRecordingImportReq req) {
        String mode = req.getMode();
        checkModePermission(mode);
        PlaywrightRecordedCaseReq recordedCase = req.getRecordedCase();
        CheckUtils.throwIf(recordedCase.getSteps() == null || recordedCase.getSteps().isEmpty(), "录制导入失败：录制步骤不能为空");
        String recordingId = newRecordingId();

        return switch (mode) {
            case MODE_CREATE_SCENE -> createScene(req, recordedCase, recordingId);
            case MODE_APPEND_CASE -> appendCase(req, recordedCase, recordingId);
            case MODE_REPLACE_CASE -> replaceCase(req, recordedCase, recordingId);
            case MODE_APPEND_STEP -> appendStep(req, recordedCase, recordingId);
            case MODE_REPLACE_STEP -> replaceStep(req, recordedCase, recordingId);
            case MODE_REPLACE_CASE_STEPS -> replaceCaseSteps(req, recordedCase, recordingId);
            default -> throw new BusinessException("录制导入失败：不支持的 mode=" + mode);
        };
    }

    private AutomationRecordingImportResp createScene(AutomationRecordingImportReq req,
                                                      PlaywrightRecordedCaseReq recordedCase,
                                                      String recordingId) {
        RecordingSceneReq sceneReq = req.getScene();
        CheckUtils.throwIf(sceneReq == null, "录制导入失败：createScene 模式场景信息不能为空");
        CheckUtils.throwIf(automationUiSceneService.isExists(null, sceneReq.getProjectId(), sceneReq
            .getVersionId(), sceneReq.getSceneId()), "录制导入失败：场景ID已存在，sceneId={}", sceneReq.getSceneId());

        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, sceneReq
            .getProjectId(), sceneReq.getProjectName(), sceneReq.getVersionName(), sceneReq.getSceneId());
        CaseDO caseDO = playwrightRecordingAssembler.toCase(recordedCase, context);
        AutomationUiSceneDO scene = buildScene(sceneReq, caseDO);
        automationUiSceneMapper.insert(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), caseDO
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_CREATE_SCENE);
    }

    private AutomationRecordingImportResp appendCase(AutomationRecordingImportReq req,
                                                     PlaywrightRecordedCaseReq recordedCase,
                                                     String recordingId) {
        AutomationUiSceneDO scene = requireTargetScene(req);
        List<CaseDO> caseList = mutableCaseList(scene);
        RecordingAppendCasePositionResolver.normalizeOrder(caseList);
        int insertIndex = RecordingAppendCasePositionResolver.resolveIndex(caseList, req.getAppendPosition(), req
            .getAppendAfterCaseId());
        String caseIdPrefix = RecordingAppendCasePositionResolver.resolveCaseIdPrefix(caseList);
        int nextOrder = insertIndex + 1;
        String caseId = caseIdPrefix + String.format("%03d", nextOrder);
        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, scene
            .getProjectId(), scene.getProjectName(), scene.getVersionName(), scene.getSceneId());
        CaseDO caseDO = playwrightRecordingAssembler.toCase(recordedCase, caseId, nextOrder, context);

        caseList.add(insertIndex, caseDO);
        RecordingAppendCasePositionResolver.renumberCaseIds(caseList, caseIdPrefix);
        refreshSceneCounts(scene, caseList);
        automationUiSceneMapper.updateById(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), caseDO
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_APPEND_CASE);
    }

    private AutomationRecordingImportResp replaceCase(AutomationRecordingImportReq req,
                                                      PlaywrightRecordedCaseReq recordedCase,
                                                      String recordingId) {
        AutomationUiSceneDO scene = requireTargetScene(req);
        CheckUtils.throwIf(req.getTargetCaseId() == null || req.getTargetCaseId()
            .isBlank(), "录制导入失败：replaceCase 模式 targetCaseId 不能为空");
        List<CaseDO> caseList = mutableCaseList(scene);
        CaseDO targetCase = findCase(caseList, req.getTargetCaseId());
        CheckUtils.throwIf(targetCase == null, "录制导入失败：目标用例不存在，targetCaseId={}", req.getTargetCaseId());

        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, scene
            .getProjectId(), scene.getProjectName(), scene.getVersionName(), scene.getSceneId());
        CaseDO replacement = playwrightRecordingAssembler.toCase(recordedCase, targetCase.getId(), targetCase
            .getOrder(), context);
        preserveCaseIdentity(targetCase, replacement);
        int targetIndex = caseList.indexOf(targetCase);
        caseList.set(targetIndex, replacement);
        refreshSceneCounts(scene, caseList);
        automationUiSceneMapper.updateById(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), replacement
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_REPLACE_CASE);
    }

    private AutomationRecordingImportResp replaceCaseSteps(AutomationRecordingImportReq req,
                                                           PlaywrightRecordedCaseReq recordedCase,
                                                           String recordingId) {
        AutomationUiSceneDO scene = requireTargetScene(req);
        CaseDO targetCase = requireTargetCase(req, scene, MODE_REPLACE_CASE_STEPS);
        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, scene
            .getProjectId(), scene.getProjectName(), scene.getVersionName(), scene.getSceneId());
        targetCase.setStepList(playwrightRecordingAssembler.toSteps(recordedCase, targetCase.getId(), context));
        targetCase.setName(recordedCase.getName());
        refreshSceneCounts(scene, mutableCaseList(scene));
        automationUiSceneMapper.updateById(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), targetCase
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_REPLACE_CASE_STEPS);
    }

    private AutomationRecordingImportResp appendStep(AutomationRecordingImportReq req,
                                                     PlaywrightRecordedCaseReq recordedCase,
                                                     String recordingId) {
        AutomationUiSceneDO scene = requireTargetScene(req);
        CaseDO targetCase = requireTargetCase(req, scene, MODE_APPEND_STEP);
        List<StepDO> stepList = mutableStepList(targetCase);
        RecordingStepPositionResolver.normalizeOrder(stepList);
        int insertIndex = RecordingStepPositionResolver.resolveIndex(stepList, req.getStepAppendPosition(), req
            .getAppendAfterStepId());
        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, scene
            .getProjectId(), scene.getProjectName(), scene.getVersionName(), scene.getSceneId());
        stepList.addAll(insertIndex, playwrightRecordingAssembler.toSteps(recordedCase, targetCase.getId(), context));
        RecordingStepPositionResolver.renumberStepIds(stepList, targetCase.getId());
        refreshSceneCounts(scene, mutableCaseList(scene));
        automationUiSceneMapper.updateById(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), targetCase
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_APPEND_STEP);
    }

    private AutomationRecordingImportResp replaceStep(AutomationRecordingImportReq req,
                                                      PlaywrightRecordedCaseReq recordedCase,
                                                      String recordingId) {
        AutomationUiSceneDO scene = requireTargetScene(req);
        CaseDO targetCase = requireTargetCase(req, scene, MODE_REPLACE_STEP);
        CheckUtils.throwIf(req.getTargetStepId() == null || req.getTargetStepId()
            .isBlank(), "录制导入失败：replaceStep 模式 targetStepId 不能为空");
        List<StepDO> stepList = mutableStepList(targetCase);
        RecordingStepPositionResolver.normalizeOrder(stepList);
        int targetIndex = findStepIndex(stepList, req.getTargetStepId());
        CheckUtils.throwIf(targetIndex < 0, "录制导入失败：目标步骤不存在，targetStepId={}", req.getTargetStepId());
        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, scene
            .getProjectId(), scene.getProjectName(), scene.getVersionName(), scene.getSceneId());
        stepList.remove(targetIndex);
        stepList.addAll(targetIndex, playwrightRecordingAssembler.toSteps(recordedCase, targetCase.getId(), context));
        RecordingStepPositionResolver.renumberStepIds(stepList, targetCase.getId());
        refreshSceneCounts(scene, mutableCaseList(scene));
        automationUiSceneMapper.updateById(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), targetCase
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_REPLACE_STEP);
    }

    private AutomationUiSceneDO buildScene(RecordingSceneReq sceneReq, CaseDO caseDO) {
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setSceneId(sceneReq.getSceneId());
        scene.setName(sceneReq.getName());
        scene.setDescription(sceneReq.getDescription());
        scene.setProjectId(sceneReq.getProjectId());
        scene.setProjectName(sceneReq.getProjectName());
        scene.setVersionId(sceneReq.getVersionId());
        scene.setVersionName(sceneReq.getVersionName());
        scene.setModuleId(sceneReq.getModuleId());
        scene.setModulePath(sceneReq.getModulePath());
        scene.setLevel(sceneReq.getLevel());
        scene.setStatus(StatusTypeEnum.ENABLE);
        scene.setTags(sceneReq.getTags());
        scene.setCaseList(List.of(caseDO));
        scene.setDebugRecord(defaultDebugRecord());
        scene.setExecuteStatus(STATUS_NOT_STARTED);
        scene.setExecuteResult(RESULT_NOT_EXECUTED);
        scene.setCaseTotal(1);
        scene.setStepTotal(caseDO.getStepList() == null ? 0 : caseDO.getStepList().size());
        scene.setDelFlag(StatusTypeEnum.NORMAL);
        return scene;
    }

    private AutomationUiSceneDO requireTargetScene(AutomationRecordingImportReq req) {
        CheckUtils.throwIf(req.getTargetSceneDbId() == null, "录制导入失败：{} 模式 targetSceneDbId 不能为空", req.getMode());
        AutomationUiSceneDO scene = automationUiSceneMapper.selectById(req.getTargetSceneDbId());
        CheckUtils.throwIf(scene == null, "录制导入失败：目标场景不存在，targetSceneDbId={}", req.getTargetSceneDbId());
        return scene;
    }

    private List<CaseDO> mutableCaseList(AutomationUiSceneDO scene) {
        List<CaseDO> caseList = scene.getCaseList() == null ? new ArrayList<>() : new ArrayList<>(scene.getCaseList());
        scene.setCaseList(caseList);
        return caseList;
    }

    private CaseDO findCase(List<CaseDO> caseList, String caseId) {
        for (CaseDO caseDO : caseList) {
            if (caseDO != null && caseId.equals(caseDO.getId())) {
                return caseDO;
            }
        }
        return null;
    }

    private CaseDO requireTargetCase(AutomationRecordingImportReq req, AutomationUiSceneDO scene, String mode) {
        CheckUtils.throwIf(req.getTargetCaseId() == null || req.getTargetCaseId()
            .isBlank(), "录制导入失败：{} 模式 targetCaseId 不能为空", mode);
        CaseDO targetCase = findCase(mutableCaseList(scene), req.getTargetCaseId());
        CheckUtils.throwIf(targetCase == null, "录制导入失败：目标用例不存在，targetCaseId={}", req.getTargetCaseId());
        return targetCase;
    }

    private List<StepDO> mutableStepList(CaseDO targetCase) {
        List<StepDO> stepList = targetCase.getStepList() == null
            ? new ArrayList<>()
            : new ArrayList<>(targetCase.getStepList());
        targetCase.setStepList(stepList);
        return stepList;
    }

    private int findStepIndex(List<StepDO> stepList, String stepId) {
        for (int i = 0; i < stepList.size(); i++) {
            StepDO step = stepList.get(i);
            if (step != null && stepId.equals(step.getId())) {
                return i;
            }
        }
        return -1;
    }

    private void preserveCaseIdentity(CaseDO targetCase, CaseDO replacement) {
        replacement.setId(targetCase.getId());
        replacement.setOrder(targetCase.getOrder());
        replacement.setCopyId(targetCase.getCopyId());
        replacement.setSortType(targetCase.getSortType());
        replacement.setItemOrder(targetCase.getItemOrder());
        replacement.setStatus(targetCase.getStatus());
        // replaceCase replaces executable content; remarks remain unless explicitly supported by the form.
        replacement.setRemark(targetCase.getRemark());
    }

    private void refreshSceneCounts(AutomationUiSceneDO scene, List<CaseDO> caseList) {
        scene.setCaseTotal(caseList.size());
        int stepTotal = 0;
        for (CaseDO caseDO : caseList) {
            if (caseDO != null && caseDO.getStepList() != null) {
                stepTotal += caseDO.getStepList().size();
            }
        }
        scene.setStepTotal(stepTotal);
    }

    private void checkModePermission(String mode) {
        if (MODE_CREATE_SCENE.equals(mode)) {
            StpUtil.checkPermission("automation:automationUiScene:create");
            return;
        }
        if (MODE_APPEND_CASE.equals(mode) || MODE_REPLACE_CASE.equals(mode) || MODE_APPEND_STEP
            .equals(mode) || MODE_REPLACE_STEP.equals(mode) || MODE_REPLACE_CASE_STEPS.equals(mode)) {
            StpUtil.checkPermission("automation:automationUiScene:update");
            return;
        }
        throw new BusinessException("录制导入失败：不支持的 mode=" + mode);
    }

    private PlaywrightRecordingAssembler.RecordingImportContext newContext(AutomationRecordingImportReq req,
                                                                           String recordingId,
                                                                           Long projectId,
                                                                           String projectName,
                                                                           String versionName,
                                                                           String sceneId) {
        String projectShortName = resolveProjectShortName(projectId, projectName);
        return new PlaywrightRecordingAssembler.RecordingImportContext(recordingId, projectShortName, versionName, sceneId, Boolean.TRUE
            .equals(req.getPersistScreenshots()), Boolean.TRUE.equals(req.getKeepRawScreenshotInStep()));
    }

    private String resolveProjectShortName(Long projectId, String fallbackProjectName) {
        if (projectId != null) {
            ProjectConfigDO projectConfig = projectConfigMapper.selectById(projectId);
            if (projectConfig != null && hasText(projectConfig.getAbbreviate())) {
                return projectConfig.getAbbreviate();
            }
        }
        return fallbackProjectName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<Object> defaultDebugRecord() {
        List<Object> defaultDebugRecord = new ArrayList<>();
        Map<String, Object> defaultRecord = new HashMap<>(16);
        defaultRecord.put("sceneTotal", 0);
        defaultRecord.put("scenePass", 0);
        defaultRecord.put("sceneFail", 0);
        defaultRecord.put("sceneSkip", 0);
        defaultRecord.put("scenePassRate", "-");
        defaultRecord.put("caseTotal", 0);
        defaultRecord.put("casePass", 0);
        defaultRecord.put("caseFail", 0);
        defaultRecord.put("caseSkip", 0);
        defaultRecord.put("casePassRate", "0%");
        defaultRecord.put("stepTotal", 0);
        defaultRecord.put("stepPass", 0);
        defaultRecord.put("stepFail", 0);
        defaultRecord.put("stepSkip", 0);
        defaultRecord.put("stepPassRate", "0%");
        defaultRecord.put("executeName", "-");
        defaultRecord.put("executeStatus", STATUS_NOT_STARTED);
        defaultRecord.put("executeResult", RESULT_NOT_EXECUTED);
        defaultRecord.put("duration", "-");
        defaultDebugRecord.add(defaultRecord);
        return defaultDebugRecord;
    }

    private String newRecordingId() {
        String time = LocalDateTime.now().format(RECORDING_ID_TIME);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        return "rec-" + time + "-" + suffix;
    }
}
