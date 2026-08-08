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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedStepReq;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;

class CuecastRecordingOperationProjectorTest {

    private CuecastRecordingOperationProjector projector;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationOperationCatalogServiceImpl catalogService = new AutomationOperationCatalogServiceImpl(objectMapper);
        catalogService.initialize();
        projector = new CuecastRecordingOperationProjector(objectMapper, catalogService, new AutomationOperationConfigValidator());
    }

    @Test
    void shouldProjectRecordedVariableWithoutUsingValueSnapshot() {
        PlaywrightRecordedStepReq step = new PlaywrightRecordedStepReq();
        step.setActionType("set_variable");
        step.setTargetSelector(".order-number");
        step.setTargetXpath("//*[@class='order-number']");
        step.setValue("order_number");
        step.addExtra("value_text", "ORD-20260807");
        step.setLocatorMeta(Map.of("version", 1, "candidates", List.of(Map
            .of("type", "css_unique", "value", ".order-number")), "context", Map.of("variable", Map
                .of("name", "order_number", "source", "text", "extract", Map
                    .of("mode", "regex", "pattern", "ORD-(\\d+)", "group", 0)))));

        CuecastRecordingOperationProjector.RecordedOperationProjection result = projector.project(step);

        assertThat(result.recognized()).isTrue();
        assertThat(result.methodCode()).isEqualTo("global.variable.set");
        assertThat(result.methodConfig()).containsEntry("variable_name", "order_number")
            .containsEntry("source_type", "locator")
            .containsEntry("read_mode", "text")
            .containsEntry("regex", "ORD-(\\d+)")
            .containsEntry("regex_group", 0)
            .doesNotContainKey("value")
            .doesNotContainKey("value_text");
        assertThat(result.methodConfig().get("target_ref"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsKeys("target_selector", "target_xpath", "locator_meta");
    }

    @Test
    void shouldProjectEveryRecordedElementAssertionMode() {
        for (String matchMode : List.of("contains", "equals", "not_contains", "regex", "visible")) {
            PlaywrightRecordedStepReq step = assertionStep(matchMode);

            CuecastRecordingOperationProjector.RecordedOperationProjection result = projector.project(step);

            assertThat(result.recognized()).as(matchMode).isTrue();
            assertThat(result.methodCode()).isEqualTo("assertion.element.match");
            assertThat(result.methodConfig()).containsEntry("match_mode", matchMode)
                .containsEntry("read_mode", "value");
            if ("visible".equals(matchMode)) {
                assertThat(result.methodConfig()).doesNotContainKey("expect");
            } else {
                assertThat(result.methodConfig()).containsEntry("expect", "系统管理平台");
            }
        }
    }

    @Test
    void shouldReturnWarningInsteadOfDroppingInvalidRecordedStep() {
        PlaywrightRecordedStepReq step = assertionStep("unknown");

        CuecastRecordingOperationProjector.RecordedOperationProjection result = projector.project(step);

        assertThat(result.attempted()).isTrue();
        assertThat(result.recognized()).isFalse();
        assertThat(result.warnings()).containsExactly("RECORDED_ASSERTION_MATCH_UNSUPPORTED");
    }

    private PlaywrightRecordedStepReq assertionStep(String matchMode) {
        PlaywrightRecordedStepReq step = new PlaywrightRecordedStepReq();
        step.setActionType("assert_text");
        step.setTargetSelector("#title");
        step.setValue("visible".equals(matchMode) ? "" : "系统管理平台");
        step.setLocatorMeta(Map.of("assertion", Map.of("target", "element", "match", matchMode), "context", Map
            .of("assertion", Map.of("target", "element", "match", matchMode, "source", "value"))));
        return step;
    }
}
