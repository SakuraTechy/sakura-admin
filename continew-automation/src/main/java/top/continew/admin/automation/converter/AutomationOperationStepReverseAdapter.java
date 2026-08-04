/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package top.continew.admin.automation.converter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationOperationCatalogService;

/**
 * 将历史 StepDO 反向转换为目录方法配置，仅用于回显和用户显式编辑。
 *
 * <p>该组件绝不修改传入的 StepDO，也不在读取场景时回写数据库。不能唯一还原的目标或敏感值只进入
 * warning，保存时仍须由正向 Assembler 和服务端校验重新确认。</p>
 */
@Component
@RequiredArgsConstructor
public class AutomationOperationStepReverseAdapter {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> SENSITIVE_NAMES = Set.of("password", "passwd", "pwd", "secret", "token",
        "private_key", "privatekey", "pass_word");

    private final ObjectMapper objectMapper;
    private final AutomationOperationCatalogService catalogService;

    public ReverseResult adapt(StepDO step) {
        if (step == null) {
            return new ReverseResult(false, "", null, Map.of(), List.of("步骤为空，无法反向解析"));
        }
        Map<String, String> configs = configMap(step.getConfigList());
        String methodCode = text(configs.get("method_code"));
        if (!methodCode.isBlank() && configs.containsKey("method_config")) {
            Map<String, Object> existing = parseMap(configs.get("method_config"));
            if (existing != null && catalogService.findMethod(methodCode).isPresent()) {
                return new ReverseResult(true, methodCode, parseVersion(configs.get("method_version")), existing,
                    List.of());
            }
        }

        String rawStep = text(configs.get("playwright_step"));
        if ("sakura-playwright".equalsIgnoreCase(configs.get("source")) || !rawStep.isBlank()) {
            ReverseResult rawResult = fromRaw(rawStep);
            if (rawResult.recognized()) {
                return rawResult;
            }
        }

        AutomationOperationCatalog.OperationMethod method = catalogService.findMethod(step.getOperationValue()).orElse(null);
        if (method == null) {
            return new ReverseResult(false, "", null, Map.of(), List.of("无法唯一识别历史 operationValue："
                + text(step.getOperationValue())));
        }
        List<String> warnings = new ArrayList<>();
        LinkedHashMap<String, Object> methodConfig = projectLegacyConfig(method, configs, warnings);
        return new ReverseResult(true, method.getMethodCode(), method.getMethodVersion(), methodConfig, List.copyOf(warnings));
    }

    private ReverseResult fromRaw(String rawStep) {
        Map<String, Object> raw = parseMap(rawStep);
        if (raw == null) {
            return new ReverseResult(false, "", null, Map.of(), List.of("playwright_step 不是合法 JSON，不能覆盖历史字段"));
        }
        String actionType = text(raw.get("action_type"));
        AutomationOperationCatalog.OperationMethod method = catalogService.findMethod(actionType).orElse(null);
        if (method == null) {
            return new ReverseResult(false, "", null, Map.of(), List.of("raw step action_type 未注册：" + actionType));
        }
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!Set.of("id", "action_type", "description", "source", "schema_version", "catalog_version",
                "step_index").contains(entry.getKey())) {
                config.put(entry.getKey(), entry.getValue());
            }
        }
        return new ReverseResult(true, method.getMethodCode(), method.getMethodVersion(), config, List.of());
    }

    private LinkedHashMap<String, Object> projectLegacyConfig(AutomationOperationCatalog.OperationMethod method,
                                                               Map<String, String> legacy,
                                                               List<String> warnings) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String name : legacy.keySet()) {
            if (SENSITIVE_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                warnings.add("敏感字段 " + name + " 未回填，请重新选择安全引用");
            }
        }
        for (Map<String, Object> field : method.getFormSchema()) {
            String name = text(field.get("name"));
            String value = legacyValue(name, legacy);
            if (value.isBlank()) {
                continue;
            }
            if (SENSITIVE_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                warnings.add("敏感字段 " + name + " 未回填，请重新选择安全引用");
                continue;
            }
            result.put(name, value);
        }
        String locator = first(legacy, "locator", "target_selector", "target_xpath");
        if (!locator.isBlank() && hasField(method, "target_ref") && !result.containsKey("target_ref")) {
            result.put("target_ref", locatorReference(locator));
            warnings.add("历史 locator 已还原为 target_ref，locator_meta 未伪造");
        }
        if (requiresTargetRef(method) && !result.containsKey("target_ref")) {
            String device = first(legacy, "device", "target_config_id");
            if (!device.isBlank()) {
                result.put("target_ref", targetReference(method.getActionType(), device));
                warnings.add("历史目标仅保留引用，保存前必须重新确认项目配置");
            }
        }
        if (legacy.containsKey("details")) {
            Map<String, String> details = parseDetails(legacy.get("details"));
            if (!result.containsKey("variable_name")) {
                String variable = first(details, "key", "variable_name");
                if (!variable.isBlank() && hasField(method, "variable_name")) {
                    result.put("variable_name", variable);
                }
            }
        }
        return result;
    }

    private String legacyValue(String field, Map<String, String> legacy) {
        String direct = text(legacy.get(field));
        if (!direct.isBlank()) {
            return canonicalDateMode(field, direct);
        }
        return switch (field) {
            case "url" -> first(legacy, "url", "value");
            case "value" -> first(legacy, "value", "expect");
            case "expect" -> first(legacy, "expect", "value");
            case "duration_ms" -> first(legacy, "duration_ms", "waitTime", "value");
            case "command" -> first(legacy, "command", "shell", "value");
            case "path", "file_ref", "certificate_path" -> first(legacy, field, "localpath", "value");
            case "remote_path" -> first(legacy, "remote_path", "remotepath");
            case "profile" -> first(legacy, "profile", "dataenviron");
            case "property_key" -> first(legacy, "property_key", "value");
            case "sql" -> first(legacy, "sql", "value");
            case "index" -> first(legacy, "index", "value");
            case "script" -> first(legacy, "script", "value");
            case "key" -> first(legacy, "key", "keys", "value");
            default -> "";
        };
    }

    private String canonicalDateMode(String field, String value) {
        if (!"date_mode".equals(field)) {
            return value;
        }
        return switch (value) {
            case "获取当前时间戳", "timestamp" -> "timestamp";
            case "获取自定义时间", "custom_datetime" -> "custom_datetime";
            default -> "today";
        };
    }

    private Map<String, Object> locatorReference(String locator) {
        String value = locator.trim();
        if (value.regionMatches(true, 0, "xpath=", 0, 6)) {
            return Map.of("scope", "page", "target_xpath", value.substring(6));
        }
        if (value.regionMatches(true, 0, "css=", 0, 4)) {
            return Map.of("scope", "page", "target_selector", value.substring(4));
        }
        return Map.of("scope", "page", "target_selector", value);
    }

    private Map<String, Object> targetReference(String actionType, String reference) {
        String kind = actionType.startsWith("database") ? "database" : "server";
        if (reference.matches("[1-9][0-9]*")) {
            return Map.of("scope", "project_config", "kind", kind, "config_id", Long.valueOf(reference));
        }
        return Map.of("scope", "project_config", "kind", kind, "binding_key", reference);
    }

    private boolean hasField(AutomationOperationCatalog.OperationMethod method, String name) {
        return method.getFormSchema().stream().anyMatch(field -> name.equals(text(field.get("name"))));
    }

    private boolean requiresTargetRef(AutomationOperationCatalog.OperationMethod method) {
        return hasField(method, "target_ref") || Set.of("server_command", "server_file_upload", "database_sql",
            "database_native").contains(method.getActionType());
    }

    private Map<String, String> configMap(List<StepDO.Config> configs) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (configs != null) {
            for (StepDO.Config config : configs) {
                if (config != null && config.getParamsName() != null) {
                    result.put(config.getParamsName(), config.getParamsValue());
                }
            }
        }
        return result;
    }

    private Map<String, String> parseDetails(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String part : text(raw).split("[;丨]")) {
            String[] pair = part.split(":", 2);
            if (pair.length == 2 && !pair[0].isBlank()) {
                result.put(pair[0].trim(), pair[1].trim());
            }
        }
        return result;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseVersion(String version) {
        try {
            return Integer.valueOf(version);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String first(Map<String, String> source, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ReverseResult(boolean recognized,
                                String methodCode,
                                Integer methodVersion,
                                Map<String, Object> methodConfig,
                                List<String> warnings) {
    }
}
