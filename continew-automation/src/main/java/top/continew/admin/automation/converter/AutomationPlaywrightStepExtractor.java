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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.core.exception.BusinessException;

/**
 * 从 admin StepDO 反向提取 Playwright 原始 step。
 *
 * @author Codex
 */
@Component
@RequiredArgsConstructor
public class AutomationPlaywrightStepExtractor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> INTERNAL_CONFIGS = Set
        .of("playwright_step", "type_code", "type_label", "method_code", "method_version", "method_label", "diagnostic_profile", "method_config", "canonical_digest", "catalog_version", "schema_version", "original_case_id", "original_step_id", "recording_id", "screenshot", "screenshot_url", "screenshot_file_id", "screenshot_path", "screenshot_present");

    private final ObjectMapper objectMapper;
    private final AutomationOperationCatalogService catalogService;

    public Map<String, Object> extract(StepDO stepDO, int index) {
        Map<String, String> configs = toConfigMap(stepDO.getConfigList());
        String rawStep = configs.get("playwright_step");
        if (rawStep != null && !rawStep.isBlank() && !isHistoricalManualStep(configs)) {
            Map<String, Object> step = parseMap(rawStep);
            Object originalStepId = step.get("id");
            if (originalStepId != null && !String.valueOf(originalStepId).equals(stepDO.getId())) {
                step.putIfAbsent("original_step_id", originalStepId);
            }
            // admin 是主数据源，执行步骤使用唯一 StepDO.id；原始录制 ID 仅用于追溯且不会回写 playwright_step。
            if (stepDO.getId() != null && !stepDO.getId().isBlank()) {
                step.put("id", stepDO.getId());
            }
            // 原始 playwright_step 不包含中台启用状态；必须以 StepDO 为准，供 Runner 最后过滤。
            step.put("status", stepStatus(stepDO));
            step.putIfAbsent("step_index", index);
            Object description = step.get("description");
            if (description == null || description.toString().isBlank()) {
                step.put("description", stepDO.getName());
            }
            // 编辑后的录制步骤可能已有新的 canonical step；缺失字段必须从原始录制快照恢复，不能让定位事实退化。
            copyOriginalRecordingFacts(step, configs);
            // case 级配置仍挂在 StepDO.configList 中，补到响应顶层供两种执行器读取。
            copyIfAbsent(step, configs, "start_url");
            copyIfAbsent(step, configs, "end_url");
            copyIfAbsent(step, configs, "screenshot_mode");
            copyIfAbsent(step, configs, "page_error_check_enabled");
            copyIfAbsent(step, configs, "window_size_mode");
            copyIfAbsent(step, configs, "viewport_width");
            copyIfAbsent(step, configs, "viewport_height");
            enrichMethodIdentity(step, configs);
            return step;
        }
        Map<String, Object> step = fallbackStep(stepDO, configs);
        step.put("id", stepDO.getId());
        // legacy 步骤同样以 StepDO.status 为执行开关，不能只依赖 operation 字段。
        step.put("status", stepStatus(stepDO));
        step.put("step_index", index);
        step.put("description", stepDO.getName());
        step.putIfAbsent("value_masked", configs.getOrDefault("value_masked", "0"));
        step.putIfAbsent("wait_before", configs.getOrDefault("wait_before", "0"));
        step.putIfAbsent("is_overlay", configs.getOrDefault("is_overlay", "0"));
        enrichMethodIdentity(step, configs);
        return step;
    }

    private void enrichMethodIdentity(Map<String, Object> step, Map<String, String> configs) {
        String methodRef = firstText(String.valueOf(step.getOrDefault("method_code", "")), configs
            .get("method_code"), String.valueOf(step.getOrDefault("action_type", "")));
        catalogService.findOperation(methodRef).ifPresent(operation -> {
            var method = operation.method();
            step.putIfAbsent("type_code", operation.typeCode());
            step.putIfAbsent("type_label", operation.typeLabel());
            step.putIfAbsent("method_code", method.getMethodCode());
            step.putIfAbsent("method_version", method.getMethodVersion());
            step.putIfAbsent("method_label", method.getLabel());
            step.putIfAbsent("diagnostic_profile", method.getDiagnosticProfile());
            // Runner 按 Admin 已校验的目录字段生成执行详情，避免各执行器维护第二份参数列表。
            if (!step.containsKey("diagnostic_fields") && method.getFormSchema() != null) {
                step.put("diagnostic_fields", method.getFormSchema().stream().map(LinkedHashMap::new).toList());
            }
        });
    }

    /**
     * 旧手工步骤的 raw 快照可能由过期表单生成。没有 method_code 且非录制来源时，
     * 只能按当前 legacy 配置重建，不能把该快照误当成执行事实。
     */
    private boolean isHistoricalManualStep(Map<String, String> configs) {
        if (configs.containsKey("method_code") && !configs.get("method_code").isBlank()) {
            return false;
        }
        return "admin-manual".equalsIgnoreCase(configs.get("source"));
    }

    private Map<String, Object> fallbackStep(StepDO stepDO, Map<String, String> configs) {
        Map<String, Object> step = new LinkedHashMap<>();
        String methodConfig = configs.get("method_config");
        if (methodConfig != null && !methodConfig.isBlank()) {
            step.putAll(parseMap(methodConfig));
        }
        // 旧 XML/Jenkins 步骤没有 raw step 时，保留其实际参数供新执行器兼容回放。
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            if (!INTERNAL_CONFIGS.contains(entry.getKey()) && !"locator_meta".equals(entry.getKey())) {
                step.put(entry.getKey(), entry.getValue());
            }
        }
        String actionType = configs.get("action_type");
        if (actionType == null || actionType.isBlank()) {
            actionType = fallbackActionType(stepDO.getOperationValue());
        }
        step.put("action_type", actionType);
        applyFallbackLocator(step, configs);
        if (configs.containsKey("locator_meta")) {
            step.put("locator_meta", parseMap(configs.get("locator_meta")));
        }
        applyFallbackCompatibility(step, configs);
        return step;
    }

    private void applyFallbackLocator(Map<String, Object> step, Map<String, String> configs) {
        putIfText(step, "target_selector", configs.get("target_selector"));
        putIfText(step, "target_xpath", configs.get("target_xpath"));
        String locator = configs.get("locator");
        if (locator == null || locator.isBlank()) {
            return;
        }
        if (locator.regionMatches(true, 0, "xpath=", 0, 6)) {
            step.putIfAbsent("target_xpath", locator.substring(6));
        } else if (locator.startsWith("/") || locator.startsWith("(")) {
            step.putIfAbsent("target_xpath", locator);
        } else if (locator.regionMatches(true, 0, "css=", 0, 4)) {
            step.putIfAbsent("target_selector", locator.substring(4));
        } else {
            step.putIfAbsent("target_selector", locator);
        }
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.putIfAbsent(key, value);
        }
    }

    private void applyFallbackCompatibility(Map<String, Object> step, Map<String, String> configs) {
        String action = String.valueOf(step.get("action_type")).toLowerCase(Locale.ROOT);
        if ("navigate".equals(action)) {
            String url = firstText(configs.get("url"), configs.get("value"));
            step.putIfAbsent("url", url);
            step.putIfAbsent("value", url);
        } else if ("assert_text".equals(action)) {
            step.putIfAbsent("expect", firstText(configs.get("expect"), configs.get("value")));
            step.putIfAbsent("value", firstText(configs.get("expect"), configs.get("value")));
        } else if ("wait".equals(action) || "implicit_wait".equals(action)) {
            step.putIfAbsent("duration_ms", firstText(configs.get("duration_ms"), configs.get("value")));
            step.putIfAbsent("value", firstText(configs.get("duration_ms"), configs.get("value")));
        } else if ("key".equals(action)) {
            String key = firstText(configs.get("value"), configs.get("keys"), configs.get("key"));
            step.putIfAbsent("key", key);
            step.putIfAbsent("value", key);
        }
        step.putIfAbsent("value", configs.getOrDefault("value", ""));
        step.putIfAbsent("url", configs.getOrDefault("url", ""));
    }

    private void copyIfAbsent(Map<String, Object> target, Map<String, String> configs, String key) {
        if (!target.containsKey(key) && configs.containsKey(key)) {
            target.put(key, configs.get(key));
        }
    }

    private void copyOriginalRecordingFacts(Map<String, Object> step, Map<String, String> configs) {
        Map<String, Object> original = parseOptionalMap(configs.get("original_playwright_step"));
        if (original != null) {
            for (String key : List.of("target_selector", "target_xpath", "locator_meta", "url", "key")) {
                copyObjectIfAbsent(step, original, key);
            }
            if (!isMasked(configs)) {
                copyObjectIfAbsent(step, original, "value");
            }
            if ("key".equals(String.valueOf(step.get("action_type")))) {
                copyObjectIfAbsent(step, original, "key", "value");
            }
        }
        Map<String, Object> originalLocatorMeta = parseOptionalMap(configs.get("original_locator_meta"));
        if (originalLocatorMeta != null) {
            step.putIfAbsent("locator_meta", originalLocatorMeta);
        }
        for (String key : List.of("target_selector", "target_xpath", "url")) {
            copyIfAbsent(step, configs, key);
        }
        if (!isMasked(configs)) {
            copyIfAbsent(step, configs, "value");
        }
    }

    private void copyObjectIfAbsent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (!target.containsKey(key) && source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private void copyObjectIfAbsent(Map<String, Object> target,
                                    Map<String, Object> source,
                                    String targetKey,
                                    String sourceKey) {
        if (!target.containsKey(targetKey) && source.containsKey(sourceKey) && source.get(sourceKey) != null) {
            target.put(targetKey, source.get(sourceKey));
        }
    }

    private boolean isMasked(Map<String, String> configs) {
        return "1".equals(configs.get("value_masked")) || "true".equalsIgnoreCase(configs.get("value_masked"));
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

    private String stepStatus(StepDO stepDO) {
        StatusTypeEnum status = stepDO.getStatus();
        return status == null ? StatusTypeEnum.ENABLE.name() : status.name();
    }

    private Map<String, String> toConfigMap(List<StepDO.Config> configList) {
        Map<String, String> configs = new LinkedHashMap<>();
        if (configList == null) {
            return configs;
        }
        for (StepDO.Config config : configList) {
            if (config != null && config.getParamsName() != null) {
                configs.put(config.getParamsName(), config.getParamsValue());
            }
        }
        return configs;
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException("Playwright step 解析失败：" + e.getMessage());
        }
    }

    private String fallbackActionType(String operationValue) {
        if (operationValue == null || operationValue.isBlank()) {
            return "unknown";
        }
        return catalogService.findMethod(operationValue)
            .map(top.continew.admin.automation.model.catalog.AutomationOperationCatalog.OperationMethod::getActionType)
            .orElseGet(() -> fallbackUncataloguedActionType(operationValue));
    }

    private String fallbackUncataloguedActionType(String operationValue) {
        if ("infra-server-command".equals(operationValue)) {
            return "server_command";
        }
        if ("infra-database-sql".equals(operationValue)) {
            return "database_sql";
        }
        if ("infra-database-native".equals(operationValue)) {
            return "database_native";
        }
        return operationValue.startsWith("pw-") ? operationValue.substring(3) : operationValue;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
