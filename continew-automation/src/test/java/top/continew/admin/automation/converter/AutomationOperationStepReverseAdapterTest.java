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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;

class AutomationOperationStepReverseAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AutomationOperationStepReverseAdapter adapter;

    @BeforeEach
    void setUp() {
        AutomationOperationCatalogServiceImpl catalogService = new AutomationOperationCatalogServiceImpl(objectMapper);
        catalogService.initialize();
        CuecastRecordingOperationProjector projector = new CuecastRecordingOperationProjector(objectMapper, catalogService, new AutomationOperationConfigValidator());
        adapter = new AutomationOperationStepReverseAdapter(objectMapper, catalogService, projector);
    }

    @Test
    void shouldRecognizeEveryLegacyActionInThe63MethodFixture() throws Exception {
        JsonNode fixture = objectMapper.readTree(getClass()
            .getResourceAsStream("/automation/automation-operation-63-fixture.json"));
        for (JsonNode expected : fixture.path("methods")) {
            StepDO step = step(expected.path("legacy_action").asText());
            AutomationOperationStepReverseAdapter.ReverseResult result = adapter.adapt(step);
            assertThat(result.recognized()).as(expected.path("legacy_action").asText()).isTrue();
            assertThat(result.methodCode()).isEqualTo(expected.path("method_code").asText());
        }
    }

    @Test
    void shouldPreferExistingCanonicalConfigAndNeverMutateStep() {
        StepDO step = step("web-click");
        step.setConfigList(List
            .of(config("method_code", "click.element"), config("method_version", "1"), config("method_config", "{\"target_ref\":{\"scope\":\"page\",\"target_selector\":\"#submit\"}}")));
        AutomationOperationStepReverseAdapter.ReverseResult result = adapter.adapt(step);
        assertThat(result.methodCode()).isEqualTo("click.element");
        assertThat(result.methodConfig()).containsKey("target_ref");
        assertThat(step.getConfigList()).hasSize(3);
    }

    @Test
    void shouldConvertLegacyLocatorAndTargetWithWarnings() {
        StepDO step = step("web-click");
        step.setConfigList(List.of(config("locator", "xpath=//button[@id='submit']")));
        AutomationOperationStepReverseAdapter.ReverseResult locator = adapter.adapt(step);
        assertThat(locator.methodConfig()).containsEntry("target_ref", Map
            .of("scope", "page", "target_xpath", "//button[@id='submit']"));
        assertThat(locator.warnings()).anyMatch(item -> item.contains("locator_meta"));

        StepDO command = step("exe-shell");
        command.setConfigList(List.of(config("device", "server-license"), config("value", "hostname")));
        AutomationOperationStepReverseAdapter.ReverseResult target = adapter.adapt(command);
        assertThat(target.methodConfig()).containsKey("target_ref");
        assertThat(target.warnings()).anyMatch(item -> item.contains("重新确认"));
    }

    @Test
    void shouldNotCreateNewSensitiveConfig() {
        StepDO step = step("web-input");
        step.setConfigList(List.of(config("password", "should-not-be-copied")));
        AutomationOperationStepReverseAdapter.ReverseResult result = adapter.adapt(step);
        assertThat(result.methodConfig()).doesNotContainKey("password");
        assertThat(result.warnings()).anyMatch(item -> item.contains("敏感"));
    }

    @Test
    void shouldNormalizeLegacyDateModeToCurrentCatalogOption() {
        StepDO currentDate = step("web-setdate");
        currentDate.setConfigList(List.of(config("date_mode", "today")));
        StepDO customDate = step("web-setdate");
        customDate.setConfigList(List.of(config("date_mode", "获取自定义时间")));

        assertThat(adapter.adapt(currentDate).methodConfig()).containsEntry("date_mode", "current_datetime");
        assertThat(adapter.adapt(customDate).methodConfig()).containsEntry("date_mode", "custom_datetime");
    }

    @Test
    void shouldMapRecordedTabValueToDeclaredKeyField() {
        StepDO step = step("pw-key");
        step.setConfigList(List.of(config("source", "sakura-playwright"), config("playwright_step", """
            {"action_type":"key","target_selector":"#user_name","value":"Tab"}
            """)));

        AutomationOperationStepReverseAdapter.ReverseResult result = adapter.adapt(step);

        assertThat(result.methodCode()).isEqualTo("windows.key.normal");
        assertThat(result.methodConfig()).containsExactly(Map.entry("key", "Tab"));
    }

    @Test
    void shouldProjectRecordedLocatorFactsOnlyIntoDeclaredTargetReference() {
        StepDO step = step("pw-input");
        step.setConfigList(List.of(config("source", "sakura-playwright"), config("playwright_step", """
            {"action_type":"input","target_selector":"#user_name","target_xpath":"//*[@id='user_name']",
             "locator_meta":{"strategy":"css"},"url":"https://example.test/login","value":"sysadmin"}
            """)));

        AutomationOperationStepReverseAdapter.ReverseResult result = adapter.adapt(step);

        assertThat(result.methodConfig()).containsOnlyKeys("target_ref", "value").containsEntry("value", "sysadmin");
        Map<?, ?> targetRef = (Map<?, ?>)result.methodConfig().get("target_ref");
        assertThat(targetRef.get("target_selector")).isEqualTo("#user_name");
        assertThat(targetRef.get("target_xpath")).isEqualTo("//*[@id='user_name']");
        assertThat(targetRef.get("locator_meta")).isEqualTo(Map.of("strategy", "css"));
    }

    @Test
    void shouldRemoveUndeclaredRecordingFieldsFromExistingMethodConfig() {
        StepDO step = step("input.text");
        step.setConfigList(List
            .of(config("method_code", "input.text"), config("method_version", "1"), config("method_config", """
                {"target_selector":"#user_name","target_xpath":"//*[@id='user_name']",
                 "locator_meta":{"strategy":"css"},"url":"https://example.test/login","value":"sysadmin"}
                """)));

        AutomationOperationStepReverseAdapter.ReverseResult result = adapter.adapt(step);

        assertThat(result.methodConfig()).containsOnlyKeys("target_ref", "value")
            .doesNotContainKeys("target_selector", "target_xpath", "locator_meta", "url");
    }

    private StepDO step(String operationValue) {
        StepDO step = new StepDO();
        step.setOperationValue(operationValue);
        step.setConfigList(new ArrayList<>());
        return step;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
