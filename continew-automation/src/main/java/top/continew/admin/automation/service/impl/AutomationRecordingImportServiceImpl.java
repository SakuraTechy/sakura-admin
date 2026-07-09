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
import top.continew.admin.automation.model.req.recording.AutomationRecordingImportReq;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedCaseReq;
import top.continew.admin.automation.model.req.recording.RecordingSceneReq;
import top.continew.admin.automation.model.resp.recording.AutomationRecordingImportResp;
import top.continew.admin.automation.service.AutomationRecordingImportService;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.common.enums.StatusTypeEnum;
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
    private static final DateTimeFormatter RECORDING_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final AutomationUiSceneService automationUiSceneService;
    private final PlaywrightRecordingAssembler playwrightRecordingAssembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationRecordingImportResp importRecording(AutomationRecordingImportReq req) {
        if (!MODE_CREATE_SCENE.equals(req.getMode())) {
            throw new BusinessException("录制导入失败：MVP 阶段仅支持 createScene，当前 mode=" + req.getMode());
        }
        RecordingSceneReq sceneReq = req.getScene();
        PlaywrightRecordedCaseReq recordedCase = req.getRecordedCase();
        CheckUtils.throwIf(recordedCase.getSteps() == null || recordedCase.getSteps().isEmpty(), "录制导入失败：录制步骤不能为空");
        CheckUtils.throwIf(automationUiSceneService
            .isExists(null, sceneReq.getProjectId(), sceneReq.getVersionId(), sceneReq.getSceneId()), "录制导入失败：场景ID已存在，sceneId={}", sceneReq
                .getSceneId());

        String recordingId = newRecordingId();
        PlaywrightRecordingAssembler.RecordingImportContext context = new PlaywrightRecordingAssembler
            .RecordingImportContext(recordingId, Boolean.TRUE.equals(req.getPersistScreenshots()), Boolean.TRUE
                .equals(req.getKeepRawScreenshotInStep()));
        CaseDO caseDO = playwrightRecordingAssembler.toCase(recordedCase, context);

        AutomationUiSceneDO scene = buildScene(sceneReq, caseDO);
        automationUiSceneMapper.insert(scene);
        return new AutomationRecordingImportResp(scene.getId(), scene.getSceneId(), caseDO
            .getId(), recordingId, recordedCase.getSteps().size(), MODE_CREATE_SCENE);
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
        scene.setStatus(StatusTypeEnum.NORMAL);
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
