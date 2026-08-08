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

import java.util.List;
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
    void shouldLoadThirteenTypesAndSixtyTwoMethods() {
        AutomationOperationCatalog catalog = catalog();
        List<AutomationOperationCatalog.OperationMethod> methods = catalog.getTypes()
            .stream()
            .flatMap(type -> type.getMethods().stream())
            .toList();

        assertThat(catalog.getTypes()).hasSize(13);
        assertThat(methods).hasSize(62);
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
            .getResourceAsStream("/automation/automation-operation-62-fixture.json"));
        assertThat(fixture.path("catalog_version").asText()).isEqualTo("2026-07-30.1");
        assertThat(fixture.path("methods").size()).isEqualTo(62);

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
        request.setCatalogVersion("2026-07-30.1");
        request.setProjectEnvironmentId(ENVIRONMENT_ID);
        request.setSessionId("cuecast".equals(executor) ? SESSION_ID : null);
        request.setActions(actions);
        request.setFeatures(List.of("browser"));
        return request;
    }
}
