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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedStepReq;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 将 CueCast 录制私有动作投影为 Admin 操作目录方法。
 *
 * <p>投影只生成展示和编辑旁路配置，不修改原始 playwright_step；失败结果由导入器降级保存，
 * 不能因为目录无法表达而丢弃录制步骤。</p>
 */
@Component
@RequiredArgsConstructor
public class CuecastRecordingOperationProjector {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> MATCH_MODES = Set.of("contains", "equals", "not_contains", "regex", "visible");
    private static final Set<String> READ_MODES = Set.of("auto", "text", "value");

    private final ObjectMapper objectMapper;
    private final AutomationOperationCatalogService catalogService;
    private final AutomationOperationConfigValidator configValidator;

    public RecordedOperationProjection project(PlaywrightRecordedStepReq step) {
        if (step == null) {
            return RecordedOperationProjection.notApplicable();
        }
        return project(step.getActionType(), step.getValue(), step.getTargetSelector(), step.getTargetXpath(), step
            .getLocatorMeta());
    }

    public RecordedOperationProjection project(Map<String, Object> rawStep) {
        if (rawStep == null) {
            return RecordedOperationProjection.notApplicable();
        }
        return project(text(rawStep.get("action_type")), rawStep.get("value"), text(rawStep
            .get("target_selector")), text(rawStep.get("target_xpath")), rawStep.get("locator_meta"));
    }

    private RecordedOperationProjection project(String rawActionType,
                                                Object value,
                                                String targetSelector,
                                                String targetXpath,
                                                Object rawLocatorMeta) {
        String actionType = text(rawActionType).toLowerCase(java.util.Locale.ROOT);
        if (!"set_variable".equals(actionType) && !"assert_text".equals(actionType)) {
            return RecordedOperationProjection.notApplicable();
        }

        Map<String, Object> locatorMeta = mapValue(rawLocatorMeta);
        if ("assert_text".equals(actionType) && !hasAssertionMetadata(locatorMeta)) {
            return rawLocatorMeta != null && locatorMeta == null
                ? RecordedOperationProjection.failed("RECORDED_LOCATOR_META_INVALID")
                : RecordedOperationProjection.notApplicable();
        }
        if (locatorMeta == null) {
            return RecordedOperationProjection.failed("RECORDED_LOCATOR_META_INVALID");
        }
        return "set_variable".equals(actionType)
            ? projectVariable(value, targetSelector, targetXpath, locatorMeta)
            : projectAssertion(value, targetSelector, targetXpath, locatorMeta);
    }

    private RecordedOperationProjection projectVariable(Object value,
                                                        String targetSelector,
                                                        String targetXpath,
                                                        Map<String, Object> locatorMeta) {
        Map<String, Object> variable = nestedMap(locatorMeta, "context", "variable");
        if (variable == null) {
            return RecordedOperationProjection.failed("RECORDED_VARIABLE_METADATA_MISSING");
        }
        String variableName = firstText(variable.get("name"), value);
        if (variableName.isBlank()) {
            return RecordedOperationProjection.failed("RECORDED_VARIABLE_NAME_MISSING");
        }
        Map<String, Object> targetRef = targetRef(targetSelector, targetXpath, locatorMeta);
        if (targetRef == null) {
            return RecordedOperationProjection.failed("RECORDED_TARGET_MISSING");
        }

        String source = defaultText(variable.get("source"), "text").toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("value", "text", "contenteditable").contains(source)) {
            return RecordedOperationProjection.failed("RECORDED_VARIABLE_SOURCE_UNSUPPORTED");
        }
        Map<String, Object> extract = mapValue(variable.get("extract"));
        String extractMode = defaultText(extract == null ? null : extract.get("mode"), "full")
            .toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("full", "regex").contains(extractMode)) {
            return RecordedOperationProjection.failed("RECORDED_VARIABLE_EXTRACT_UNSUPPORTED");
        }

        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("variable_name", variableName);
        config.put("source_type", "locator");
        config.put("target_ref", targetRef);
        config.put("read_mode", "value".equals(source) ? "value" : "text");
        if ("regex".equals(extractMode)) {
            String pattern = text(extract == null ? null : extract.get("pattern"));
            if (pattern.isBlank()) {
                return RecordedOperationProjection.failed("RECORDED_VARIABLE_REGEX_MISSING");
            }
            Integer group = integerValue(extract.get("group"));
            if (group == null || group < 0) {
                return RecordedOperationProjection.failed("RECORDED_VARIABLE_REGEX_GROUP_INVALID");
            }
            config.put("regex", pattern);
            config.put("regex_group", group);
        }
        return validate("global.variable.set", config);
    }

    private RecordedOperationProjection projectAssertion(Object value,
                                                         String targetSelector,
                                                         String targetXpath,
                                                         Map<String, Object> locatorMeta) {
        Map<String, Object> assertion = mapValue(locatorMeta.get("assertion"));
        Map<String, Object> contextAssertion = nestedMap(locatorMeta, "context", "assertion");
        if (assertion == null) {
            assertion = contextAssertion;
        }
        if (assertion == null || !"element".equals(defaultText(assertion.get("target"), "element"))) {
            return RecordedOperationProjection.failed("RECORDED_ASSERTION_TARGET_UNSUPPORTED");
        }
        String matchMode = text(assertion.get("match")).toLowerCase(java.util.Locale.ROOT);
        if (!MATCH_MODES.contains(matchMode)) {
            return RecordedOperationProjection.failed("RECORDED_ASSERTION_MATCH_UNSUPPORTED");
        }
        Map<String, Object> targetRef = targetRef(targetSelector, targetXpath, locatorMeta);
        if (targetRef == null) {
            return RecordedOperationProjection.failed("RECORDED_TARGET_MISSING");
        }

        String source = defaultText(contextAssertion == null ? null : contextAssertion.get("source"), "auto")
            .toLowerCase(java.util.Locale.ROOT);
        String readMode = "contenteditable".equals(source) ? "text" : source;
        if (!READ_MODES.contains(readMode)) {
            return RecordedOperationProjection.failed("RECORDED_ASSERTION_SOURCE_UNSUPPORTED");
        }
        String expected = "visible".equals(matchMode) ? "" : text(value);
        if (!"visible".equals(matchMode) && expected.isBlank()) {
            return RecordedOperationProjection.failed("RECORDED_ASSERTION_EXPECT_MISSING");
        }

        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("target_ref", targetRef);
        config.put("read_mode", readMode);
        config.put("match_mode", matchMode);
        if (!"visible".equals(matchMode)) {
            config.put("expect", expected);
        }
        return validate("assertion.element.match", config);
    }

    private RecordedOperationProjection validate(String methodCode, LinkedHashMap<String, Object> config) {
        AutomationOperationCatalog.OperationMethod method = catalogService.findMethod(methodCode).orElse(null);
        AutomationOperationCatalogService.OperationDescriptor operation = catalogService.findOperation(methodCode)
            .orElse(null);
        if (method == null || operation == null) {
            return RecordedOperationProjection.failed("RECORDED_METHOD_NOT_REGISTERED");
        }
        try {
            configValidator.validate(method, config);
        } catch (BusinessException e) {
            return RecordedOperationProjection.failed("RECORDED_METHOD_CONFIG_INVALID：" + e.getMessage());
        }
        return new RecordedOperationProjection(true, true, method.getMethodCode(), method.getMethodVersion(), Map
            .copyOf(config), operation.typeCode(), operation.typeLabel(), method.getLabel(), method
                .getLegacyAction(), method.getDiagnosticProfile(), List.of());
    }

    private Map<String, Object> targetRef(String selector, String xpath, Map<String, Object> locatorMeta) {
        boolean hasCandidate = locatorMeta.get("candidates") instanceof List<?> candidates && candidates.stream()
            .map(this::mapValue)
            .filter(java.util.Objects::nonNull)
            .anyMatch(candidate -> !text(candidate.get("value")).isBlank());
        if (text(selector).isBlank() && text(xpath).isBlank() && !hasCandidate) {
            return null;
        }
        LinkedHashMap<String, Object> target = new LinkedHashMap<>();
        target.put("scope", "page");
        putIfText(target, "target_selector", selector);
        putIfText(target, "target_xpath", xpath);
        // locator_meta 是语义定位事实，旁路投影也不能退化为仅 CSS/XPath。
        target.put("locator_meta", locatorMeta);
        return target;
    }

    private boolean hasAssertionMetadata(Map<String, Object> locatorMeta) {
        return locatorMeta != null && (mapValue(locatorMeta
            .get("assertion")) != null || nestedMap(locatorMeta, "context", "assertion") != null);
    }

    private Map<String, Object> nestedMap(Map<String, Object> source, String parent, String child) {
        Map<String, Object> parentMap = source == null ? null : mapValue(source.get(parent));
        return parentMap == null ? null : mapValue(parentMap.get(child));
    }

    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String json) {
                return json.isBlank() ? null : objectMapper.readValue(json, MAP_TYPE);
            }
            return objectMapper.convertValue(value, MAP_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(text(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void putIfText(Map<String, Object> target, String name, Object value) {
        String text = text(value);
        if (!text.isBlank()) {
            target.put(name, text);
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record RecordedOperationProjection(boolean attempted, boolean recognized, String methodCode,
                                              Integer methodVersion, Map<String, Object> methodConfig, String typeCode,
                                              String typeLabel, String methodLabel, String legacyAction,
                                              String diagnosticProfile, List<String> warnings) {

        public static RecordedOperationProjection notApplicable() {
            return new RecordedOperationProjection(false, false, "", null, Map.of(), "", "", "", "", "", List.of());
        }

        public static RecordedOperationProjection failed(String warning) {
            return new RecordedOperationProjection(true, false, "", null, Map.of(), "", "", "", "", "", List
                .of(warning));
        }
    }
}
