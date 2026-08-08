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
        adapter = new AutomationOperationStepReverseAdapter(objectMapper, catalogService);
    }

    @Test
    void shouldRecognizeEveryLegacyActionInThe62MethodFixture() throws Exception {
        JsonNode fixture = objectMapper.readTree(getClass()
            .getResourceAsStream("/automation/automation-operation-62-fixture.json"));
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
