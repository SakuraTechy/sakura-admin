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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.starter.core.exception.BusinessException;

/**
 * 手工步骤配置的跨执行器安全校验。
 *
 * <p>这里只校验目录可表达的公共语义。执行节点凭据、运行时属性值和文件内容均不能进入 method_config，
 * 它们必须通过 target_ref、file_ref 或 Admin 运行时解析。</p>
 *
 * @author Codex
 */
@Component
public class AutomationOperationConfigValidator {

    private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]{0,127}$");
    private static final Pattern VARIABLE_REFERENCE = Pattern
        .compile("^[A-Za-z_][A-Za-z0-9_.-]*(?:(?:\\.[A-Za-z_][A-Za-z0-9_-]*)|(?:\\[(?:\\d+|\"[^\"]+\"|'[^']+')]))*$");
    private static final Pattern TEMPLATE_REFERENCE = Pattern.compile("\\$\\{([^{}]+)}");
    private static final Pattern FORMULA_REMAINDER = Pattern.compile("^[0-9+\\-*/%().\\s]+$");
    private static final Set<String> SENSITIVE_NAMES = Set
        .of("password", "passwd", "secret", "token", "privatekey", "credential", "certificate");
    private static final List<String> RESERVED_VARIABLE_PREFIXES = List.of("system.", "secret.", "execution.");
    private static final Set<String> SERVER_TARGET_ACTIONS = Set.of("server_command", "server_file_upload");
    private static final Set<String> DATABASE_TARGET_ACTIONS = Set.of("database_sql", "database_native");
    private static final Map<String, Set<String>> INFRASTRUCTURE_COMPATIBILITY_FIELDS = Map.of("server_command", Set
        .of("shell", "timeout_ms"), "database_sql", Set.of("sql_mode", "timeout_ms"), "database_native", Set
            .of("mongo_operation", "collection", "filter", "document", "timeout_ms"));

    public void validate(AutomationOperationCatalog.OperationMethod method, Map<String, Object> config) {
        rejectPlaintextSecrets(config);
        normalizeCompatibilityFields(method, config);
        validateDeclaredFields(method, config);
        validateRequiredFields(method, config);
        validateFieldValues(method, config);
        validateInfrastructureTargetRef(method, config);
        validateRegex(config);
        validateVariableName(method.getActionType(), config);
        validateDateFormat(method.getActionType(), config);
        validateFormula(method.getActionType(), config);
        validateIpRange(method.getActionType(), config);
    }

    private void normalizeCompatibilityFields(AutomationOperationCatalog.OperationMethod method,
                                              Map<String, Object> config) {
        if (!"server_command".equals(method.getActionType())) {
            return;
        }
        if (!config.containsKey("shell")) {
            if (!config.containsKey("shell_type")) {
                return;
            }
            // 兼容早期字段名，统一迁移到当前 Shell 内部字段。
            config.put("shell", config.remove("shell_type"));
        }
        String normalized = normalizeShellValue(config.get("shell"));
        if (normalized != null && !normalized.equals(stringValue(config.get("shell")))) {
            // 兼容旧表单把展示文本直接写入配置，但执行快照统一使用内部值。
            config.put("shell", normalized);
        }
    }

    private String normalizeShellValue(Object value) {
        String normalized = stringValue(value).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "bash", "/bin/bash", "linux", "shell", "shell 类型", "shell type", "脚本类型", "shell 脚本类型" -> "bash";
            case "sh", "/bin/sh" -> "sh";
            case "powershell", "powershell.exe", "pwsh", "power shell" -> "powershell";
            default -> null;
        };
    }

    private void validateRequiredFields(AutomationOperationCatalog.OperationMethod method, Map<String, Object> config) {
        for (Map<String, Object> field : method.getFormSchema()) {
            if (!isFieldVisible(field, config)) {
                continue;
            }
            String name = stringValue(field.get("name"));
            boolean required = Boolean.TRUE.equals(field.get("required")) || conditionMatches(field
                .get("required_when"), config);
            if (!required) {
                continue;
            }
            Object value = config.get(name);
            if (isEmpty(value)) {
                throw new BusinessException("METHOD_CONFIG_INVALID：" + method
                    .getLabel() + " 缺少必填参数“" + fieldLabel(field) + "”");
            }
        }
    }

    private void validateDeclaredFields(AutomationOperationCatalog.OperationMethod method, Map<String, Object> config) {
        Map<String, Map<String, Object>> declaredFields = fieldMap(method);
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Map<String, Object> field = declaredFields.get(entry.getKey());
            if (field == null) {
                if (infrastructureCompatibilityFields(method).contains(entry.getKey())) {
                    validateInfrastructureCompatibilityField(method, entry.getKey(), entry.getValue());
                    continue;
                }
                throw new BusinessException("METHOD_CONFIG_INVALID：" + method.getLabel() + " 包含未声明参数“" + entry
                    .getKey() + "”");
            }
            if (!isFieldVisible(field, config)) {
                throw new BusinessException("METHOD_CONFIG_INVALID：" + method
                    .getLabel() + " 当前选项不允许参数“" + fieldLabel(field) + "”");
            }
        }
    }

    private Set<String> infrastructureCompatibilityFields(AutomationOperationCatalog.OperationMethod method) {
        return INFRASTRUCTURE_COMPATIBILITY_FIELDS.getOrDefault(method.getActionType(), Set.of());
    }

    private void validateInfrastructureCompatibilityField(AutomationOperationCatalog.OperationMethod method,
                                                          String name,
                                                          Object value) {
        if ("timeout_ms".equals(name)) {
            double timeout;
            try {
                timeout = Double.parseDouble(stringValue(value));
            } catch (NumberFormatException e) {
                throw new BusinessException("METHOD_CONFIG_INVALID：" + method.getLabel() + " 参数“执行超时”必须是数字");
            }
            if (!Double.isFinite(timeout) || timeout < 1000 || timeout > 600000) {
                throw new BusinessException("METHOD_CONFIG_INVALID：" + method
                    .getLabel() + " 参数“执行超时”必须在 1000-600000 毫秒之间");
            }
        } else if ("shell".equals(name) && normalizeShellValue(value) == null) {
            throw new BusinessException("METHOD_CONFIG_INVALID：" + method.getLabel() + " 参数“Shell 类型”不是有效选项");
        } else if ("sql_mode".equals(name) && !Set.of("query", "update", "call").contains(stringValue(value))) {
            throw new BusinessException("METHOD_CONFIG_INVALID：" + method.getLabel() + " 参数“SQL 类型”不是有效选项");
        } else if ("mongo_operation".equals(name) && !Set.of("find", "insert", "update", "delete")
            .contains(stringValue(value))) {
            throw new BusinessException("METHOD_CONFIG_INVALID：" + method.getLabel() + " 参数“MongoDB 操作”不是有效选项");
        }
    }

    private void validateFieldValues(AutomationOperationCatalog.OperationMethod method, Map<String, Object> config) {
        for (Map<String, Object> field : method.getFormSchema()) {
            if (!isFieldVisible(field, config)) {
                continue;
            }
            String name = stringValue(field.get("name"));
            Object value = config.get(name);
            if (isEmpty(value)) {
                continue;
            }
            String component = stringValue(field.get("component"));
            if ("number".equals(component)) {
                validateNumberField(method, field, value);
            } else if ("select".equals(component)) {
                validateSelectField(method, field, value);
            }
        }
    }

    private void validateNumberField(AutomationOperationCatalog.OperationMethod method,
                                     Map<String, Object> field,
                                     Object value) {
        if (!(value instanceof Number numericValue)) {
            throw invalidField(method, field, "必须是数字");
        }
        double number = numericValue.doubleValue();
        if (!Double.isFinite(number)) {
            throw invalidField(method, field, "必须是有限数字");
        }
        if (field.get("min") instanceof Number min && number < min.doubleValue()) {
            throw invalidField(method, field, "不能小于 " + min);
        }
        if (field.get("max") instanceof Number max && number > max.doubleValue()) {
            throw invalidField(method, field, "不能大于 " + max);
        }
    }

    private void validateSelectField(AutomationOperationCatalog.OperationMethod method,
                                     Map<String, Object> field,
                                     Object value) {
        Set<String> allowedValues = new HashSet<>();
        Object rawOptions = field.get("options");
        if (rawOptions instanceof Collection<?> options) {
            for (Object option : options) {
                if (option instanceof Map<?, ?> optionMap) {
                    allowedValues.add(stringValue(optionMap.get("value")));
                }
            }
        }
        if (!allowedValues.contains(stringValue(value))) {
            throw invalidField(method, field, "不是有效选项");
        }
    }

    private BusinessException invalidField(AutomationOperationCatalog.OperationMethod method,
                                           Map<String, Object> field,
                                           String reason) {
        return new BusinessException("METHOD_CONFIG_INVALID：" + method
            .getLabel() + " 参数“" + fieldLabel(field) + "”" + reason);
    }

    private Map<String, Map<String, Object>> fieldMap(AutomationOperationCatalog.OperationMethod method) {
        Map<String, Map<String, Object>> fields = new HashMap<>();
        for (Map<String, Object> field : method.getFormSchema()) {
            fields.put(stringValue(field.get("name")), field);
        }
        return fields;
    }

    private boolean isFieldVisible(Map<String, Object> field, Map<String, Object> config) {
        return !field.containsKey("visible_when") || conditionMatches(field.get("visible_when"), config);
    }

    private boolean conditionMatches(Object rawCondition, Map<String, Object> config) {
        if (!(rawCondition instanceof Map<?, ?> condition) || condition.isEmpty()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : condition.entrySet()) {
            String actual = stringValue(config.get(stringValue(entry.getKey())));
            Object expected = entry.getValue();
            if (expected instanceof Collection<?> values) {
                if (values.stream().map(this::stringValue).noneMatch(actual::equals)) {
                    return false;
                }
            } else if (!actual.equals(stringValue(expected))) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty(Object value) {
        return value == null || value instanceof String text && text
            .isBlank() || value instanceof Collection<?> collection && collection
                .isEmpty() || value instanceof Map<?, ?> map && map.isEmpty();
    }

    private String fieldLabel(Map<String, Object> field) {
        String label = stringValue(field.get("label"));
        return label.isBlank() ? stringValue(field.get("name")) : label;
    }

    /**
     * 基础设施目标是路由事实来源，不能只依赖通用的“字段存在”校验。
     * 新建步骤使用环境资源角色；config_id 和 binding_key 仅为历史步骤保留兼容入口。
     */
    private void validateInfrastructureTargetRef(AutomationOperationCatalog.OperationMethod method,
                                                 Map<String, Object> config) {
        String actionType = method.getActionType();
        String expectedKind = SERVER_TARGET_ACTIONS.contains(actionType)
            ? "server"
            : DATABASE_TARGET_ACTIONS.contains(actionType) ? "database" : null;
        if (expectedKind == null) {
            return;
        }

        Object rawTargetRef = config.get("target_ref");
        if (!(rawTargetRef instanceof Map<?, ?> targetRef)) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：" + method.getLabel() + " 的 target_ref 必须是对象");
        }
        String scope = stringValue(targetRef.get("scope"));
        String actualKind = stringValue(targetRef.get("kind"));
        if (!expectedKind.equals(actualKind)) {
            throw new BusinessException("INFRA_TARGET_KIND_MISMATCH：" + method.getLabel() + " 需要 kind=" + expectedKind);
        }

        if ("project_environment".equals(scope)) {
            String slotId = stringValue(targetRef.get("slot_id"));
            try {
                if (Long.parseLong(slotId) <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new BusinessException("INFRA_TARGET_REF_INVALID：target_ref.slot_id 必须为正数");
            }
            return;
        }
        if (!"project_config".equals(scope)) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：" + method
                .getLabel() + " 的 target_ref.scope 必须为 project_environment 或 project_config");
        }

        String configId = stringValue(targetRef.get("config_id"));
        String bindingKey = stringValue(targetRef.get("binding_key"));
        if (configId.isBlank() && bindingKey.isBlank()) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：" + method
                .getLabel() + " 的 target_ref 必须包含正数 config_id 或 binding_key");
        }
        if (!configId.isBlank()) {
            try {
                if (Long.parseLong(configId) <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new BusinessException("INFRA_TARGET_REF_INVALID：target_ref.config_id 必须为正数");
            }
        }
    }

    private void rejectPlaintextSecrets(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = String.valueOf(entry.getKey());
                String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (SENSITIVE_NAMES.contains(normalized)) {
                    throw new BusinessException("METHOD_CONFIG_INVALID：敏感值必须保存引用，禁止直接写入 " + name);
                }
                rejectPlaintextSecrets(entry.getValue());
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                rejectPlaintextSecrets(item);
            }
        }
    }

    private void validateRegex(Map<String, Object> config) {
        String regex = stringValue(config.get("regex"));
        if (regex.isBlank()) {
            return;
        }
        if (regex.length() > 512) {
            throw new BusinessException("METHOD_CONFIG_INVALID：正则表达式长度不能超过 512");
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new BusinessException("METHOD_CONFIG_INVALID：正则表达式不合法");
        }
    }

    private void validateVariableName(String actionType, Map<String, Object> config) {
        String variableName = stringValue(config.get("variable_name"));
        if (variableName.isBlank()) {
            return;
        }
        boolean isVariableReference = Set
            .of("assert_variable_list", "assert_variable_list_not", "assert_database_value")
            .contains(actionType);
        if (!(isVariableReference ? VARIABLE_REFERENCE : VARIABLE_NAME).matcher(variableName).matches()) {
            throw new BusinessException("VARIABLE_NAME_INVALID：变量名不合法");
        }
        if (RESERVED_VARIABLE_PREFIXES.stream().anyMatch(variableName::startsWith)) {
            throw new BusinessException("VARIABLE_NAME_INVALID：变量名不允许使用保留前缀");
        }
    }

    private void validateDateFormat(String actionType, Map<String, Object> config) {
        if (!"global_variable_date".equals(actionType)) {
            return;
        }
        String format = stringValue(config.get("format"));
        if (format.isBlank()) {
            return;
        }
        String unsupported = format.replaceAll("yyyy|SSS|MM|dd|HH|mm|ss|M|d|H|m|s", "");
        if (unsupported.matches(".*[A-Za-z].*")) {
            throw new BusinessException("METHOD_CONFIG_INVALID：日期格式仅支持 yyyy、MM、dd、HH、mm、ss、SSS");
        }
    }

    private void validateFormula(String actionType, Map<String, Object> config) {
        if (!"global_variable_formula".equals(actionType) && !"calculation".equals(actionType)) {
            return;
        }
        String expression = stringValue(config.get("expression"));
        String remainder = TEMPLATE_REFERENCE.matcher(expression).replaceAll("");
        if (expression.isBlank() || !FORMULA_REMAINDER.matcher(remainder).matches()) {
            throw new BusinessException("VARIABLE_EXPRESSION_INVALID：计算公式只允许四则运算、括号和变量引用");
        }
    }

    private void validateIpRange(String actionType, Map<String, Object> config) {
        if (!"global_variable_available_ip".equals(actionType)) {
            return;
        }
        int start = numberValue(config.get("start"));
        int end = numberValue(config.get("end"));
        if (start < 1 || end > 254 || start > end) {
            throw new BusinessException("METHOD_CONFIG_INVALID：可用 IP 范围必须在 1-254 且起始值不大于结束值");
        }
    }

    private int numberValue(Object value) {
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
