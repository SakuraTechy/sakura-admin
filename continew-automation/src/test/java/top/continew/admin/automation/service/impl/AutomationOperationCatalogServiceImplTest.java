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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorCapabilityReq;

class AutomationOperationCatalogServiceImplTest {

    private static final Long SCENE_ID = 1L;
    private static final Long ENVIRONMENT_ID = 7L;
    private static final String PRINCIPAL_SCOPE = "principal:1";
    private static final String SESSION_ID = "cuecast-session-1";

    private AutomationOperationCatalogServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AutomationOperationCatalogServiceImpl(objectMapper);
        service.initialize();
    }

    @Test
    void shouldLoadThirteenTypesAndSixtyThreeMethods() {
        AutomationOperationCatalog catalog = catalog();
        List<AutomationOperationCatalog.OperationMethod> methods = catalog.getTypes()
            .stream()
            .flatMap(type -> type.getMethods().stream())
            .toList();

        assertThat(catalog.getTypes()).hasSize(13);
        assertThat(methods).hasSize(63);
        assertThat(methods).allSatisfy(method -> {
            assertThat(method.getMethodCode()).isNotBlank();
            assertThat(method.getLegacyAction()).isNotBlank();
            assertThat(method.getActionType()).isNotBlank();
            assertThat(method.getFormSchema()).isNotNull();
            assertThat(method.getCapabilities()).containsKeys("selenium", "playwright", "cuecast");
            assertThat(method.getAuthoringEnabled()).isTrue();
            assertThat(method.getImplemented()).isTrue();
            assertThat(method.getRuntimeReady()).isFalse();
            assertThat(method.getEnabled()).isFalse();
        });
    }

    @Test
    void shouldCoverEveryMethodWithADiagnosticProfile() {
        AutomationOperationCatalog catalog = catalog();
        assertThat(catalog.getDiagnosticProfiles())
            .containsKeys("navigation", "element_interaction", "dialog", "assertion", "wait", "variable", "script", "infrastructure");
        assertThat(catalog.getDiagnosticProfiles().values().stream().mapToInt(List::size).sum()).isEqualTo(63);
        assertThat(catalog.getDiagnosticProfiles().values().stream().flatMap(List::stream).distinct()).hasSize(63);
    }

    @Test
    void shouldExposeCompleteAndSafeFormPresentationMetadata() {
        List<AutomationOperationCatalog.OperationMethod> methods = methods();
        List<Map<String, Object>> fields = methods.stream().flatMap(method -> method.getFormSchema().stream()).toList();

        assertThat(fields).hasSize(117);
        assertThat(catalog().getDiagnosticFieldDefaults()).hasSize(44);
        assertThat(fields.stream().filter(field -> field.containsKey("default"))).hasSize(16);
        for (AutomationOperationCatalog.OperationMethod method : methods) {
            Set<String> fieldNames = new HashSet<>();
            for (Map<String, Object> field : method.getFormSchema()) {
                assertThat(field.get("name")).as(method.getMethodCode()).isInstanceOf(String.class);
                assertThat(String.valueOf(field.get("name"))).as(method.getMethodCode()).isNotBlank();
                assertThat(String.valueOf(field.get("label"))).as(method.getMethodCode()).isNotBlank();
                assertThat(String.valueOf(field.get("component"))).as(method.getMethodCode()).isNotBlank();
                assertThat(fieldNames.add(String.valueOf(field.get("name")))).as(method.getMethodCode()).isTrue();
                assertThat(field).containsKeys("diagnostic_role", "sensitivity", "result_display");
                assertThat(String.valueOf(field.get("diagnostic_role")))
                    .isIn("target", "input", "expected", "definition", "binding", "control");
                assertThat(String.valueOf(field.get("sensitivity")))
                    .isIn("public", "inherit", "sensitive", "restricted");
                assertThat(String.valueOf(field.get("result_display")))
                    .isIn("effective_preview", "configured_preview", "basename", "summary", "definition_endpoint", "omit");
                assertFieldPresentation(method, field);
                assertConditionsReferenceDeclaredFields(method, field);
            }
        }
    }

    @Test
    void shouldExposeConditionalDefaultsForGlobalVariableMethods() {
        AutomationOperationCatalog.OperationMethod date = findMethod(catalog(), "global.variable.date");
        assertThat(field(date, "date_mode")).containsEntry("default", "current_datetime");
        assertThat(field(date, "format")).containsEntry("default", "yyyy-MM-dd HH:mm:ss")
            .containsEntry("visible_when", Map.of("date_mode", List.of("current_datetime", "custom_datetime")));
        assertThat(field(date, "datetime")).containsEntry("visible_when", Map.of("date_mode", "custom_datetime"))
            .containsEntry("required_when", Map.of("date_mode", "custom_datetime"));
        assertThat(field(date, "timestamp_unit")).containsEntry("default", "milliseconds")
            .containsEntry("visible_when", Map.of("date_mode", "timestamp"));

        AutomationOperationCatalog.OperationMethod set = findMethod(catalog(), "global.variable.set");
        assertThat(field(set, "source_type")).containsEntry("default", "literal");
        assertThat(field(set, "value")).containsEntry("visible_when", Map.of("source_type", List
            .of("literal", "script")))
            .containsEntry("required_when", Map.of("source_type", List.of("literal", "script")));
        assertThat(field(set, "target_ref")).containsEntry("visible_when", Map.of("source_type", "locator"))
            .containsEntry("required_when", Map.of("source_type", "locator"));
    }

    @Test
    void shouldResolveLegacyAliasesAndRequireActiveCapabilityHandshake() {
        assertThat(service.findOperation("input")).get().satisfies(operation -> {
            assertThat(operation.typeLabel()).isEqualTo("输入操作");
            assertThat(operation.method().getLabel()).isEqualTo("输入文本");
            assertThat(operation.method().getMethodCode()).isEqualTo("input.text");
        });
        assertThat(service.findMethod("web-checkset")).get()
            .extracting(AutomationOperationCatalog.OperationMethod::getLegacyAction)
            .isEqualTo("web-checksetlist");
        assertThat(service.findMethod("web-notchecklists")).get()
            .extracting(AutomationOperationCatalog.OperationMethod::getLegacyAction)
            .isEqualTo("web-notchecksetlist");
        assertThat(service.findMethod("web-click")).get().satisfies(method -> {
            assertThat(method.getAuthoringEnabled()).isTrue();
            assertThat(method.getEnabled()).isFalse();
            assertThat(method.getDisabledCode()).isEqualTo("EXECUTION_CONTEXT_REQUIRED");
        });
        assertThat(service.findMethod("javascript-executor")).get()
            .extracting(AutomationOperationCatalog.OperationMethod::getEnabled)
            .isEqualTo(false);
    }

    @Test
    void shouldDowngradeMethodWhenExecutorHandshakeDoesNotContainAction() {
        register("cuecast", List.of("navigate", "click"));
        register("playwright", List.of("navigate"));

        assertThat(findMethod(catalog(), "web-click")).satisfies(method -> {
            assertThat(method.getAuthoringEnabled()).isTrue();
            assertThat(method.getEnabled()).isFalse();
            assertThat(method.getDisabledReason()).contains("playwright 1.0.0");
        });
        assertThat(findMethod(catalog(), "web-geturl"))
            .extracting(AutomationOperationCatalog.OperationMethod::getEnabled)
            .isEqualTo(true);
    }

    @Test
    void shouldEnableMethodOnlyWhenBothCurrentExecutorsReportTheAction() {
        register("playwright", List.of("click"));
        register("cuecast", List.of("click"));

        assertThat(findMethod(catalog(), "web-click")).satisfies(method -> {
            assertThat(method.getAuthoringEnabled()).isTrue();
            assertThat(method.getEnabled()).isTrue();
            assertThat(method.getRuntimeReady()).isTrue();
            assertThat(method.getDisabledReason()).isBlank();
        });
    }

    @Test
    void shouldMatchEveryCatalogMethodWithTheCrossExecutorFixture() throws Exception {
        JsonNode fixture = objectMapper.readTree(getClass()
            .getResourceAsStream("/automation/automation-operation-63-fixture.json"));
        assertThat(fixture.path("catalog_version").asText()).isEqualTo("2026-08-07.1");
        assertThat(fixture.path("methods").size()).isEqualTo(63);

        List<AutomationOperationCatalog.OperationMethod> catalogMethods = catalog().getTypes()
            .stream()
            .flatMap(type -> type.getMethods().stream())
            .toList();
        for (JsonNode expected : fixture.path("methods")) {
            AutomationOperationCatalog.OperationMethod actual = catalogMethods.stream()
                .filter(method -> expected.path("method_code").asText().equals(method.getMethodCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("目录缺少 fixture 方法：" + expected.path("method_code").asText()));
            assertThat(actual.getActionType()).isEqualTo(expected.path("action_type").asText());
            assertThat(actual.getLegacyAction()).isEqualTo(expected.path("legacy_action").asText());
            assertThat(actual.getCapabilities()).containsEntry("selenium", "implemented")
                .containsEntry("playwright", "implemented")
                .containsEntry("cuecast", "implemented");
        }
    }

    @Test
    void shouldIsolateCuecastSnapshotsByPrincipalSessionAndEnvironment() {
        register("playwright", List.of("click"));
        register("cuecast", List.of("click"));

        assertThat(findMethod(catalog(), "web-click").getEnabled()).isTrue();
        AutomationOperationCatalog otherSession = service
            .getCatalog(SCENE_ID, ENVIRONMENT_ID, PRINCIPAL_SCOPE, "other-session", true, true, Set.of(), Set.of());
        assertThat(findMethod(otherSession, "web-click").getDisabledCode()).isEqualTo("CUECAST_NOT_READY");
        AutomationOperationCatalog otherEnvironment = service
            .getCatalog(SCENE_ID, 8L, PRINCIPAL_SCOPE, SESSION_ID, true, true, Set.of(), Set.of());
        assertThat(findMethod(otherEnvironment, "web-click").getDisabledCode()).isEqualTo("PLAYWRIGHT_NOT_READY");
    }

    @Test
    void shouldNotMixPlaywrightSnapshotsFromDifferentNodes() {
        register("playwright", List.of("click"));
        registerWithInstance("playwright", List.of("navigate"), "playwright-node-2");
        register("cuecast", List.of("click", "navigate"));

        AutomationOperationCatalog ambiguous = catalog();
        assertThat(findMethod(ambiguous, "web-click").getDisabledCode()).isEqualTo("PLAYWRIGHT_NOT_READY");

        AutomationOperationCatalog nodeOne = service
            .getCatalog(SCENE_ID, ENVIRONMENT_ID, "playwright-node-1", PRINCIPAL_SCOPE, SESSION_ID, true, true, Set
                .of(), Set.of());
        assertThat(findMethod(nodeOne, "web-click").getEnabled()).isTrue();
        assertThat(findMethod(nodeOne, "web-geturls").getDisabledCode()).isEqualTo("EXECUTOR_ACTION_NOT_READY");
    }

    @Test
    void shouldIgnoreSpoofedExecutorAndUnknownActions() {
        AutomationExecutorCapabilityReq request = request("cuecast", List.of("click", "unknown-action"));

        service.registerCapabilities("playwright", PRINCIPAL_SCOPE, request);
        register("cuecast", List.of("click"));

        assertThat(findMethod(catalog(), "web-click").getEnabled()).isTrue();
    }

    @Test
    void shouldEnableHighRiskMethodOnlyWhenAgentHealthMatchesRequirements() {
        register("playwright", List.of("host_command"));
        register("cuecast", List.of("host_command"));

        assertThat(findMethod(catalog(), "windows.command").getDisabledCode())
            .isEqualTo("HOST_COMMAND_AGENT_NOT_READY");
        AutomationOperationCatalog ready = service
            .getCatalog(SCENE_ID, ENVIRONMENT_ID, PRINCIPAL_SCOPE, SESSION_ID, true, true, Set.of("runner-host"), Set
                .of("host_command"));
        assertThat(findMethod(ready, "windows.command").getEnabled()).isTrue();
    }

    private AutomationOperationCatalog catalog() {
        return service.getCatalog(SCENE_ID, ENVIRONMENT_ID, PRINCIPAL_SCOPE, SESSION_ID, true, true, Set.of(), Set
            .of());
    }

    private List<AutomationOperationCatalog.OperationMethod> methods() {
        return catalog().getTypes().stream().flatMap(type -> type.getMethods().stream()).toList();
    }

    private void assertFieldPresentation(AutomationOperationCatalog.OperationMethod method, Map<String, Object> field) {
        String component = String.valueOf(field.get("component"));
        String name = String.valueOf(field.get("name"));
        if ("select".equals(component)) {
            assertThat(field.get("options")).as(method.getMethodCode() + "." + name).isInstanceOf(Collection.class);
            Collection<?> options = (Collection<?>)field.get("options");
            assertThat(options).isNotEmpty();
            assertThat(options.stream().map(option -> String.valueOf(((Map<?, ?>)option).get("value"))).toList())
                .doesNotHaveDuplicates();
        } else {
            assertThat(String.valueOf(field.getOrDefault("placeholder", "")) + String.valueOf(field
                .getOrDefault("help", ""))).as(method.getMethodCode() + "." + name).isNotBlank();
        }
        if (field.containsKey("default")) {
            assertThat(name).as(method.getMethodCode())
                .doesNotMatch("(?i).*(url|sql|command|path|file_ref|target_ref|certificate|variable_name|value|expect|script).*");
        }
    }

    private void assertConditionsReferenceDeclaredFields(AutomationOperationCatalog.OperationMethod method,
                                                         Map<String, Object> field) {
        Set<String> names = method.getFormSchema()
            .stream()
            .map(item -> String.valueOf(item.get("name")))
            .collect(java.util.stream.Collectors.toSet());
        for (String conditionName : List.of("visible_when", "required_when")) {
            if (field.get(conditionName) instanceof Map<?, ?> condition) {
                assertThat(condition.keySet().stream().map(String::valueOf)).allMatch(names::contains);
            }
        }
    }

    private Map<String, Object> field(AutomationOperationCatalog.OperationMethod method, String name) {
        return method.getFormSchema().stream().filter(item -> name.equals(item.get("name"))).findFirst().orElseThrow();
    }

    private AutomationOperationCatalog.OperationMethod findMethod(AutomationOperationCatalog source, String code) {
        return source.getTypes()
            .stream()
            .flatMap(type -> type.getMethods().stream())
            .filter(method -> code.equals(method.getMethodCode()) || code.equals(method.getLegacyAction()))
            .findFirst()
            .orElseThrow();
    }

    private void register(String executor, List<String> actions) {
        registerWithInstance(executor, actions, executor + "-node-1");
    }

    private void registerWithInstance(String executor, List<String> actions, String instanceId) {
        AutomationExecutorCapabilityReq request = request(executor, actions);
        request.setExecutorInstanceId(instanceId);
        service.registerCapabilities(executor, PRINCIPAL_SCOPE, request);
    }

    private AutomationExecutorCapabilityReq request(String executor, List<String> actions) {
        AutomationExecutorCapabilityReq request = new AutomationExecutorCapabilityReq();
        request.setExecutor(executor);
        request.setExecutorInstanceId(executor + "-node-1");
        request.setExecutorVersion("1.0.0");
        request.setCatalogVersion("2026-08-07.1");
        request.setProjectEnvironmentId(ENVIRONMENT_ID);
        request.setSessionId("cuecast".equals(executor) ? SESSION_ID : null);
        request.setActions(actions);
        request.setFeatures(List.of("browser"));
        return request;
    }
}
