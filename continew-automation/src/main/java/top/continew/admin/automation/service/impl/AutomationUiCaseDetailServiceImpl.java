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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.converter.AutomationRecordingActionResolver;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.CaseOriginDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.ui.AutomationUiCaseEditReq;
import top.continew.admin.automation.model.req.ui.AutomationUiCaseExecutionConfigReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepConfigEditReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepEditReq;
import top.continew.admin.automation.model.resp.ui.AutomationUiCaseExecutionConfigResp;
import top.continew.admin.automation.model.resp.ui.AutomationUiCaseDetailResp;
import top.continew.admin.automation.model.resp.ui.AutomationUiCaseOriginResp;
import top.continew.admin.automation.model.resp.ui.AutomationUiStepConfigResp;
import top.continew.admin.automation.model.resp.ui.AutomationUiStepDetailResp;
import top.continew.admin.automation.service.AutomationUiCaseDetailService;
import top.continew.admin.automation.service.AutomationUiCaseTreeService;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.starter.core.exception.BusinessException;

/** DTO 组装器和服务端字段白名单的唯一实现。 */
@Service
@RequiredArgsConstructor
public class AutomationUiCaseDetailServiceImpl implements AutomationUiCaseDetailService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final List<String> RECOVERED_RECORDING_FIELDS = List
        .of("target_selector", "target_xpath", "url", "locator_meta", "value");

    private final AutomationUiSceneMapper sceneMapper;
    private final AutomationUiCaseTreeService caseTreeService;
    private final AutomationRecordingActionResolver actionResolver;
    private final AutomationOperationCatalogService operationCatalogService;
    private final ObjectMapper objectMapper;

    @Override
    public AutomationUiCaseDetailResp getCaseDetail(Long sceneDbId, String caseId) {
        ResolvedCase resolved = resolveCase(sceneDbId, caseId);
        return toCaseDetail(resolved.scene(), resolved.caseDO());
    }

    @Override
    public AutomationUiStepDetailResp getStepDetail(Long sceneDbId, String caseId, String stepId) {
        ResolvedStep resolved = resolveStep(sceneDbId, caseId, stepId);
        return toStepDetail(resolved.step());
    }

    @Override
    public AutomationUiCaseDetailResp updateCase(Long sceneDbId, AutomationUiCaseEditReq request) {
        CaseDO command = new CaseDO();
        command.setId(request.getId());
        command.setName(request.getName());
        command.setRemark(request.getRemark());
        command.setStatus(request.getStatus());
        command.setExecutionConfig(toExecutionConfig(request.getExecutionConfig()));
        command.setExpectedDefinitionVersion(request.getExpectedDefinitionVersion());
        caseTreeService.updateCase(sceneDbId, command);
        return getCaseDetail(sceneDbId, request.getId());
    }

    @Override
    public AutomationUiStepDetailResp updateStep(Long sceneDbId, AutomationUiStepEditReq request) {
        StepDO command = new StepDO();
        command.setPid(request.getPid());
        command.setId(request.getId());
        command.setOrder(request.getOrder());
        command.setName(request.getName());
        command.setRemark(request.getRemark());
        command.setStatus(request.getStatus());
        String operationType = request.getOperationType();
        if ((operationType == null || operationType.isBlank()) && request.getMethodCode() != null) {
            operationType = operationCatalogService.findOperation(request.getMethodCode())
                .map(AutomationOperationCatalogService.OperationDescriptor::typeLabel)
                .orElse(operationType);
        }
        command.setOperationType(operationType);
        command.setOperationName(request.getOperationName());
        command.setOperationValue(request.getOperationValue());
        command.setExpectedDefinitionVersion(request.getExpectedDefinitionVersion());
        List<StepDO.Config> configs = new ArrayList<>();
        if (request.getConfigList() != null) {
            for (AutomationUiStepConfigEditReq source : request.getConfigList()) {
                if (source == null || source.getParamsName() == null || source.getParamsName().isBlank()) {
                    continue;
                }
                addConfig(configs, source.getParamsName(), source.getParamsValue());
            }
        }
        if (request.getMethodCode() != null && !request.getMethodCode().isBlank()) {
            putConfig(configs, "method_code", request.getMethodCode());
            if (request.getMethodVersion() != null) {
                putConfig(configs, "method_version", String.valueOf(request.getMethodVersion()));
            }
            try {
                putConfig(configs, "method_config", objectMapper.writeValueAsString(request.getMethodConfig() == null
                    ? Map.of()
                    : request.getMethodConfig()));
            } catch (Exception e) {
                throw new BusinessException("METHOD_CONFIG_INVALID：方法配置无法序列化");
            }
        }
        command.setConfigList(configs);
        caseTreeService.updateStep(sceneDbId, command);
        return getStepDetail(sceneDbId, request.getPid(), request.getId());
    }

    private AutomationUiCaseDetailResp toCaseDetail(AutomationUiSceneDO scene, CaseDO caseDO) {
        AutomationUiCaseDetailResp result = new AutomationUiCaseDetailResp();
        result.setId(caseDO.getId());
        result.setName(caseDO.getName());
        result.setRemark(caseDO.getRemark());
        result.setType(caseDO.getType());
        result.setOrder(caseDO.getOrder());
        result.setStatus(caseDO.getStatus());
        result.setDefinitionVersion(scene.getDefinitionVersion());
        result.setExecutionConfig(toExecutionConfigResp(caseDO.getExecutionConfig()));
        result.setOrigin(toOriginResp(caseDO.getOrigin()));
        List<AutomationUiStepDetailResp> steps = caseDO.getStepList() == null
            ? List.of()
            : caseDO.getStepList().stream().filter(Objects::nonNull).map(this::toStepDetail).toList();
        result.setSteps(steps);
        Map<String, Integer> counts = new LinkedHashMap<>();
        steps.forEach(step -> counts.merge(step.getSource(), 1, Integer::sum));
        result.setSourceCounts(counts);
        result.setCompositionSource(resolveComposition(counts));
        result.setNormalizedSource(resolveCaseSource(counts));
        return result;
    }

    private AutomationUiStepDetailResp toStepDetail(StepDO step) {
        AutomationRecordingActionResolver.Resolution resolution = actionResolver.resolve(step);
        AutomationUiStepDetailResp result = new AutomationUiStepDetailResp();
        result.setPid(step.getPid());
        result.setId(step.getId());
        result.setName(step.getName());
        result.setRemark(step.getRemark());
        result.setType(step.getType());
        result.setOrder(step.getOrder());
        result.setStatus(step.getStatus());
        AutomationOperationCatalogService.OperationDescriptor operation = operationCatalogService
            .findOperation(resolution.reverse().methodCode())
            .orElse(null);
        result.setOperationType(operation == null ? step.getOperationType() : operation.typeLabel());
        result.setOperationName(operation == null ? step.getOperationName() : operation.method().getLabel());
        result.setSource(resolution.source());
        result.setRecording(resolution.recording());
        result.setRecordingId(resolution.recordingId().isBlank() ? null : resolution.recordingId());
        result.setWarnings(resolution.warnings());
        result.setEditable(resolution.reverse().recognized());
        result.setMethodCode(resolution.reverse().methodCode());
        result.setMethodVersion(resolution.reverse().methodVersion());
        result.setMethodConfig(resolution.reverse().methodConfig());
        Map<String, String> values = configMap(step);
        boolean masked = "1".equals(values.get("value_masked")) || "true".equalsIgnoreCase(values.get("value_masked"));
        // 历史状态编辑只改写了 canonical step，录制事实仍在 original_* 中；详情读取时恢复，避免要求数据迁移。
        recoverOriginalRecordingValues(values, masked);
        result.setValueMasked(masked);
        result.setOperationValue(masked ? "******" : step.getOperationValue());
        result.setTargetSummary(resolveTargetSummary(values));
        result.setConfigList(toConfigResp(step, masked, values));
        return result;
    }

    private List<AutomationUiStepConfigResp> toConfigResp(StepDO step,
                                                          boolean masked,
                                                          Map<String, String> displayValues) {
        List<AutomationUiStepConfigResp> result = new ArrayList<>();
        if (step.getConfigList() != null) {
            step.getConfigList().stream().filter(Objects::nonNull).map(config -> {
                AutomationUiStepConfigResp item = new AutomationUiStepConfigResp();
                item.setParamsName(config.getParamsName());
                item.setReadOnly(false);
                String value = config.getParamsValue();
                if (masked && ("value".equals(config.getParamsName()) || "operationValue".equals(config
                    .getParamsName()))) {
                    value = "******";
                }
                if (masked && ("playwright_step".equals(config.getParamsName()) || "original_playwright_step"
                    .equals(config.getParamsName()))) {
                    value = maskRawStep(value);
                }
                item.setParamsValue(value);
                return item;
            }).forEach(result::add);
        }
        for (String name : RECOVERED_RECORDING_FIELDS) {
            boolean exists = result.stream().anyMatch(item -> name.equals(item.getParamsName()));
            String value = displayValues.get(name);
            if (!exists && value != null && !value.isBlank()) {
                AutomationUiStepConfigResp item = new AutomationUiStepConfigResp();
                item.setParamsName(name);
                item.setParamsValue(value);
                item.setReadOnly(true);
                result.add(item);
            }
        }
        return result;
    }

    private void recoverOriginalRecordingValues(Map<String, String> values, boolean masked) {
        Map<String, Object> originalStep = parseOptionalMap(values.get("original_playwright_step"));
        if (originalStep != null) {
            for (String name : List.of("target_selector", "target_xpath", "url")) {
                putTextIfAbsent(values, name, originalStep.get(name));
            }
            putJsonIfAbsent(values, "locator_meta", originalStep.get("locator_meta"));
            if (!masked) {
                putTextIfAbsent(values, "value", originalStep.get("value"));
            }
        }
        putTextIfAbsent(values, "locator_meta", values.get("original_locator_meta"));
    }

    private Map<String, Object> parseOptionalMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void putTextIfAbsent(Map<String, String> target, String name, Object value) {
        if (hasText(target.get(name)) || value == null || String.valueOf(value).isBlank()) {
            return;
        }
        target.put(name, String.valueOf(value));
    }

    private void putJsonIfAbsent(Map<String, String> target, String name, Object value) {
        if (hasText(target.get(name)) || value == null) {
            return;
        }
        try {
            target.put(name, value instanceof String text ? text : objectMapper.writeValueAsString(value));
        } catch (Exception ignored) {
            // 原始定位元数据损坏时保持缺失，不能构造伪造 locator_meta。
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskRawStep(String value) {
        try {
            Map<String, Object> raw = objectMapper.readValue(value, MAP_TYPE);
            if (raw.containsKey("value"))
                raw.put("value", "******");
            return objectMapper.writeValueAsString(raw);
        } catch (Exception ignored) {
            return "******";
        }
    }

    private String resolveTargetSummary(Map<String, String> values) {
        String targetRef = values.get("target_ref");
        if (targetRef != null && !targetRef.isBlank())
            return targetRef;
        String selector = values.get("target_selector");
        if (selector != null && !selector.isBlank())
            return "css=" + selector;
        String xpath = values.get("target_xpath");
        return xpath == null || xpath.isBlank() ? "" : "xpath=" + xpath;
    }

    private String resolveComposition(Map<String, Integer> counts) {
        if (counts.isEmpty())
            return "empty";
        if (counts.size() == 1)
            return counts.keySet().iterator().next();
        return "mixed";
    }

    private String resolveCaseSource(Map<String, Integer> counts) {
        if (counts.isEmpty())
            return "empty";
        if (counts.containsKey("sakura-playwright") && counts.size() == 1)
            return "sakura-playwright";
        if (counts.containsKey("admin-manual") && counts.size() == 1)
            return "admin-manual";
        if (counts.containsKey("legacy") && counts.size() == 1)
            return "legacy";
        return "unknown";
    }

    private Map<String, String> configMap(StepDO step) {
        Map<String, String> values = new LinkedHashMap<>();
        if (step.getConfigList() != null)
            step.getConfigList().forEach(item -> {
                if (item != null && item.getParamsName() != null)
                    values.put(item.getParamsName(), item.getParamsValue());
            });
        return values;
    }

    private void addConfig(List<StepDO.Config> configs, String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        configs.add(config);
    }

    private void putConfig(List<StepDO.Config> configs, String name, String value) {
        configs.removeIf(config -> config != null && Objects.equals(name, config.getParamsName()));
        addConfig(configs, name, value);
    }

    private CaseExecutionConfigDO toExecutionConfig(AutomationUiCaseExecutionConfigReq source) {
        if (source == null)
            return null;
        CaseExecutionConfigDO target = new CaseExecutionConfigDO();
        target.setStartUrl(source.getStartUrl());
        target.setWindowSizeMode(source.getWindowSizeMode());
        target.setViewportWidth(source.getViewportWidth());
        target.setViewportHeight(source.getViewportHeight());
        target.setScreenshotMode(source.getScreenshotMode());
        target.setPageErrorCheckEnabled(source.getPageErrorCheckEnabled());
        return target;
    }

    private AutomationUiCaseExecutionConfigResp toExecutionConfigResp(CaseExecutionConfigDO source) {
        if (source == null)
            return null;
        AutomationUiCaseExecutionConfigResp target = new AutomationUiCaseExecutionConfigResp();
        target.setStartUrl(source.getStartUrl());
        target.setWindowSizeMode(source.getWindowSizeMode());
        target.setViewportWidth(source.getViewportWidth());
        target.setViewportHeight(source.getViewportHeight());
        target.setScreenshotMode(source.getScreenshotMode());
        target.setPageErrorCheckEnabled(source.getPageErrorCheckEnabled());
        return target;
    }

    private AutomationUiCaseOriginResp toOriginResp(CaseOriginDO source) {
        if (source == null)
            return null;
        AutomationUiCaseOriginResp target = new AutomationUiCaseOriginResp();
        target.setCreationSource(source.getCreationSource());
        target.setOriginalCaseId(source.getOriginalCaseId());
        target.setInitialRecordingId(source.getInitialRecordingId());
        target.setCopiedFromCaseId(source.getCopiedFromCaseId());
        return target;
    }

    private ResolvedCase resolveCase(Long sceneDbId, String caseId) {
        AutomationUiSceneDO scene = sceneMapper.selectById(sceneDbId);
        if (scene == null)
            throw new BusinessException("SCENE_NOT_FOUND：场景不存在");
        if (scene.getCaseList() != null)
            for (CaseDO item : scene.getCaseList()) {
                if (item != null && Objects.equals(item.getId(), caseId))
                    return new ResolvedCase(scene, item);
            }
        throw new BusinessException("CASE_NOT_FOUND：用例不存在");
    }

    private ResolvedStep resolveStep(Long sceneDbId, String caseId, String stepId) {
        ResolvedCase resolved = resolveCase(sceneDbId, caseId);
        if (resolved.caseDO().getStepList() != null)
            for (StepDO step : resolved.caseDO().getStepList()) {
                if (step != null && Objects.equals(step.getId(), stepId))
                    return new ResolvedStep(resolved.scene(), step);
            }
        throw new BusinessException("STEP_NOT_FOUND：步骤不存在");
    }

    private record ResolvedCase(AutomationUiSceneDO scene, CaseDO caseDO) {
    }

    private record ResolvedStep(AutomationUiSceneDO scene, StepDO step) {
    }
}
