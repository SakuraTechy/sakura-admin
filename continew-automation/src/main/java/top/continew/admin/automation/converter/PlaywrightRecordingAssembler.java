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

package top.continew.admin.automation.converter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedCaseReq;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedStepReq;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService.ScreenshotArtifact;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.core.exception.BusinessException;

/**
 * Playwright 录制结构转换为 admin 场景用例结构。
 *
 * @author Codex
 */
@Component
@RequiredArgsConstructor
public class PlaywrightRecordingAssembler {

    public static final String DEFAULT_CASE_ID = "SCENE_CASE_001";
    public static final String STEP_ID_PREFIX = "CASE_STEP_";
    public static final String SOURCE = "sakura-playwright";

    private static final String TYPE_CASE = "case";
    private static final String TYPE_STEP = "step";

    private final ObjectMapper objectMapper;
    private final AutomationRecordingScreenshotService screenshotService;

    public CaseDO toCase(PlaywrightRecordedCaseReq recordedCase, RecordingImportContext context) {
        return toCase(recordedCase, DEFAULT_CASE_ID, 1, context);
    }

    public CaseDO toCase(PlaywrightRecordedCaseReq recordedCase,
                         String caseId,
                         int order,
                         RecordingImportContext context) {
        CaseDO caseDO = new CaseDO();
        caseDO.setId(caseId);
        caseDO.setName(defaultString(recordedCase.getName(), "Playwright 录制用例"));
        caseDO.setRemark(recordedCase.getDescription());
        caseDO.setType(TYPE_CASE);
        caseDO.setOrder(order);
        caseDO.setStatus(StatusTypeEnum.ENABLE);

        caseDO.setStepList(toSteps(recordedCase, caseId, context));
        return caseDO;
    }

    public List<StepDO> toSteps(PlaywrightRecordedCaseReq recordedCase, String caseId, RecordingImportContext context) {
        List<StepDO> stepList = new ArrayList<>();
        List<PlaywrightRecordedStepReq> steps = recordedCase.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            PlaywrightRecordedStepReq step = steps.get(i);
            stepList.add(toStep(recordedCase, step, caseId, resolveStepOrder(step, i + 1), context));
        }
        return stepList;
    }

    public StepDO toStep(PlaywrightRecordedCaseReq recordedCase,
                         PlaywrightRecordedStepReq step,
                         String caseId,
                         int order,
                         RecordingImportContext context) {
        String actionType = normalizeActionType(step.getActionType());
        PlaywrightActionMapping.ActionDisplay display = PlaywrightActionMapping.resolve(actionType);

        StepDO stepDO = new StepDO();
        stepDO.setPid(caseId);
        stepDO.setId(nextStepId(order));
        stepDO.setName(resolveStepName(step, display, order));
        stepDO.setRemark(step.getDescription());
        stepDO.setType(TYPE_STEP);
        stepDO.setOperationType(display.operationType());
        stepDO.setOperationName(display.operationName());
        stepDO.setOperationValue(display.operationValue());
        stepDO.setOrder(order);
        stepDO.setStatus(StatusTypeEnum.ENABLE);
        stepDO.setConfigList(buildConfigList(recordedCase, step, actionType, caseId, stepDO.getId(), order, context));
        return stepDO;
    }

    private List<StepDO.Config> buildConfigList(PlaywrightRecordedCaseReq recordedCase,
                                                PlaywrightRecordedStepReq step,
                                                String actionType,
                                                String caseId,
                                                String adminStepId,
                                                int order,
                                                RecordingImportContext context) {
        List<StepDO.Config> configList = new ArrayList<>();
        ScreenshotArtifact screenshotArtifact = saveScreenshotIfNeeded(step, caseId, adminStepId, context);
        Map<String, Object> rawStep = toRawStepMap(step, order, context, screenshotArtifact);

        // 保留原始 Playwright step 作为后续执行、导出、调试回放的事实来源。
        // admin 操作字段仅用于展示和兼容旧流程，不能替代 playwright_step。
        addConfig(configList, "playwright_step", toJson(rawStep));
        addConfig(configList, "action_type", actionType);
        addConfig(configList, "source", SOURCE);
        addConfig(configList, "recording_id", context.recordingId());
        addConfig(configList, "original_case_id", valueToString(recordedCase.getId()));
        addConfig(configList, "original_step_id", valueToString(step.getId()));
        addConfig(configList, "target_selector", step.getTargetSelector());
        addConfig(configList, "target_xpath", step.getTargetXpath());
        if (step.getLocatorMeta() != null) {
            // locator_meta 包含高级定位上下文，必须原样 JSON 化保存，不能只保留 CSS/XPath。
            addConfig(configList, "locator_meta", toJson(step.getLocatorMeta()));
        }
        addConfig(configList, "value", valueToJsonOrString(step.getValue()));
        addConfig(configList, "value_masked", valueToString(step.getValueMasked()));
        addConfig(configList, "url", step.getUrl());
        addConfig(configList, "wait_before", valueToString(step.getWaitBefore()));
        addConfig(configList, "is_overlay", valueToString(step.getIsOverlay()));
        addConfig(configList, "start_url", recordedCase.getStartUrl());
        addConfig(configList, "end_url", recordedCase.getEndUrl());
        addConfig(configList, "screenshot_mode", recordedCase.getScreenshotMode());
        addConfig(configList, "page_error_check_enabled", valueToString(recordedCase.getPageErrorCheckEnabled()));
        addConfig(configList, "window_size_mode", recordedCase.getWindowSizeMode());
        addConfig(configList, "viewport_width", valueToString(recordedCase.getViewportWidth()));
        addConfig(configList, "viewport_height", valueToString(recordedCase.getViewportHeight()));
        if (hasText(step.getScreenshot())) {
            addConfig(configList, "screenshot_present", "true");
        }
        if (screenshotArtifact != null) {
            // 截图文件化后只保存文件引用信息，避免 base64 直接撑大 caseList JSON。
            addConfig(configList, "screenshot_url", screenshotArtifact.url());
            addConfig(configList, "screenshot_path", screenshotArtifact.relativePath());
            addConfig(configList, "screenshot_file_id", screenshotArtifact.fileId());
            addConfig(configList, "screenshot_thumbnail_url", screenshotArtifact.thumbnailUrl());
            addConfig(configList, "screenshot_content_type", screenshotArtifact.contentType());
            addConfig(configList, "screenshot_size", valueToString(screenshotArtifact.size()));
        }
        if (step.getScreenshotFocus() != null) {
            addConfig(configList, "screenshot_focus", toJson(step.getScreenshotFocus()));
        }
        if (step.getScreenshotFocusRect() != null) {
            addConfig(configList, "screenshot_focus_rect", toJson(step.getScreenshotFocusRect()));
        }
        if (!PlaywrightActionMapping.isKnown(actionType)) {
            // 未识别 action 降级为 pw-custom，但完整 step 仍保留，避免录制能力丢失。
            addConfig(configList, "unknown_action_type", actionType);
        }
        return configList;
    }

    private Map<String, Object> toRawStepMap(PlaywrightRecordedStepReq step,
                                             int order,
                                             RecordingImportContext context,
                                             ScreenshotArtifact screenshotArtifact) {
        Map<String, Object> raw = new LinkedHashMap<>();
        // step.id 是录制端定义的顺序标识，StepDO.order 也由它解析得到，不能反向用 order 伪造原始 id。
        putIfNotNull(raw, "id", step.getId());
        putIfNotNull(raw, "step_index", step.getStepIndex());
        putIfNotNull(raw, "action_type", step.getActionType());
        putIfNotNull(raw, "target_selector", step.getTargetSelector());
        putIfNotNull(raw, "target_xpath", step.getTargetXpath());
        putIfNotNull(raw, "locator_meta", step.getLocatorMeta());
        putIfNotNull(raw, "value", step.getValue());
        putIfNotNull(raw, "value_masked", step.getValueMasked());
        putIfNotNull(raw, "url", step.getUrl());
        putIfNotNull(raw, "description", step.getDescription());
        putIfNotNull(raw, "wait_before", step.getWaitBefore());
        putIfNotNull(raw, "is_overlay", step.getIsOverlay());
        if (hasText(step.getScreenshot())) {
            if (screenshotArtifact != null) {
                // playwright_step 是执行事实来源，但截图二进制必须文件化引用。
                raw.put("screenshot_url", screenshotArtifact.url());
                raw.put("screenshot_path", screenshotArtifact.relativePath());
                raw.put("screenshot_file_id", screenshotArtifact.fileId());
            } else if (context.keepRawScreenshotInStep()) {
                raw.put("screenshot", step.getScreenshot());
            } else {
                // screenshot base64 可能非常大，MVP 只保留存在标记，避免 caseList JSON 无界增长。
                raw.put("screenshot_present", true);
            }
        }
        putIfNotNull(raw, "screenshot_focus", step.getScreenshotFocus());
        putIfNotNull(raw, "screenshot_focus_rect", step.getScreenshotFocusRect());
        raw.putAll(step.getExtra());
        return raw;
    }

    private ScreenshotArtifact saveScreenshotIfNeeded(PlaywrightRecordedStepReq step,
                                                      String caseId,
                                                      String adminStepId,
                                                      RecordingImportContext context) {
        if (!context.persistScreenshots() || !hasText(step.getScreenshot())) {
            return null;
        }
        return screenshotService.store(context.recordingId(), context.projectShortName(), context.versionName(), context
            .sceneId(), caseId, adminStepId, step.getScreenshot());
    }

    private String resolveStepName(PlaywrightRecordedStepReq step,
                                   PlaywrightActionMapping.ActionDisplay display,
                                   int order) {
        if (hasText(step.getDescription())) {
            return step.getDescription().trim();
        }
        String actionName = display.operationName();
        if (hasText(step.getTargetSelector())) {
            return actionName + " " + step.getTargetSelector().trim();
        }
        if (hasText(step.getUrl())) {
            return actionName + " " + step.getUrl().trim();
        }
        return actionName + " " + order;
    }

    private String normalizeActionType(String actionType) {
        return hasText(actionType) ? actionType.trim() : "unknown";
    }

    private int resolveStepOrder(PlaywrightRecordedStepReq step, int fallbackOrder) {
        Integer order = parsePositiveInt(step.getId());
        if (order != null) {
            return order;
        }
        if (step.getStepIndex() != null && step.getStepIndex() > 0) {
            return step.getStepIndex();
        }
        return fallbackOrder;
    }

    private Integer parsePositiveInt(Object value) {
        if (value instanceof Number number) {
            int order = number.intValue();
            return order > 0 ? order : null;
        }
        if (value instanceof String text && hasText(text)) {
            try {
                int order = Integer.parseInt(text.trim());
                return order > 0 ? order : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void addConfig(List<StepDO.Config> configList, String name, String value) {
        if (!hasText(value)) {
            return;
        }
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        configList.add(config);
    }

    private void putIfNotNull(Map<String, Object> raw, String key, Object value) {
        if (value != null) {
            raw.put(key, value);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("录制导入失败：序列化 Playwright step 失败：" + e.getMessage());
        }
    }

    private String valueToJsonOrString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return toJson(value);
    }

    private String valueToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultString(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nextStepId(int order) {
        return STEP_ID_PREFIX + String.format("%03d", order);
    }

    public record RecordingImportContext(String recordingId, String projectShortName, String versionName,
                                         String sceneId, boolean persistScreenshots, boolean keepRawScreenshotInStep) {
        public RecordingImportContext {
            Objects.requireNonNull(recordingId, "recordingId");
        }
    }
}
