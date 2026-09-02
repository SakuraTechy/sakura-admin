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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.CaseOriginDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

/**
 * 用例内容指纹。
 *
 * <p>指纹只表达目标用例自身的执行定义。用例在场景中的位置不属于内容，
 * 步骤顺序则由 {@code stepList} 的顺序表达，避免其他用例插入或移动使批准误过期。</p>
 */
public final class AutomationUiCaseFingerprint {

    public static final String SCHEMA_VERSION = "CASE_FINGERPRINT_V1";

    private static final List<String> JSON_CONFIG_NAMES = List
        .of("playwright_step", "original_playwright_step", "locator_meta", "original_locator_meta", "method_config", "screenshot_focus", "screenshot_focus_rect");

    private AutomationUiCaseFingerprint() {
    }

    public static Fingerprint compute(CaseDO caseDO) {
        if (caseDO == null) {
            throw new IllegalArgumentException("caseDO 不能为空");
        }
        Map<String, Object> canonical = new TreeMap<>();
        put(canonical, "name", caseDO.getName());
        put(canonical, "remark", caseDO.getRemark());
        put(canonical, "cancel", caseDO.getCancel());
        put(canonical, "type", caseDO.getType());
        put(canonical, "status", caseDO.getStatus() == null ? null : caseDO.getStatus().name());
        put(canonical, "executionConfig", executionConfig(caseDO.getExecutionConfig()));
        put(canonical, "origin", origin(caseDO.getOrigin()));

        List<Object> steps = new ArrayList<>();
        if (caseDO.getStepList() != null) {
            for (StepDO step : caseDO.getStepList()) {
                if (step != null) {
                    steps.add(step(step));
                }
            }
        }
        canonical.put("steps", steps);
        String canonicalJson = JSONUtil.toJsonStr(canonical);
        return new Fingerprint(SCHEMA_VERSION, DigestUtil.sha256Hex(canonicalJson), canonicalJson);
    }

    private static Map<String, Object> executionConfig(CaseExecutionConfigDO config) {
        if (config == null) {
            return null;
        }
        Map<String, Object> value = new TreeMap<>();
        put(value, "startUrl", config.getStartUrl());
        put(value, "windowSizeMode", config.getWindowSizeMode());
        put(value, "viewportWidth", config.getViewportWidth());
        put(value, "viewportHeight", config.getViewportHeight());
        put(value, "screenshotMode", config.getScreenshotMode());
        put(value, "pageErrorCheckEnabled", config.getPageErrorCheckEnabled());
        return value;
    }

    private static Map<String, Object> origin(CaseOriginDO origin) {
        if (origin == null) {
            return null;
        }
        Map<String, Object> value = new TreeMap<>();
        put(value, "creationSource", origin.getCreationSource());
        put(value, "originalCaseId", origin.getOriginalCaseId());
        put(value, "initialRecordingId", origin.getInitialRecordingId());
        put(value, "copiedFromCaseId", origin.getCopiedFromCaseId());
        return value;
    }

    private static Map<String, Object> step(StepDO step) {
        Map<String, Object> value = new TreeMap<>();
        put(value, "id", step.getId());
        put(value, "name", step.getName());
        put(value, "remark", step.getRemark());
        put(value, "type", step.getType());
        put(value, "operationType", step.getOperationType());
        put(value, "operationName", step.getOperationName());
        put(value, "operationValue", step.getOperationValue());
        put(value, "setting", step.getSetting());
        put(value, "status", step.getStatus() == null ? null : step.getStatus().name());
        value.put("configList", configs(step.getConfigList()));
        return value;
    }

    private static List<Object> configs(List<StepDO.Config> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<CanonicalConfig> configs = new ArrayList<>();
        for (StepDO.Config config : source) {
            if (config == null || StringUtils.isBlank(config.getParamsName())) {
                continue;
            }
            String name = config.getParamsName();
            Object value = normalizeConfigValue(name, config.getParamsValue());
            if (value != null) {
                configs.add(new CanonicalConfig(name, value, JSONUtil.toJsonStr(value)));
            }
        }
        configs.sort(Comparator.comparing(CanonicalConfig::name).thenComparing(CanonicalConfig::sortValue));
        return configs.stream().map(config -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", config.name());
            value.put("value", config.value());
            return (Object)value;
        }).toList();
    }

    private static Object normalizeConfigValue(String name, String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalizedName = normalizeKey(name);
        if (isInlineScreenshot(normalizedName, rawValue)) {
            return null;
        }
        if (JSON_CONFIG_NAMES.contains(normalizedName) || looksLikeJson(rawValue)) {
            try {
                return normalizeJsonValue(normalizedName, JSONUtil.parse(rawValue));
            } catch (Exception ignored) {
                // 非法 JSON 仍是定义事实，按原始字符串参与指纹并由检查器报告。
            }
        }
        return rawValue;
    }

    private static Object normalizeJsonValue(String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((childKey, childValue) -> {
                String name = String.valueOf(childKey);
                Object child = normalizeJsonValue(name, childValue);
                if (child != null) {
                    normalized.put(name, child);
                }
            });
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) {
                Object child = normalizeJsonValue(key, item);
                if (child != null) {
                    normalized.add(child);
                }
            }
            return normalized;
        }
        if (value instanceof CharSequence text && isInlineScreenshot(normalizeKey(key), String.valueOf(text))) {
            return null;
        }
        return value;
    }

    private static boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}") || trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private static boolean isInlineScreenshot(String normalizedKey, String value) {
        String normalizedValue = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        boolean screenshotKey = normalizedKey.equals("screenshot") || normalizedKey
            .contains("screenshot_base64") || normalizedKey.contains("screenshot_data");
        return screenshotKey || normalizedValue.startsWith("data:image/");
    }

    private static String normalizeKey(String key) {
        return StringUtils.defaultString(key)
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replace('-', '_')
            .toLowerCase(Locale.ROOT);
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private record CanonicalConfig(String name, Object value, String sortValue) {
    }

    public record Fingerprint(String schemaVersion, String hash, String canonicalJson) {
    }
}
