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

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorCapabilityReq;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 代码仓库内版本化操作目录实现。
 *
 * @author Codex
 */
@Service
@RequiredArgsConstructor
public class AutomationOperationCatalogServiceImpl implements AutomationOperationCatalogService {

    private static final String CATALOG_RESOURCE = "automation/automation-operation-catalog.json";
    private static final Set<String> EXECUTORS = Set.of("selenium", "playwright", "cuecast");
    private static final Set<String> FORM_COMPONENTS = Set
        .of("input", "number", "select", "switch", "textarea", "code", "locator", "target_ref", "file_ref", "key_value");
    private static final Set<String> DIAGNOSTIC_PROFILES = Set
        .of("navigation", "element_interaction", "dialog", "assertion", "wait", "variable", "script", "infrastructure");
    private static final Set<String> DIAGNOSTIC_ROLES = Set
        .of("target", "input", "expected", "definition", "binding", "control");
    private static final Set<String> DIAGNOSTIC_SENSITIVITIES = Set.of("public", "inherit", "sensitive", "restricted");
    private static final Set<String> DIAGNOSTIC_DISPLAYS = Set
        .of("effective_preview", "configured_preview", "basename", "summary", "definition_endpoint", "omit");
    private static final Set<String> TEXT_DEFAULT_COMPONENTS = Set
        .of("input", "textarea", "code", "locator", "target_ref", "file_ref");
    private static final Set<String> DEFAULT_FORBIDDEN_FIELDS = Set
        .of("url", "target_ref", "sql", "command", "path", "file_ref", "certificate_ref", "variable_name", "value", "expect", "script");
    private static final int EXPECTED_TYPE_COUNT = 13;
    private static final int EXPECTED_METHOD_COUNT = 63;
    // 能力上报是短租约：Runner 或扩展升级、退出后不能继续以历史能力开放手工步骤。
    private static final Duration CAPABILITY_SNAPSHOT_TTL = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;

    private final Map<CapabilitySnapshotKey, CapabilitySnapshot> capabilitySnapshots = new ConcurrentHashMap<>();
    private final Map<String, AutomationOperationCatalog.OperationMethod> methodIndex = new HashMap<>();
    private final Set<String> knownActions = new HashSet<>();

    private AutomationOperationCatalog catalog;

    @PostConstruct
    public void initialize() {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            catalog = objectMapper.readValue(inputStream, AutomationOperationCatalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("自动化操作目录加载失败：" + CATALOG_RESOURCE, e);
        }
        validateAndIndex(catalog);
    }

    @Override
    public AutomationOperationCatalog getCatalog(Long sceneId,
                                                 Long projectEnvironmentId,
                                                 String executorInstanceId,
                                                 String principalScope,
                                                 String sessionId,
                                                 boolean canAddStep,
                                                 boolean canExecuteInfrastructure,
                                                 Set<String> agentTypes,
                                                 Set<String> agentFeatures) {
        cleanupExpiredSnapshots();
        CatalogRequestScope scope = new CatalogRequestScope(sceneId, projectEnvironmentId, executorInstanceId, principalScope, sessionId, canAddStep, canExecuteInfrastructure, normalizeSet(agentTypes), normalizeSet(agentFeatures));
        AutomationOperationCatalog response = objectMapper.convertValue(catalog, AutomationOperationCatalog.class);
        for (AutomationOperationCatalog.OperationType type : response.getTypes()) {
            for (AutomationOperationCatalog.OperationMethod method : type.getMethods()) {
                applyAvailability(method, scope);
            }
        }
        return response;
    }

    @Override
    public Optional<AutomationOperationCatalog.OperationMethod> findMethod(String methodCodeOrAction) {
        if (methodCodeOrAction == null || methodCodeOrAction.isBlank()) {
            return Optional.empty();
        }
        AutomationOperationCatalog.OperationMethod method = methodIndex.get(normalize(methodCodeOrAction));
        if (method == null) {
            return Optional.empty();
        }
        AutomationOperationCatalog.OperationMethod copy = objectMapper
            .convertValue(method, AutomationOperationCatalog.OperationMethod.class);
        applyStaticAvailability(copy);
        return Optional.of(copy);
    }

    @Override
    public Optional<OperationDescriptor> findOperation(String methodCodeOrAction) {
        Optional<AutomationOperationCatalog.OperationMethod> resolved = findMethod(methodCodeOrAction);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        AutomationOperationCatalog.OperationMethod method = resolved.get();
        return catalog.getTypes()
            .stream()
            .filter(type -> type.getMethods()
                .stream()
                .anyMatch(candidate -> candidate.getMethodCode().equals(method.getMethodCode())))
            .findFirst()
            .map(type -> new OperationDescriptor(type.getTypeCode(), type.getLabel(), method));
    }

    @Override
    public void registerCapabilities(String executorType, String principalScope, AutomationExecutorCapabilityReq req) {
        String executor = normalize(executorType);
        if (!Set.of("playwright", "cuecast").contains(executor)) {
            throw new BusinessException("不支持的执行器：" + executorType);
        }
        if (!catalog.getCatalogVersion().equals(req.getCatalogVersion())) {
            throw new BusinessException("执行器目录版本不兼容：Admin=" + catalog.getCatalogVersion() + "，执行器=" + req
                .getCatalogVersion());
        }
        Set<String> actions = new HashSet<>();
        for (String action : req.getActions()) {
            String normalized = normalize(action);
            if (knownActions.contains(normalized)) {
                actions.add(normalized);
            }
        }
        Set<String> features = new HashSet<>();
        for (String feature : Optional.ofNullable(req.getFeatures()).orElseGet(List::of)) {
            if (feature != null && !feature.isBlank()) {
                features.add(normalize(feature));
            }
        }
        String instanceId = normalizeRequired(req.getExecutorInstanceId(), "执行器实例不能为空");
        String scope = normalizeRequired(principalScope, "执行器主体作用域不能为空");
        String sessionId = normalize(req.getSessionId());
        if ("cuecast".equals(executor)) {
            if (sessionId.isBlank()) {
                throw new BusinessException("CueCast 扩展会话不能为空");
            }
            scope = scope + ":" + sessionId;
        }
        Instant reportedAt = Instant.now();
        CapabilitySnapshotKey key = new CapabilitySnapshotKey(executor, instanceId, req
            .getProjectEnvironmentId(), scope);
        capabilitySnapshots.put(key, new CapabilitySnapshot(req.getExecutorVersion(), Set.copyOf(actions), Set
            .copyOf(features), reportedAt, reportedAt.plus(CAPABILITY_SNAPSHOT_TTL)));
    }

    private void validateAndIndex(AutomationOperationCatalog source) {
        if (source == null || source.getCatalogVersion() == null || source.getCatalogVersion().isBlank()) {
            throw new IllegalStateException("自动化操作目录缺少 catalog_version");
        }
        if (source.getTypes() == null || source.getTypes().size() != EXPECTED_TYPE_COUNT) {
            throw new IllegalStateException("自动化操作目录必须包含 " + EXPECTED_TYPE_COUNT + " 个操作类型");
        }
        int methodCount = 0;
        Set<String> typeCodes = new HashSet<>();
        for (AutomationOperationCatalog.OperationType type : source.getTypes()) {
            if (type.getTypeCode() == null || !typeCodes.add(normalize(type.getTypeCode()))) {
                throw new IllegalStateException("自动化操作目录存在空或重复 type_code");
            }
            if (type.getMethods() == null) {
                throw new IllegalStateException("操作类型缺少 methods：" + type.getTypeCode());
            }
            for (AutomationOperationCatalog.OperationMethod method : type.getMethods()) {
                methodCount++;
                validateMethod(method);
                knownActions.add(normalize(method.getActionType()));
                index(method.getMethodCode(), method);
                index(method.getLegacyAction(), method);
                index(method.getActionType(), method);
                for (String alias : method.getAliases()) {
                    index(alias, method);
                }
            }
        }
        if (methodCount != EXPECTED_METHOD_COUNT) {
            throw new IllegalStateException("自动化操作目录必须包含 " + EXPECTED_METHOD_COUNT + " 个方法，当前为 " + methodCount);
        }
        validateDiagnosticProfiles(source);
    }

    private void validateDiagnosticProfiles(AutomationOperationCatalog source) {
        Map<String, List<String>> profiles = source.getDiagnosticProfiles();
        if (profiles == null || profiles.isEmpty()) {
            throw new IllegalStateException("自动化操作目录缺少 diagnostic_profiles");
        }
        Map<String, String> methodProfiles = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : profiles.entrySet()) {
            if (!DIAGNOSTIC_PROFILES.contains(normalize(entry.getKey())) || entry.getValue() == null) {
                throw new IllegalStateException("自动化操作目录包含无效诊断模板：" + entry.getKey());
            }
            for (String methodCode : entry.getValue()) {
                String normalizedMethod = normalize(methodCode);
                if (normalizedMethod.isBlank() || methodProfiles.putIfAbsent(normalizedMethod, entry
                    .getKey()) != null) {
                    throw new IllegalStateException("自动化操作目录诊断模板存在空或重复方法：" + methodCode);
                }
            }
        }
        for (AutomationOperationCatalog.OperationType type : source.getTypes()) {
            for (AutomationOperationCatalog.OperationMethod method : type.getMethods()) {
                String profile = methodProfiles.get(normalize(method.getMethodCode()));
                if (profile == null) {
                    throw new IllegalStateException("操作方法缺少诊断模板：" + method.getMethodCode());
                }
                method.setDiagnosticProfile(profile);
            }
        }
        if (methodProfiles.size() != EXPECTED_METHOD_COUNT) {
            throw new IllegalStateException("诊断模板必须覆盖 " + EXPECTED_METHOD_COUNT + " 个方法，当前为 " + methodProfiles.size());
        }
    }

    private void validateMethod(AutomationOperationCatalog.OperationMethod method) {
        if (method.getMethodCode() == null || method.getMethodCode().isBlank() || method
            .getLegacyAction() == null || method.getLegacyAction().isBlank() || method.getActionType() == null || method
                .getActionType()
                .isBlank()) {
            throw new IllegalStateException("自动化操作方法缺少编码：" + method.getLabel());
        }
        if (method.getMethodVersion() == null || method.getMethodVersion() < 1) {
            throw new IllegalStateException("自动化操作方法版本无效：" + method.getMethodCode());
        }
        if (method.getFormSchema() == null) {
            throw new IllegalStateException("自动化操作方法缺少 form_schema：" + method.getMethodCode());
        }
        validateFormSchema(method);
        if (method.getCapabilities() == null || !method.getCapabilities().keySet().containsAll(EXECUTORS)) {
            throw new IllegalStateException("自动化操作方法缺少三执行器能力声明：" + method.getMethodCode());
        }
    }

    private void validateFormSchema(AutomationOperationCatalog.OperationMethod method) {
        Map<String, Map<String, Object>> fields = new HashMap<>();
        for (Map<String, Object> field : method.getFormSchema()) {
            String name = text(field.get("name"));
            String label = text(field.get("label"));
            String component = text(field.get("component"));
            if (name.isBlank() || label.isBlank() || !FORM_COMPONENTS.contains(component)) {
                throw catalogFieldError(method, name, "缺少 name/label 或 component 不受支持");
            }
            if (fields.putIfAbsent(name, field) != null) {
                throw catalogFieldError(method, name, "字段名重复");
            }
            validateFieldPresentation(method, field, name, component);
            validateFieldOptions(method, field, name, component);
            validateFieldRange(method, field, name);
            validateFieldDefault(method, field, name, component);
            applyDiagnosticFieldMetadata(method, field, name);
        }
        for (Map<String, Object> field : method.getFormSchema()) {
            validateFieldCondition(method, fields, field, "visible_when");
            validateFieldCondition(method, fields, field, "required_when");
        }
    }

    private void applyDiagnosticFieldMetadata(AutomationOperationCatalog.OperationMethod method,
                                              Map<String, Object> field,
                                              String name) {
        Map<String, String> defaults = catalog.getDiagnosticFieldDefaults().get(name);
        if (defaults == null) {
            throw catalogFieldError(method, name, "缺少 diagnostic_role/sensitivity/result_display");
        }
        for (String key : List.of("diagnostic_role", "sensitivity", "result_display")) {
            String value = text(field.get(key));
            if (value.isBlank()) {
                value = text(defaults.get(key));
                field.put(key, value);
            }
            if (value.isBlank()) {
                throw catalogFieldError(method, name, "缺少字段诊断元数据：" + key);
            }
        }
        if (!DIAGNOSTIC_ROLES.contains(text(field.get("diagnostic_role"))) || !DIAGNOSTIC_SENSITIVITIES
            .contains(text(field.get("sensitivity"))) || !DIAGNOSTIC_DISPLAYS.contains(text(field
                .get("result_display")))) {
            throw catalogFieldError(method, name, "字段诊断元数据取值无效");
        }
        if (Set.of("sql", "command", "script").contains(name) && !Set.of("restricted", "sensitive")
            .contains(text(field.get("sensitivity")))) {
            throw catalogFieldError(method, name, "受限定义字段不能声明普通敏感级别");
        }
    }

    private void validateFieldPresentation(AutomationOperationCatalog.OperationMethod method,
                                           Map<String, Object> field,
                                           String name,
                                           String component) {
        if (!"select".equals(component) && text(field.get("placeholder")).isBlank() && text(field.get("help"))
            .isBlank()) {
            throw catalogFieldError(method, name, "非选择字段必须声明 placeholder 或 help");
        }
    }

    private void validateFieldOptions(AutomationOperationCatalog.OperationMethod method,
                                      Map<String, Object> field,
                                      String name,
                                      String component) {
        if (!"select".equals(component)) {
            return;
        }
        List<Map<String, Object>> options = options(method, field, name);
        if (options.isEmpty()) {
            throw catalogFieldError(method, name, "select 字段缺少 options");
        }
        Set<String> values = new HashSet<>();
        for (Map<String, Object> option : options) {
            String label = text(option.get("label"));
            String value = text(option.get("value"));
            if (label.isBlank() || value.isBlank() || !values.add(value)) {
                throw catalogFieldError(method, name, "option 缺少 label/value 或 value 重复");
            }
        }
    }

    private void validateFieldRange(AutomationOperationCatalog.OperationMethod method,
                                    Map<String, Object> field,
                                    String name) {
        Object minimum = field.get("min");
        Object maximum = field.get("max");
        if (minimum != null && !(minimum instanceof Number) || maximum != null && !(maximum instanceof Number)) {
            throw catalogFieldError(method, name, "min/max 必须是数字");
        }
        if (minimum instanceof Number min && maximum instanceof Number max && min.doubleValue() > max.doubleValue()) {
            throw catalogFieldError(method, name, "min 不能大于 max");
        }
    }

    private void validateFieldDefault(AutomationOperationCatalog.OperationMethod method,
                                      Map<String, Object> field,
                                      String name,
                                      String component) {
        if (!field.containsKey("default")) {
            return;
        }
        Object value = field.get("default");
        if (value == null || isDefaultForbiddenField(name, component)) {
            throw catalogFieldError(method, name, "不允许声明默认值");
        }
        if ("number".equals(component) && !(value instanceof Number)) {
            throw catalogFieldError(method, name, "number 默认值必须是数字");
        }
        if (value instanceof Number number) {
            if (field.get("min") instanceof Number min && number.doubleValue() < min.doubleValue() || field
                .get("max") instanceof Number max && number.doubleValue() > max.doubleValue()) {
                throw catalogFieldError(method, name, "number 默认值超出 min/max 范围");
            }
        }
        if (TEXT_DEFAULT_COMPONENTS.contains(component) && !(value instanceof String)) {
            throw catalogFieldError(method, name, component + " 默认值必须是字符串");
        }
        if ("switch".equals(component) && !(value instanceof Boolean)) {
            throw catalogFieldError(method, name, "switch 默认值必须是布尔值");
        }
        if ("key_value".equals(component) && !(value instanceof Map<?, ?>)) {
            throw catalogFieldError(method, name, "key_value 默认值必须是对象");
        }
        if ("select".equals(component) && options(method, field, name).stream()
            .noneMatch(option -> text(option.get("value")).equals(text(value)))) {
            throw catalogFieldError(method, name, "默认值不在 options 中");
        }
    }

    private boolean isDefaultForbiddenField(String name, String component) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        return DEFAULT_FORBIDDEN_FIELDS.contains(normalized) || Set.of("locator", "target_ref", "file_ref")
            .contains(component) || normalized.endsWith("_path") || normalized.contains("password") || normalized
                .contains("secret") || normalized.contains("token") || normalized.contains("certificate");
    }

    private void validateFieldCondition(AutomationOperationCatalog.OperationMethod method,
                                        Map<String, Map<String, Object>> fields,
                                        Map<String, Object> field,
                                        String conditionName) {
        Object rawCondition = field.get(conditionName);
        if (rawCondition == null) {
            return;
        }
        if (!(rawCondition instanceof Map<?, ?> condition) || condition.isEmpty()) {
            throw catalogFieldError(method, text(field.get("name")), conditionName + " 必须是非空对象");
        }
        for (Map.Entry<?, ?> entry : condition.entrySet()) {
            String referencedName = text(entry.getKey());
            Map<String, Object> referencedField = fields.get(referencedName);
            if (referencedField == null) {
                throw catalogFieldError(method, text(field.get("name")), conditionName + " 引用了未知字段 " + referencedName);
            }
            if (entry.getValue() == null || entry.getValue() instanceof Collection<?> values && values.isEmpty()) {
                throw catalogFieldError(method, text(field.get("name")), conditionName + " 的条件值不能为空");
            }
            String referencedComponent = text(referencedField.get("component"));
            if ("select".equals(referencedComponent)) {
                Set<String> optionValues = new HashSet<>();
                for (Map<String, Object> option : options(method, referencedField, referencedName)) {
                    optionValues.add(text(option.get("value")));
                }
                for (Object expected : conditionValues(entry.getValue())) {
                    if (!optionValues.contains(text(expected))) {
                        throw catalogFieldError(method, text(field.get("name")), conditionName + " 包含无效选项 " + expected);
                    }
                }
            } else {
                for (Object expected : conditionValues(entry.getValue())) {
                    boolean validType = switch (referencedComponent) {
                        case "number" -> expected instanceof Number;
                        case "switch" -> expected instanceof Boolean;
                        default -> expected instanceof String;
                    };
                    if (!validType) {
                        throw catalogFieldError(method, text(field
                            .get("name")), conditionName + " 的条件值类型与 " + referencedName + " 不一致");
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> options(AutomationOperationCatalog.OperationMethod method,
                                              Map<String, Object> field,
                                              String name) {
        Object rawOptions = field.get("options");
        if (!(rawOptions instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>))) {
            throw catalogFieldError(method, name, "options 必须是对象数组");
        }
        return (List<Map<String, Object>>)(List<?>)list;
    }

    private Collection<?> conditionValues(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of(value);
    }

    private IllegalStateException catalogFieldError(AutomationOperationCatalog.OperationMethod method,
                                                    String field,
                                                    String reason) {
        return new IllegalStateException("自动化操作目录字段无效：" + method.getMethodCode() + "." + field + "，" + reason);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void index(String key, AutomationOperationCatalog.OperationMethod method) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalized = normalize(key);
        AutomationOperationCatalog.OperationMethod previous = methodIndex.putIfAbsent(normalized, method);
        if (previous != null && previous != method) {
            // 多个旧方法可复用同一个 canonical action，action_type 不能作为唯一索引覆盖方法编码。
            if (normalized.equals(normalize(method.getActionType()))) {
                return;
            }
            throw new IllegalStateException("自动化操作目录存在重复方法编码或 alias：" + key);
        }
    }

    private void applyStaticAvailability(AutomationOperationCatalog.OperationMethod method) {
        boolean implemented = EXECUTORS.stream()
            .allMatch(executor -> "implemented".equals(normalize(method.getCapabilities().get(executor))));
        method.setImplemented(implemented);
        method.setAuthoringEnabled(implemented);
        method.setRuntimeReady(false);
        method.setPermissionGranted(true);
        method.setEnabled(false);
        if (!implemented) {
            method.setDisabledCode("METHOD_ADAPTER_NOT_READY");
            method.setDisabledReason("至少一个执行器尚未实现该方法");
            return;
        }
        method.setDisabledCode("EXECUTION_CONTEXT_REQUIRED");
        method.setDisabledReason("需要场景、环境和当前执行器作用域后才能判断运行可用性");
    }

    private void applyAvailability(AutomationOperationCatalog.OperationMethod method, CatalogRequestScope scope) {
        boolean implemented = EXECUTORS.stream()
            .allMatch(executor -> "implemented".equals(normalize(method.getCapabilities().get(executor))));
        boolean permissionGranted = scope.canAddStep() && (!requiresInfrastructurePermission(method) || scope
            .canExecuteInfrastructure());
        method.setImplemented(implemented);
        method.setAuthoringEnabled(implemented);
        method.setPermissionGranted(permissionGranted);
        method.setRuntimeReady(false);
        method.setEnabled(false);
        if (!implemented) {
            disable(method, "METHOD_ADAPTER_NOT_READY", "至少一个执行器尚未实现该方法");
            return;
        }
        if (!permissionGranted) {
            disable(method, "METHOD_PERMISSION_DENIED", "当前用户没有添加或执行该方法的权限");
            return;
        }
        if (scope.sceneId() == null) {
            disable(method, "SCENE_CONTEXT_REQUIRED", "缺少场景上下文");
            return;
        }
        if (scope.projectEnvironmentId() == null) {
            disable(method, "PROJECT_ENVIRONMENT_REQUIRED", "请选择当前场景所属的项目环境");
            return;
        }
        if (requiresAgent(method)) {
            Set<String> requiredAgentTypes = requiredAgentTypes(method);
            if (!scope.agentTypes().containsAll(requiredAgentTypes) || !scope.agentFeatures()
                .containsAll(requiredFeatures(method))) {
                disable(method, disabledCodeForAgent(method.getActionType()), "当前环境没有满足安全前置条件的执行 Agent");
                return;
            }
        }
        for (String executor : List.of("playwright", "cuecast")) {
            CapabilitySnapshot snapshot = findSnapshot(executor, scope);
            if (snapshot == null) {
                disable(method, executor.toUpperCase(Locale.ROOT) + "_NOT_READY", executor + " 未上报当前环境的有效能力快照");
                return;
            }
            if (!snapshot.actions().contains(normalize(method.getActionType()))) {
                disable(method, "EXECUTOR_ACTION_NOT_READY", executor + " " + snapshot.version() + " 未上报该 action");
                return;
            }
            if (!requiresAgent(method) && !snapshot.features().containsAll(requiredFeatures(method))) {
                disable(method, "EXECUTOR_FEATURE_NOT_READY", executor + " 缺少方法要求的运行特性");
                return;
            }
        }
        method.setRuntimeReady(true);
        method.setEnabled(true);
        method.setDisabledCode("");
        method.setDisabledReason("");
    }

    private CapabilitySnapshot findSnapshot(String executor, CatalogRequestScope scope) {
        String cuecastScope = normalize(scope.principalScope()) + ":" + normalize(scope.sessionId());
        List<Map.Entry<CapabilitySnapshotKey, CapabilitySnapshot>> candidates = capabilitySnapshots.entrySet()
            .stream()
            .filter(entry -> executor.equals(entry.getKey().executorType()))
            .filter(entry -> scope.projectEnvironmentId().equals(entry.getKey().projectEnvironmentId()))
            .filter(entry -> !"cuecast".equals(executor) || cuecastScope.equals(entry.getKey().principalScope()))
            .filter(entry -> !"playwright".equals(executor) || scope.executorInstanceId() == null || scope
                .executorInstanceId()
                .isBlank() || normalize(scope.executorInstanceId()).equals(entry.getKey().executorInstanceId()))
            .filter(entry -> entry.getValue().expiresAt().isAfter(Instant.now()))
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        // Playwright 是节点能力，不允许多个节点在未指定实例时互相污染目录状态。
        if ("playwright".equals(executor) && (scope.executorInstanceId() == null || scope.executorInstanceId()
            .isBlank()) && candidates.stream()
                .map(entry -> entry.getKey().executorInstanceId())
                .distinct()
                .count() != 1) {
            return null;
        }
        return candidates.stream()
            .map(Map.Entry::getValue)
            .max(java.util.Comparator.comparing(CapabilitySnapshot::reportedAt))
            .orElse(null);
    }

    private void cleanupExpiredSnapshots() {
        Instant now = Instant.now();
        capabilitySnapshots.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private boolean requiresInfrastructurePermission(AutomationOperationCatalog.OperationMethod method) {
        return readStringList(requirements(method).get("permissions"))
            .contains("automation:automationUiScene:execute-infrastructure");
    }

    private boolean requiresAgent(AutomationOperationCatalog.OperationMethod method) {
        return !readStringList(requirements(method).get("agent_types")).isEmpty();
    }

    private Set<String> requiredAgentTypes(AutomationOperationCatalog.OperationMethod method) {
        Set<String> result = new HashSet<>();
        for (String agentType : readStringList(requirements(method).get("agent_types"))) {
            result.add(normalize(agentType));
        }
        return result;
    }

    private Set<String> requiredFeatures(AutomationOperationCatalog.OperationMethod method) {
        Set<String> result = new HashSet<>();
        for (String feature : readStringList(requirements(method).get("features"))) {
            result.add(normalize(feature));
        }
        return result;
    }

    private Map<String, Object> requirements(AutomationOperationCatalog.OperationMethod method) {
        return Optional.ofNullable(method.getRequirements()).orElseGet(Map::of);
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (Object item : iterable) {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String disabledCodeForAgent(String actionType) {
        return switch (normalize(actionType)) {
            case "captcha_ocr" -> "OCR_AGENT_NOT_READY";
            case "host_pointer_move" -> "DESKTOP_AGENT_NOT_READY";
            case "host_file_lookup", "host_file_delete" -> "HOST_FILE_AGENT_NOT_READY";
            case "host_command" -> "HOST_COMMAND_AGENT_NOT_READY";
            case "global_variable_property" -> "RUNTIME_PROPERTY_NOT_ALLOWED";
            case "server_file_upload" -> "SERVER_FILE_AGENT_NOT_READY";
            default -> "AGENT_NOT_READY";
        };
    }

    private void disable(AutomationOperationCatalog.OperationMethod method, String code, String reason) {
        method.setRuntimeReady(false);
        method.setEnabled(false);
        method.setDisabledCode(code);
        method.setDisabledReason(reason);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> normalizeSet(Set<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : Optional.ofNullable(values).orElseGet(Set::of)) {
            if (value != null && !value.isBlank()) {
                normalized.add(normalize(value));
            }
        }
        return Set.copyOf(normalized);
    }

    private record CatalogRequestScope(Long sceneId, Long projectEnvironmentId, String executorInstanceId,
                                       String principalScope, String sessionId, boolean canAddStep,
                                       boolean canExecuteInfrastructure, Set<String> agentTypes,
                                       Set<String> agentFeatures) {
    }

    private record CapabilitySnapshotKey(String executorType, String executorInstanceId, Long projectEnvironmentId,
                                         String principalScope) {
    }

    private record CapabilitySnapshot(String version, Set<String> actions, Set<String> features, Instant reportedAt,
                                      Instant expiresAt) {
    }
}
