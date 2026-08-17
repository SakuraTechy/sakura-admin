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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 将 Admin 手工步骤组装为 Selenium 旧参数和新执行器 canonical step。
 *
 * <p>前端只提交 method_code、method_version 和 method_config。完整 playwright_step
 * 必须由后端生成；录制步骤没有 method_code 时原样返回，不会被此转换覆盖。</p>
 *
 * @author Codex
 */
@Component
@RequiredArgsConstructor
public class AutomationOperationStepAssembler {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String CATALOG_VERSION = "2026-08-07.1";
    private static final java.util.Set<String> GENERATED_CONFIGS = java.util.Set
        .of("type_code", "type_label", "method_label", "diagnostic_profile", "method_version", "method_config", "action_type", "source", "schema_version", "catalog_version", "canonical_digest", "playwright_step");

    private final ObjectMapper objectMapper;
    private final AutomationOperationCatalogService catalogService;
    private final AutomationOperationConfigValidator configValidator;

    public StepDO assembleManualStep(StepDO step) {
        if (step == null) {
            return null;
        }
        Map<String, String> existing = toConfigMap(step.getConfigList());
        String methodCode = existing.get("method_code");
        if (methodCode == null || methodCode.isBlank()) {
            return step;
        }

        AutomationOperationCatalog.OperationMethod method = catalogService.findMethod(methodCode)
            .orElseThrow(() -> new BusinessException("METHOD_NOT_FOUND：未注册的操作方法 " + methodCode));
        if (!Boolean.TRUE.equals(method.getAuthoringEnabled())) {
            throw new BusinessException("METHOD_ADAPTER_NOT_READY：" + method.getLabel() + "，" + method
                .getDisabledReason());
        }
        int requestedVersion = parseVersion(existing.get("method_version"));
        if (requestedVersion != method.getMethodVersion()) {
            throw new BusinessException("METHOD_VERSION_UNSUPPORTED：" + methodCode + "，请求版本=" + requestedVersion);
        }

        LinkedHashMap<String, Object> methodConfig = parseMethodConfig(existing.get("method_config"));
        configValidator.validate(method, methodConfig);

        LinkedHashMap<String, Object> canonicalStep = buildCanonicalStep(step, method, methodConfig);
        String rawStep = writeJson(canonicalStep);
        String digest = sha256(rawStep);
        Map<String, String> legacyConfigs = buildLegacyConfigs(method, methodConfig, canonicalStep);

        List<StepDO.Config> configs = new ArrayList<>();
        for (StepDO.Config config : safeConfigs(step.getConfigList())) {
            if (config == null || config.getParamsName() == null) {
                continue;
            }
            String name = config.getParamsName();
            if (!GENERATED_CONFIGS.contains(name) && !"method_code".equals(name) && !legacyConfigs.containsKey(name)) {
                configs.add(copyConfig(config));
            }
        }
        putConfig(configs, "method_code", method.getMethodCode());
        var operation = catalogService.findOperation(method.getMethodCode())
            .orElseThrow(() -> new BusinessException("METHOD_NOT_FOUND：未注册的操作方法 " + method.getMethodCode()));
        putConfig(configs, "type_code", operation.typeCode());
        putConfig(configs, "type_label", operation.typeLabel());
        putConfig(configs, "method_label", method.getLabel());
        putConfig(configs, "diagnostic_profile", method.getDiagnosticProfile());
        putConfig(configs, "method_version", String.valueOf(method.getMethodVersion()));
        putConfig(configs, "method_config", writeJson(methodConfig));
        legacyConfigs.forEach((name, value) -> putConfig(configs, name, value));
        putConfig(configs, "action_type", method.getActionType());
        putConfig(configs, "source", "admin-manual");
        putConfig(configs, "schema_version", String.valueOf(method.getMethodVersion()));
        putConfig(configs, "catalog_version", CATALOG_VERSION);
        putConfig(configs, "canonical_digest", digest);
        // 手工步骤执行快照由后端集中生成，Runner/CueCast 仍优先读取该字段。
        putConfig(configs, "playwright_step", rawStep);

        step.setOperationName(method.getLabel());
        step.setOperationValue(method.getLegacyAction());
        step.setConfigList(configs);
        return step;
    }

    private LinkedHashMap<String, Object> buildCanonicalStep(StepDO step,
                                                             AutomationOperationCatalog.OperationMethod method,
                                                             LinkedHashMap<String, Object> methodConfig) {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        if (step.getId() != null && !step.getId().isBlank()) {
            canonical.put("id", step.getId());
        }
        canonical.put("action_type", method.getActionType());
        canonical.putAll(methodConfig);
        applyLocator(canonical, methodConfig);
        applyCanonicalCompatibility(method, canonical);
        canonical.put("description", step.getName());
        canonical.put("source", "admin-manual");
        canonical.put("schema_version", method.getMethodVersion());
        canonical.put("catalog_version", CATALOG_VERSION);
        return canonical;
    }

    private void applyCanonicalCompatibility(AutomationOperationCatalog.OperationMethod method,
                                             LinkedHashMap<String, Object> canonical) {
        String action = method.getActionType();
        if ("navigate".equals(action)) {
            Object url = firstValue(canonical, "url", "value");
            canonical.put("url", stringValue(url));
            canonical.put("value", stringValue(url));
        } else if ("assert_text".equals(action)) {
            canonical.put("value", stringValue(firstValue(canonical, "expect", "value")));
        } else if ("assert_download".equals(action)) {
            // Runner 把 value 作为下载断言契约读取，表单字段需要集中封装，不能降级为普通点击。
            LinkedHashMap<String, Object> expected = new LinkedHashMap<>();
            for (String field : List.of("filename", "mime", "contains", "min_bytes", "max_bytes", "sha256")) {
                Object value = canonical.remove(field);
                if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                    expected.put(field, value);
                }
            }
            canonical.put("value", writeJson(expected));
        } else if ("wait".equals(action)) {
            canonical.put("value", numberOrString(firstValue(canonical, "duration_ms", "value")));
        } else if ("key".equals(action)) {
            canonical.put("value", playwrightKey(canonical));
        }
    }

    private Map<String, String> buildLegacyConfigs(AutomationOperationCatalog.OperationMethod method,
                                                   Map<String, Object> methodConfig,
                                                   Map<String, Object> canonical) {
        LinkedHashMap<String, String> legacy = new LinkedHashMap<>();
        String legacyAction = method.getLegacyAction();
        String locator = legacyLocator(canonical);
        switch (legacyAction) {
            case "web-close", "web-quit", "web-refresh", "switch-window", "return-Iframe", "quit-Iframe", "click-ok",
                "click-cancel" -> {
                // 历史 Handler 不读取参数，保留空投影即可。
            }
            case "web-geturl", "web-geturls" -> putValue(legacy, "value", firstValue(canonical, "url", "value"));
            case "web-getcode" -> {
                putLocator(legacy, locator);
                putValue(legacy, "url", firstValue(methodConfig, "refresh_locator", "url"));
                putValue(legacy, "element", firstValue(methodConfig, "submit_locator", "element"));
                putAssertionOptions(legacy, methodConfig, false);
            }
            case "switch-windows" -> putValue(legacy, "value", firstValue(methodConfig, "index", "value"));
            case "switch-Iframe" -> {
                putValue(legacy, "value", firstValue(methodConfig, "index", "value"));
                putLocator(legacy, locator);
                putSkipOption(legacy, methodConfig);
            }
            case "javascript-executor" -> putValue(legacy, "script", firstValue(methodConfig, "script", "value"));
            case "web-click" -> {
                putLocator(legacy, locator);
                putElementOptions(legacy, methodConfig);
            }
            case "select-click" -> {
                putLocator(legacy, locator);
                putValue(legacy, "value", firstValue(methodConfig, "option_locator", "option", "value"));
                putElementOptions(legacy, methodConfig);
            }
            case "input-click" -> {
                putLocator(legacy, locator);
                putValue(legacy, "value", firstValue(methodConfig, "search_value", "value", "option"));
                putValue(legacy, "element", firstValue(methodConfig, "option_locator", "element"));
                putElementOptions(legacy, methodConfig);
            }
            case "click-text" -> putValue(legacy, "value", methodConfig.get("value"));
            case "web-input" -> {
                putLocator(legacy, locator);
                putValue(legacy, "value", methodConfig.get("value"));
                putElementOptions(legacy, methodConfig);
            }
            case "web-inputdate" -> {
                putLocator(legacy, locator);
                putValue(legacy, "key", firstValue(methodConfig, "format", "key"));
                putValue(legacy, "keys", firstValue(methodConfig, "offset_expression", "keys"));
                putElementOptions(legacy, methodConfig);
            }
            case "web-inputfile", "web-inputfiles" -> {
                putLocator(legacy, locator);
                putValue(legacy, "catalogue", methodConfig.get("catalogue"));
                putValue(legacy, "localpath", firstValue(methodConfig, "localpath", "file_path", "file_ref"));
                putValue(legacy, "delete", firstValue(methodConfig, "delete", "delete_after_upload"));
                putElementOptions(legacy, methodConfig);
            }
            case "web-inputzs" -> {
                putValue(legacy, "device", targetBindingKey(methodConfig.get("target_ref")));
                putLocator(legacy, locator);
                putValue(legacy, "catalogue", methodConfig.get("catalogue"));
                putValue(legacy, "localpath", firstValue(methodConfig, "localpath", "certificate_path", "certificate_ref"));
                putElementOptions(legacy, methodConfig);
            }
            case "web-inputclear" -> {
                putLocator(legacy, locator);
                putElementOptions(legacy, methodConfig);
            }
            case "web-check", "web-notcheck" -> {
                putLocator(legacy, locator);
                putAssertionOptions(legacy, methodConfig, true);
            }
            case "pw-assert-download" -> {
                // 旧 Selenium 链路不支持该动作，仅保留可诊断参数，避免静默降级为普通点击。
                putLocator(legacy, locator);
                putValue(legacy, "value", canonical.get("value"));
            }
            case "web-assert-element-match" -> {
                putLocator(legacy, locator);
                putValue(legacy, "read_mode", methodConfig.get("read_mode"));
                putValue(legacy, "match_mode", methodConfig.get("match_mode"));
                putValue(legacy, "expect", methodConfig.get("expect"));
            }
            case "web-checkvalue" -> {
                putLocator(legacy, locator);
                putValue(legacy, "value", firstValue(methodConfig, "attribute", "value"));
                putAssertionOptions(legacy, methodConfig, true);
            }
            case "web-checkjs" -> {
                putLocator(legacy, locator);
                putValue(legacy, "script", firstValue(methodConfig, "script", "value"));
                putAssertionOptions(legacy, methodConfig, true);
            }
            case "web-checklist" -> {
                // 新表单仅要求结果变量名；旧 Selenium 仍通过 subject EL 读取同一用例的局部变量。
                String variableName = stringValue(firstValue(methodConfig, "variable_name", "result_binding"));
                String subject = stringValue(firstValue(methodConfig, "subject", "value"));
                if (subject.isBlank() && !variableName.isBlank()) {
                    subject = templateVariable(variableName);
                }
                String condition = stringValue(firstValue(methodConfig, "condition", "operator"));
                putDetails(legacy, "condition", condition.isBlank() ? "field" : condition, "subject", subject);
                putAssertionOptions(legacy, methodConfig, true);
            }
            case "web-checksetlist", "web-notchecksetlist" -> {
                putValue(legacy, "value", templateVariable(firstValue(methodConfig, "variable_name", "value")));
                putAssertionOptions(legacy, methodConfig, false);
            }
            case "web-fuzzycheck" -> {
                putLocator(legacy, locator);
                putValue(legacy, "regex", methodConfig.get("regex"));
                putElementOptions(legacy, methodConfig);
            }
            case "wait-forced", "web-implicit" ->
                putValue(legacy, "value", firstValue(methodConfig, "duration_ms", "value"));
            case "web-set" -> {
                putLocator(legacy, locator);
                putValue(legacy, "script", methodConfig.get("script"));
                putValue(legacy, "value", methodConfig.get("value"));
                putValue(legacy, "regex", methodConfig.get("regex"));
                putValue(legacy, "key", methodConfig.get("replace_from"));
                putValue(legacy, "keys", methodConfig.get("replace_to"));
                putDetails(legacy, "key", methodConfig.get("variable_name"));
                putElementOptions(legacy, methodConfig);
            }
            case "web-setdate" -> {
                putValue(legacy, "key", legacyDateMode(methodConfig.get("date_mode")));
                putValue(legacy, "value", methodConfig.get("format"));
                putValue(legacy, "script", firstValue(methodConfig, "datetime", "offset_seconds"));
                putDetails(legacy, "key", methodConfig.get("variable_name"));
            }
            case "web-setsysinfo" -> {
                putValue(legacy, "key", legacySystemInfoKey(methodConfig.get("info_type")));
                putValue(legacy, "value", methodConfig.get("value"));
                putDetails(legacy, "key", methodConfig.get("variable_name"));
            }
            case "web-setusableip" -> {
                putValue(legacy, "value", methodConfig.get("ip_prefix"));
                putDetails(legacy, "start", methodConfig.get("start"), "end", methodConfig
                    .get("end"), "key", methodConfig.get("variable_name"));
            }
            case "web-setproperties" -> {
                putValue(legacy, "key", "jdbc".equals(stringValue(methodConfig.get("profile"))) ? "jdbc" : "");
                putValue(legacy, "value", methodConfig.get("property_key"));
                putDetails(legacy, "key", methodConfig.get("variable_name"));
            }
            case "web-setcalculationformula" -> {
                putValue(legacy, "key", firstValue(methodConfig, "expression", "value"));
                putValue(legacy, "keys", methodConfig.get("legacy_number_pattern"));
                putDetails(legacy, "key", methodConfig.get("variable_name"), "scale", methodConfig
                    .get("scale"), "keepTrailingZeros", methodConfig.get("keep_trailing_zeros"));
            }
            case "windows-keybg", "windows-keybc", "windows-skeybc", "windows-skeybcm" ->
                applyLegacyKey(legacyAction, methodConfig, legacy);
            case "windows-cmd" -> putValue(legacy, "value", firstValue(methodConfig, "command", "value"));
            case "mouse-move", "move-byoffset" -> putDetails(legacy, "x", methodConfig.get("x"), "y", methodConfig
                .get("y"));
            case "move-toelement" -> {
                putLocator(legacy, locator);
                putValue(legacy, "state", methodConfig.get("state"));
                putValue(legacy, "value", methodConfig.get("value"));
                putDetails(legacy, "x", methodConfig.get("x"), "y", methodConfig.get("y"));
                putElementOptions(legacy, methodConfig);
            }
            case "get-file", "delete-file" -> {
                putValue(legacy, "localpath", firstValue(methodConfig, "path", "localpath", "file_ref"));
                putValue(legacy, "delete", "delete-file".equals(legacyAction) ? "true" : methodConfig.get("delete"));
                putResultBindingDetails(legacy, methodConfig);
            }
            case "get-files", "delete-files" -> {
                putValue(legacy, "catalogue", firstValue(methodConfig, "catalogue", "workspace_property"));
                putValue(legacy, "localpath", firstValue(methodConfig, "path", "localpath", "file_ref"));
                putValue(legacy, "delete", "delete-files".equals(legacyAction) ? "true" : methodConfig.get("delete"));
                putResultBindingDetails(legacy, methodConfig);
            }
            case "exe-shell" -> {
                putValue(legacy, "device", targetBindingKey(methodConfig.get("target_ref")));
                putValue(legacy, "shell", firstValue(methodConfig, "command", "shell"));
            }
            case "free-sftp", "free-sftps" -> {
                putValue(legacy, "device", targetBindingKey(methodConfig.get("target_ref")));
                putValue(legacy, "catalogue", methodConfig.get("catalogue"));
                putValue(legacy, "localpath", firstValue(methodConfig, "localpath", "file_path", "file_ref"));
                putValue(legacy, "filetype", methodConfig.get("filetype"));
                putValue(legacy, "value", methodConfig.get("file_name"));
                putValue(legacy, "remotepath", firstValue(methodConfig, "remote_path", "remotepath"));
                putValue(legacy, "delete", methodConfig.get("delete"));
            }
            case "db-insertw", "db-deletew", "db-updatew", "db-queryw", "db-queryws", "db-procedurew" -> {
                putValue(legacy, "device", targetBindingKey(methodConfig.get("target_ref")));
                copyDatabaseLegacyOptions(legacy, methodConfig);
                putValue(legacy, "sql", methodConfig.get("sql"));
                if ("db-queryws".equals(legacyAction)) {
                    putDetails(legacy, "key", firstValue(methodConfig, "variable_name", "result_binding"));
                }
            }
            case "scroll-element" -> {
                putLocator(legacy, locator);
                String elementLocator = stringValue(firstValue(methodConfig, "element_locator", "element"));
                putValue(legacy, "element", elementLocator.isBlank() ? locator : elementLocator);
                putElementOptions(legacy, methodConfig);
            }
            default -> throw new BusinessException("METHOD_CONFIG_INVALID：未定义旧链路参数投影 " + legacyAction);
        }
        return legacy;
    }

    private void putLocator(Map<String, String> legacy, String locator) {
        putValue(legacy, "locator", locator);
    }

    private void putValue(Map<String, String> legacy, String key, Object value) {
        String text = stringValue(value);
        if (!text.isBlank()) {
            legacy.put(key, text);
        }
    }

    private void putElementOptions(Map<String, String> legacy, Map<String, Object> config) {
        putValue(legacy, "message", config.get("message"));
        putValue(legacy, "invisible", config.get("invisible"));
        putSkipOption(legacy, config);
    }

    private void putAssertionOptions(Map<String, String> legacy, Map<String, Object> config, boolean defaultMessage) {
        putValue(legacy, "expect", firstValue(config, "expect", "expected", "value"));
        putValue(legacy, "message", config.get("message"));
        if (defaultMessage) {
            legacy.putIfAbsent("message", "页面文本与预期不一致");
        }
        putValue(legacy, "parseEls", config.get("parse_els"));
        putSkipOption(legacy, config);
    }

    private void putSkipOption(Map<String, String> legacy, Map<String, Object> config) {
        legacy.put("skip", stringValue(firstValue(config, "skip", "skip_mode")).isBlank()
            ? "false"
            : stringValue(firstValue(config, "skip", "skip_mode")));
    }

    private void putDetails(Map<String, String> legacy, Object... pairs) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            String key = stringValue(pairs[index]);
            String value = stringValue(pairs[index + 1]);
            if (!key.isBlank() && !value.isBlank()) {
                values.add(key + ":" + value);
            }
        }
        if (!values.isEmpty()) {
            legacy.put("details", String.join(";", values));
        }
    }

    private void putResultBindingDetails(Map<String, String> legacy, Map<String, Object> config) {
        String variableName = stringValue(firstValue(config, "variable_name", "result_binding"));
        if (!variableName.isBlank()) {
            putDetails(legacy, "key", variableName, "keys", variableName);
        }
    }

    private void copyDatabaseLegacyOptions(Map<String, String> legacy, Map<String, Object> config) {
        for (String option : List.of("datatype", "dataenviron", "port", "database")) {
            putValue(legacy, option, config.get(option));
        }
    }

    private String targetBindingKey(Object targetRef) {
        if (targetRef instanceof Map<?, ?> target) {
            return stringValue(firstValue(target, "binding_key", "bindingKey", "device", "name", "id"));
        }
        return stringValue(targetRef);
    }

    private String templateVariable(Object value) {
        String name = stringValue(value);
        if (name.isBlank() || name.startsWith("${")) {
            return name;
        }
        return "${" + name + "}";
    }

    private String legacyDateMode(Object value) {
        return switch (stringValue(value)) {
            case "timestamp" -> "获取当前时间戳";
            case "custom_datetime" -> "获取自定义时间";
            default -> "获取当天日期时间";
        };
    }

    private String legacySystemInfoKey(Object value) {
        return switch (stringValue(value)) {
            case "host_ip" -> "主机IP";
            case "host_name" -> "主机名";
            case "os_name" -> "操作系统";
            case "os_version" -> "操作系统版本";
            case "os_arch" -> "系统架构";
            case "system_date" -> "当前日期";
            case "current_user" -> "当前用户";
            case "user_home" -> "用户目录";
            case "working_directory" -> "工作目录";
            default -> stringValue(value);
        };
    }

    private void applyLegacyKey(String legacyAction, Map<String, Object> methodConfig, Map<String, String> legacy) {
        String key = stringValue(methodConfig.get("key"));
        String modifier = stringValue(methodConfig.get("modifier"));
        List<String> modifiers = stringList(methodConfig.get("modifiers"));
        if ("windows-keybg".equals(legacyAction)) {
            legacy.put("key", key);
        } else if ("windows-skeybcm".equals(legacyAction)) {
            legacy.put("key", modifiers.size() > 0 ? modifiers.get(0) : modifier);
            legacy.put("keys", modifiers.size() > 1 ? modifiers.get(1) : "");
            legacy.put("value", key);
        } else {
            legacy.put("key", modifier);
            legacy.put("keys", key);
        }
    }

    private void applyLocator(Map<String, Object> canonical, Map<String, Object> methodConfig) {
        Object targetRef = methodConfig.get("target_ref");
        if (targetRef instanceof Map<?, ?> target) {
            putIfText(canonical, "target_selector", firstValue(target, "target_selector", "selector", "css"));
            putIfText(canonical, "target_xpath", firstValue(target, "target_xpath", "xpath"));
            Object locatorMeta = firstValue(target, "locator_meta", "meta");
            if (locatorMeta != null) {
                canonical.put("locator_meta", locatorMeta);
            }
            String strategy = stringValue(firstValue(target, "strategy", "type"));
            String value = stringValue(firstValue(target, "value", "locator_value", "locatorValue"));
            if (!strategy.isBlank() || !value.isBlank()) {
                applyTypedLocator(canonical, strategy, value, Boolean.TRUE.equals(target.get("exact")));
            }
        } else if (targetRef != null) {
            applyLegacyLocator(canonical, stringValue(targetRef));
        }
        putIfText(canonical, "target_selector", methodConfig.get("target_selector"));
        putIfText(canonical, "target_xpath", methodConfig.get("target_xpath"));
        if (!canonical.containsKey("target_selector") && !canonical.containsKey("target_xpath")) {
            applyLegacyLocator(canonical, stringValue(methodConfig.get("locator")));
        }
    }

    private void applyLegacyLocator(Map<String, Object> canonical, String locator) {
        if (locator.isBlank()) {
            return;
        }
        String normalized = locator.trim();
        String strategy = locatorStrategy(normalized);
        if (isUnsupportedLocatorStrategy(strategy) || isExecutableLocator(normalized)) {
            throw unsupportedLocator(strategy.isBlank() ? "script" : strategy);
        }
        if (normalized.regionMatches(true, 0, "xpath=", 0, 6)) {
            canonical.put("target_xpath", normalized.substring(6).trim());
        } else if (normalized.startsWith("/") || normalized.startsWith("(") || normalized.startsWith(".//")) {
            canonical.put("target_xpath", normalized);
        } else if (normalized.regionMatches(true, 0, "css=", 0, 4)) {
            canonical.put("target_selector", normalized.substring(4).trim());
        } else if (List.of("text", "role", "label", "placeholder", "testid").contains(strategy)) {
            applyTypedLocator(canonical, strategy, normalized.substring(normalized.indexOf('=') + 1).trim(), true);
        } else if (!strategy.isBlank()) {
            throw unsupportedLocator(strategy);
        } else {
            // 历史步骤可能保存无前缀 CSS，继续兼容但新建步骤由前端始终提交明确策略。
            canonical.put("target_selector", normalized);
        }
    }

    private void applyTypedLocator(Map<String, Object> canonical, String rawStrategy, String value, boolean exact) {
        String strategy = rawStrategy == null ? "" : rawStrategy.trim().toLowerCase(java.util.Locale.ROOT);
        if (strategy.isBlank() || value.isBlank()) {
            throw new BusinessException("METHOD_CONFIG_INVALID：定位策略和值不能为空");
        }
        if (isUnsupportedLocatorStrategy(strategy)) {
            throw unsupportedLocator(strategy);
        }
        switch (strategy) {
            case "css" -> canonical.put("target_selector", value);
            case "xpath" -> canonical.put("target_xpath", value);
            case "text" -> {
                canonical
                    .put("target_xpath", "//*[self::button or self::a or self::label or self::li or @role][normalize-space(.)=" + xpathLiteral(value) + "] | //*[self::span or self::div or self::p][normalize-space(.)=" + xpathLiteral(value) + " and not(.//*[normalize-space(.)=" + xpathLiteral(value) + "])]");
                addLocatorCandidate(canonical, "text_exact", value, 1D, Map.of("exact", exact));
            }
            case "role" -> {
                String selector = "[role=\"" + cssAttributeValue(value) + "\"]";
                canonical.put("target_selector", selector);
                addLocatorCandidate(canonical, "css_attr_role", selector, 1D, Map.of("role", value));
            }
            case "label" -> {
                String literal = xpathLiteral(value);
                canonical
                    .put("target_xpath", "//*[@id=//label[normalize-space(.)=" + literal + "]/@for] | //label[normalize-space(.)=" + literal + "]//*[self::input or self::textarea or self::select or self::button]");
                addLocatorCandidate(canonical, "xpath_fallback", stringValue(canonical.get("target_xpath")), 1D, Map
                    .of("label_text", value, "exact", exact));
            }
            case "placeholder" -> {
                String selector = "[placeholder=\"" + cssAttributeValue(value) + "\"]";
                canonical.put("target_selector", selector);
                addLocatorCandidate(canonical, "css_attr_placeholder", selector, 1D, Map.of("placeholder", value));
            }
            case "testid" -> {
                String escaped = cssAttributeValue(value);
                String selector = "[data-testid=\"" + escaped + "\"],[data-test=\"" + escaped + "\"],[data-qa=\"" + escaped + "\"],[data-cy=\"" + escaped + "\"]";
                canonical.put("target_selector", selector);
                addLocatorCandidate(canonical, "css_attr_data-cy", "[data-cy=\"" + escaped + "\"]", 0.97D, Map
                    .of("test_id", value));
                addLocatorCandidate(canonical, "css_attr_data-qa", "[data-qa=\"" + escaped + "\"]", 0.98D, Map
                    .of("test_id", value));
                addLocatorCandidate(canonical, "css_attr_data-test", "[data-test=\"" + escaped + "\"]", 0.99D, Map
                    .of("test_id", value));
                addLocatorCandidate(canonical, "css_attr_data-testid", "[data-testid=\"" + escaped + "\"]", 1D, Map
                    .of("test_id", value));
            }
            default -> throw unsupportedLocator(strategy);
        }
    }

    @SuppressWarnings("unchecked")
    private void addLocatorCandidate(Map<String, Object> canonical,
                                     String type,
                                     String value,
                                     double score,
                                     Map<String, Object> contextPatch) {
        // locator_meta 是跨执行器定位事实，不能只保留投影后的 CSS/XPath。
        LinkedHashMap<String, Object> meta = canonical.get("locator_meta") instanceof Map<?, ?> rawMeta
            ? new LinkedHashMap<>((Map<String, Object>)rawMeta)
            : new LinkedHashMap<>();
        meta.putIfAbsent("version", 1);
        List<Object> candidates = meta.get("candidates") instanceof java.util.Collection<?> rawCandidates
            ? new ArrayList<>(rawCandidates)
            : new ArrayList<>();
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("type", type);
        candidate.put("value", value);
        candidate.put("score", score);
        candidates.add(0, candidate);
        meta.put("candidates", candidates);
        LinkedHashMap<String, Object> context = meta.get("context") instanceof Map<?, ?> rawContext
            ? new LinkedHashMap<>((Map<String, Object>)rawContext)
            : new LinkedHashMap<>();
        context.putAll(contextPatch);
        meta.put("context", context);
        canonical.put("locator_meta", meta);
    }

    private String locatorStrategy(String locator) {
        int delimiter = locator.indexOf('=');
        return delimiter <= 0 ? "" : locator.substring(0, delimiter).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isUnsupportedLocatorStrategy(String strategy) {
        return List.of("jquery", "js", "js_path", "jspath", "testrigor").contains(strategy);
    }

    private boolean isExecutableLocator(String locator) {
        return locator.matches("(?is)^\\$\\s*\\(.*") || locator.matches("(?is)^jquery\\s*\\(.*") || locator
            .matches("(?is)^(?:document|window)\\s*\\.\\s*(?:querySelector|querySelectorAll)\\s*\\(.*");
    }

    private BusinessException unsupportedLocator(String strategy) {
        return new BusinessException("LOCATOR_STRATEGY_UNSUPPORTED：不支持的定位策略 " + strategy);
    }

    private String cssAttributeValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return "concat('" + value.replace("'", "',\"'\",'") + "')";
    }

    private String legacyLocator(Map<String, Object> canonical) {
        String xpath = stringValue(canonical.get("target_xpath"));
        if (!xpath.isBlank()) {
            return "xpath=" + xpath;
        }
        String selector = stringValue(canonical.get("target_selector"));
        return selector.isBlank() ? "" : "css=" + selector;
    }

    private LinkedHashMap<String, Object> parseMethodConfig(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException("METHOD_CONFIG_INVALID：method_config 不是合法 JSON");
        }
    }

    private int parseVersion(String version) {
        try {
            return Integer.parseInt(version);
        } catch (Exception e) {
            throw new BusinessException("METHOD_VERSION_UNSUPPORTED：method_version 必须为整数");
        }
    }

    private String playwrightKey(Map<String, Object> config) {
        List<String> values = new ArrayList<>();
        values.addAll(stringList(config.get("modifiers")));
        String modifier = stringValue(config.get("modifier"));
        if (!modifier.isBlank()) {
            values.add(modifier);
        }
        String key = stringValue(config.get("key"));
        if (!key.isBlank()) {
            values.add(key);
        }
        return String.join("+", new LinkedHashSet<>(values));
    }

    private List<String> stringList(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(this::stringValue).filter(item -> !item.isBlank()).toList();
        }
        String text = stringValue(value);
        if (text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[+,]")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private Object firstValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !stringValue(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void putIfText(Map<String, Object> target, String key, Object value) {
        if (value != null && !stringValue(value).isBlank()) {
            target.put(key, value);
        }
    }

    private Object numberOrString(Object value) {
        if (value instanceof Number) {
            return value;
        }
        String text = stringValue(value);
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("canonical step 摘要计算失败", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("METHOD_CONFIG_INVALID：步骤 JSON 生成失败");
        }
    }

    private Map<String, String> toConfigMap(List<StepDO.Config> configs) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (StepDO.Config config : safeConfigs(configs)) {
            if (config != null && config.getParamsName() != null) {
                result.put(config.getParamsName(), config.getParamsValue());
            }
        }
        return result;
    }

    private List<StepDO.Config> safeConfigs(List<StepDO.Config> configs) {
        return configs == null ? List.of() : configs;
    }

    private StepDO.Config copyConfig(StepDO.Config source) {
        StepDO.Config target = new StepDO.Config();
        target.setParamsName(source.getParamsName());
        target.setParamsValue(source.getParamsValue());
        return target;
    }

    private void putConfig(List<StepDO.Config> configs, String name, String value) {
        configs.removeIf(item -> item != null && name.equals(item.getParamsName()));
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value == null ? "" : value);
        configs.add(config);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
