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

    public void validate(AutomationOperationCatalog.OperationMethod method, Map<String, Object> config) {
        validateRequiredFields(method, config);
        validateInfrastructureTargetRef(method, config);
        rejectPlaintextSecrets(config);
        validateRegex(config);
        validateVariableName(method.getActionType(), config);
        validateDateFormat(method.getActionType(), config);
        validateFormula(method.getActionType(), config);
        validateIpRange(method.getActionType(), config);
    }

    private void validateRequiredFields(AutomationOperationCatalog.OperationMethod method, Map<String, Object> config) {
        for (Map<String, Object> field : method.getFormSchema()) {
            if (!Boolean.TRUE.equals(field.get("required"))) {
                continue;
            }
            String name = stringValue(field.get("name"));
            Object value = config.get(name);
            if (value == null || value instanceof String text && text
                .isBlank() || value instanceof Collection<?> collection && collection.isEmpty()) {
                throw new BusinessException("METHOD_CONFIG_INVALID：" + method.getLabel() + " 缺少必填参数 " + name);
            }
        }
    }

    /**
     * 基础设施目标是路由事实来源，不能只依赖通用的“字段存在”校验。
     * 新建步骤优先使用正数配置 ID；binding_key 仅为历史步骤保留兼容入口。
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
        if (!"project_config".equals(scope)) {
            throw new BusinessException("INFRA_TARGET_REF_INVALID：" + method
                .getLabel() + " 的 target_ref.scope 必须为 project_config");
        }
        String actualKind = stringValue(targetRef.get("kind"));
        if (!expectedKind.equals(actualKind)) {
            throw new BusinessException("INFRA_TARGET_KIND_MISMATCH：" + method.getLabel() + " 需要 kind=" + expectedKind);
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
