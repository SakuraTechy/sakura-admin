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
import java.net.URI;
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
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.recording.AutomationRecordingImportReq;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedCaseReq;
import top.continew.admin.automation.model.req.recording.RecordingSceneReq;
import top.continew.admin.automation.model.resp.recording.AutomationRecordingImportResp;
import top.continew.admin.automation.service.AutomationRecordingImportService;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
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
    private final AutomationPlaywrightJobMapper automationPlaywrightJobMapper;
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
        String caseId = nextCaseId(scene, caseList, caseIdPrefix);
        PlaywrightRecordingAssembler.RecordingImportContext context = newContext(req, recordingId, scene
            .getProjectId(), scene.getProjectName(), scene.getVersionName(), scene.getSceneId());
        CaseDO caseDO = playwrightRecordingAssembler.toCase(recordedCase, caseId, nextOrder, context);
        assignNewStepIds(scene, caseDO, new ArrayList<>(), mutableStepList(caseDO), null);

        caseList.add(insertIndex, caseDO);
        normalizeOrderAndPid(caseList);
        refreshSceneCounts(scene, caseList);
        updateDefinition(scene, req);
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
        synchronizeReplacementStartUrl(replacement);
        preserveCaseIdentity(targetCase, replacement);
        assignNewStepIds(scene, replacement, targetCase.getStepList() == null
            ? new ArrayList<>()
            : targetCase.getStepList(), mutableStepList(replacement), null);
        int targetIndex = caseList.indexOf(targetCase);
        caseList.set(targetIndex, replacement);
        normalizeOrderAndPid(caseList);
        refreshSceneCounts(scene, caseList);
        updateDefinition(scene, req);
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
        synchronizeReplacementStartUrl(targetCase);
        assignNewStepIds(scene, targetCase, new ArrayList<>(), mutableStepList(targetCase), null);
        // 兼容模式只替换步骤，保留原用例名称、备注、顺序和状态，避免旧客户端覆盖中台业务元信息。
        normalizeOrderAndPid(mutableCaseList(scene));
        refreshSceneCounts(scene, mutableCaseList(scene));
        updateDefinition(scene, req);
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
        List<StepDO> additions = playwrightRecordingAssembler.toSteps(recordedCase, targetCase.getId(), context);
        assignNewStepIds(scene, targetCase, stepList, additions, null);
        stepList.addAll(insertIndex, additions);
        normalizeOrderAndPid(mutableCaseList(scene));
        refreshSceneCounts(scene, mutableCaseList(scene));
        updateDefinition(scene, req);
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
        List<StepDO> replacements = playwrightRecordingAssembler.toSteps(recordedCase, targetCase.getId(), context);
        assignNewStepIds(scene, targetCase, stepList, replacements, req.getTargetStepId());
        stepList.addAll(targetIndex, replacements);
        normalizeOrderAndPid(mutableCaseList(scene));
        refreshSceneCounts(scene, mutableCaseList(scene));
        updateDefinition(scene, req);
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
        CheckUtils.throwIf(req
            .getExpectedDefinitionVersion() == null, "录制导入失败：{} 模式 expectedDefinitionVersion 不能为空", req.getMode());
        // 先锁定，再通过实体映射读取 JSON caseList；自定义锁查询不会应用 JacksonTypeHandler。
        CheckUtils.throwIf(automationUiSceneMapper.selectByIdForUpdate(req
            .getTargetSceneDbId()) == null, "录制导入失败：目标场景不存在，targetSceneDbId={}", req.getTargetSceneDbId());
        AutomationUiSceneDO scene = automationUiSceneMapper.selectById(req.getTargetSceneDbId());
        CheckUtils.throwIf(!java.util.Objects.equals(scene.getDefinitionVersion() == null
            ? 0L
            : scene.getDefinitionVersion(), req.getExpectedDefinitionVersion()), "场景定义已被其他操作修改，请刷新后重试");
        if (AutomationUiSceneStatusCodes.STATUS_RUNNING.equals(AutomationUiSceneStatusCodes.normalizeStatus(scene
            .getExecuteStatus())) || automationPlaywrightJobMapper.countActiveBySceneKeys(String.valueOf(scene
                .getId()), scene.getSceneId()) > 0) {
            throw new BusinessException("场景正在执行，暂不能修改用例树");
        }
        syncNodeIdSequences(scene);
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
        // replaceCase 只替换可执行步骤，原用例名称仍是中台业务标识，不能被录制端默认名称覆盖。
        replacement.setName(targetCase.getName());
        replacement.setOrder(targetCase.getOrder());
        replacement.setCopyId(targetCase.getCopyId());
        replacement.setSortType(targetCase.getSortType());
        replacement.setItemOrder(targetCase.getItemOrder());
        replacement.setStatus(targetCase.getStatus());
        // replaceCase replaces executable content; remarks remain unless explicitly supported by the form.
        replacement.setRemark(targetCase.getRemark());
    }

    private void synchronizeReplacementStartUrl(CaseDO targetCase) {
        String firstRecordedPageUrl = firstRecordedPageUrl(targetCase.getStepList());
        if (!hasText(firstRecordedPageUrl)) {
            return;
        }
        if (targetCase.getExecutionConfig() == null) {
            targetCase.setExecutionConfig(new CaseExecutionConfigDO());
        }
        // 完整替换后，第一条录制操作所在页面才是新用例的实际回放起点。
        // 在导入时固化为用例级配置，执行阶段不再从步骤反推；原始 playwright_step 保持不变。
        targetCase.getExecutionConfig().setStartUrl(firstRecordedPageUrl);
        for (StepDO step : targetCase.getStepList()) {
            if (step == null || step.getConfigList() == null) {
                continue;
            }
            for (StepDO.Config config : step.getConfigList()) {
                if (config != null && "start_url".equals(config.getParamsName())) {
                    config.setParamsValue(firstRecordedPageUrl);
                }
            }
        }
    }

    private String firstRecordedPageUrl(List<StepDO> steps) {
        if (steps == null) {
            return null;
        }
        for (StepDO step : steps) {
            if (step == null || step.getConfigList() == null) {
                continue;
            }
            for (StepDO.Config config : step.getConfigList()) {
                if (config != null && "url".equals(config.getParamsName()) && isHttpUrl(config.getParamsValue())) {
                    return config.getParamsValue().trim();
                }
            }
        }
        return null;
    }

    private boolean isHttpUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri
                .getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
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

    /** 录制结构变更只整理 order/pid，绝不因位置变化重写既有业务 ID。 */
    private void normalizeOrderAndPid(List<CaseDO> caseList) {
        for (int caseIndex = 0; caseIndex < caseList.size(); caseIndex++) {
            CaseDO caseDO = caseList.get(caseIndex);
            caseDO.setOrder(caseIndex + 1);
            List<StepDO> steps = mutableStepList(caseDO);
            for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
                StepDO stepDO = steps.get(stepIndex);
                stepDO.setOrder(stepIndex + 1);
                stepDO.setPid(caseDO.getId());
            }
        }
    }

    private String nextCaseId(AutomationUiSceneDO scene, List<CaseDO> caseList, String prefix) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        long max = 0;
        for (CaseDO item : caseList) {
            if (item == null || item.getId() == null)
                continue;
            ids.add(item.getId());
            if (item.getId().startsWith(prefix)) {
                String suffix = item.getId().substring(prefix.length());
                if (suffix.matches("\\d+"))
                    max = Math.max(max, Long.parseLong(suffix));
            }
        }
        Long stored = automationUiSceneMapper.selectNodeIdSequence(scene.getId(), "CASE", prefix);
        max = Math.max(max, stored == null ? 0L : stored);
        do {
            max++;
        } while (ids.contains(prefix + String.format("%03d", max)));
        automationUiSceneMapper.upsertNodeIdSequence(scene.getId(), "CASE", prefix, max);
        return prefix + String.format("%03d", max);
    }

    /** append 仅为新增步骤分配 ID；replaceStep 的首步保留被替换步骤 ID。 */
    private void assignNewStepIds(AutomationUiSceneDO scene,
                                  CaseDO targetCase,
                                  List<StepDO> existing,
                                  List<StepDO> additions,
                                  String preservedFirstId) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (StepDO item : existing)
            if (item != null && item.getId() != null)
                ids.add(item.getId());
        for (int index = 0; index < additions.size(); index++) {
            StepDO step = additions.get(index);
            if (index == 0 && preservedFirstId != null) {
                step.setId(preservedFirstId);
                ids.add(preservedFirstId);
                continue;
            }
            String prefix = step.getId() == null ? "CASE_STEP_" : step.getId().replaceFirst("\\d+$", "");
            if (prefix.isBlank())
                prefix = "CASE_STEP_";
            long next = nextStepSequence(scene.getId(), targetCase.getId(), ids, prefix);
            while (ids.contains(prefix + String.format("%03d", next)))
                next++;
            step.setId(prefix + String.format("%03d", next));
            ids.add(step.getId());
        }
    }

    private long nextStepSequence(Long sceneId, String caseId, java.util.Set<String> ids, String prefix) {
        long max = 0;
        for (String id : ids) {
            if (id != null && id.startsWith(prefix)) {
                String suffix = id.substring(prefix.length());
                if (suffix.matches("\\d+"))
                    max = Math.max(max, Long.parseLong(suffix));
            }
        }
        Long stored = automationUiSceneMapper.selectNodeIdSequence(sceneId, "STEP:" + caseId, prefix);
        max = Math.max(max, stored == null ? 0L : stored);
        return max + 1;
    }

    private void syncNodeIdSequences(AutomationUiSceneDO scene) {
        if (scene.getCaseList() == null)
            return;
        for (CaseDO caseDO : scene.getCaseList()) {
            if (caseDO == null)
                continue;
            syncNodeIdSequence(scene.getId(), "CASE", caseDO.getId());
            if (caseDO.getStepList() == null)
                continue;
            for (StepDO step : caseDO.getStepList())
                if (step != null)
                    syncNodeIdSequence(scene.getId(), "STEP:" + caseDO.getId(), step.getId());
        }
    }

    private void syncNodeIdSequence(Long sceneId, String scopeKey, String id) {
        if (id == null)
            return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*?)(\\d+)$").matcher(id);
        if (matcher.matches())
            automationUiSceneMapper.upsertNodeIdSequence(sceneId, scopeKey, matcher.group(1), Long.parseLong(matcher
                .group(2)));
    }

    /** 录制导入与树操作共用定义版本条件更新，禁止全实体 updateById 覆盖彼此结果。 */
    private void updateDefinition(AutomationUiSceneDO scene, AutomationRecordingImportReq req) {
        syncNodeIdSequences(scene);
        int updated = automationUiSceneMapper.updateDefinition(scene.getId(), req.getExpectedDefinitionVersion(), scene
            .getCaseList(), scene.getCaseTotal(), scene.getStepTotal());
        CheckUtils.throwIf(updated != 1, "场景定义已被其他操作修改，请刷新后重试");
        scene.setDefinitionVersion(req.getExpectedDefinitionVersion() + 1);
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
