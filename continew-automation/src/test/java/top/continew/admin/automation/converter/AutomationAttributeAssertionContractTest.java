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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedCaseReq;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedStepReq;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;

class AutomationAttributeAssertionContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AutomationOperationCatalogServiceImpl catalog;
    private AutomationOperationConfigValidator validator;
    private CuecastRecordingOperationProjector projector;
    private AutomationOperationStepAssembler assembler;
    private AutomationOperationStepReverseAdapter reverse;
    private AutomationPlaywrightStepExtractor extractor;

    @BeforeEach
    void setUp() {
        catalog = new AutomationOperationCatalogServiceImpl(mapper);
        catalog.initialize();
        validator = new AutomationOperationConfigValidator();
        projector = new CuecastRecordingOperationProjector(mapper, catalog, validator);
        assembler = new AutomationOperationStepAssembler(mapper, catalog, validator);
        reverse = new AutomationOperationStepReverseAdapter(mapper, catalog, projector);
        extractor = new AutomationPlaywrightStepExtractor(mapper, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contains", "equals", "not_contains", "regex"})
    void shouldRoundTripAttributeRecordingAndEditedSnapshot(String matchMode) throws Exception {
        PlaywrightRecordedStepReq raw = recorded(matchMode, "class");
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setSteps(List.of(raw));
        PlaywrightRecordingAssembler importer = new PlaywrightRecordingAssembler(mapper, mock(AutomationRecordingScreenshotService.class), catalog, projector);
        StepDO imported = importer
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-attribute", "TEST", "V1", "SCENE", false, false))
            .getStepList()
            .get(0);
        Map<String, String> importedConfigs = configs(imported);
        assertThat(imported.getOperationValue()).isEqualTo("web-assert-element-match");
        assertThat(importedConfigs.get("method_code")).isEqualTo("assertion.element.match");
        assertThat(mapper.readTree(importedConfigs.get("playwright_step")).get("locator_meta")).isEqualTo(mapper
            .valueToTree(raw.getLocatorMeta()));
        assertThat(extractor.extract(imported, 1)).containsEntry("action_type", "assert_text")
            .containsEntry("value", " icon-inner  running ");

        Map<String, Object> editConfig = new LinkedHashMap<>(reverse.adapt(imported).methodConfig());
        assertThat(editConfig).containsEntry("attribute", "class")
            .containsEntry("read_mode", "attribute")
            .containsEntry("expect", " icon-inner  running ");
        editConfig.put("attribute", "data-status");
        editConfig.put("expect", "stopped");
        StepDO edited = manual(editConfig);
        // 编辑只替换当前执行快照，原始 playwright_step 和 locator_meta 仍可追溯。
        edited.getConfigList().add(config("original_playwright_step", importedConfigs.get("playwright_step")));
        edited.getConfigList().add(config("original_locator_meta", importedConfigs.get("locator_meta")));
        assembler.assembleManualStep(edited);
        assertThat(configs(edited)).containsEntry("read_mode", "attribute").containsEntry("value", "data-status");
        assertThat(extractor.extract(edited, 1)).containsEntry("action_type", "assert_element_match")
            .containsEntry("read_mode", "attribute")
            .containsEntry("attribute", "data-status")
            .containsEntry("expect", "stopped");
        assertThat(reverse.adapt(edited).methodConfig()).containsEntry("attribute", "data-status");
        assertThat(configs(edited).get("original_playwright_step")).isEqualTo(importedConfigs.get("playwright_step"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bad name", "class=running", "<class>", "class\""})
    void shouldRejectInvalidAttributeNames(String attribute) {
        assertThatThrownBy(() -> assembler.assembleManualStep(manual(base("equals", attribute))))
            .hasMessageContaining("METHOD_CONFIG_INVALID");
        assertThat(projector.project(recorded("equals", attribute)).recognized()).isFalse();
    }

    @Test
    void shouldRejectMissingAttributeAndPreserveUnrecognizedRecording() {
        Map<String, Object> values = base("equals", "class");
        values.remove("attribute");
        assertThatThrownBy(() -> assembler.assembleManualStep(manual(values))).hasMessageContaining("属性名");
        PlaywrightRecordedStepReq raw = recorded("equals", "class");
        raw.setLocatorMeta(Map.of("assertion", Map.of("target", "element", "match", "equals"), "context", Map
            .of("assertion", Map.of("source", "attribute"))));
        var projection = projector.project(raw);
        assertThat(projection.attempted()).isTrue();
        assertThat(projection.recognized()).isFalse();
        assertThat(projection.warnings()).isNotEmpty();
    }

    @Test
    void shouldIgnoreReadFieldsForVisibleAndRejectStaleAttributeForText() throws Exception {
        Map<String, Object> visible = base("visible", "class");
        visible.remove("expect");
        Map<String, Object> result = extractor.extract(assembler.assembleManualStep(manual(visible)), 1);
        assertThat(result).doesNotContainKeys("read_mode", "attribute", "expect");
        Map<String, Object> text = base("equals", "class");
        text.put("read_mode", "text");
        assertThatThrownBy(() -> assembler.assembleManualStep(manual(text))).hasMessageContaining("属性名");
    }

    @Test
    void shouldKeepLegacyAttributeMethodUnchanged() {
        var method = catalog.findMethod("assertion.attribute").orElseThrow();
        assertThat(method.getActionType()).isEqualTo("assert_attribute");
        assertThat(method.getLegacyAction()).isEqualTo("web-checkvalue");
        assertThat(method.getFormSchema()).extracting(field -> field.get("name"))
            .containsExactly("target_ref", "attribute", "expect");
    }

    private PlaywrightRecordedStepReq recorded(String match, String attribute) {
        PlaywrightRecordedStepReq raw = new PlaywrightRecordedStepReq();
        raw.setId(1);
        raw.setActionType("assert_text");
        raw.setTargetSelector("#engine-state");
        raw.setValue(" icon-inner  running ");
        raw.setLocatorMeta(Map.of("version", 1, "candidates", List.of(Map
            .of("type", "css_id", "value", "#engine-state")), "assertion", Map
                .of("target", "element", "match", match), "context", Map.of("assertion", Map
                    .of("target", "element", "match", match, "source", "attribute", "attribute", attribute))));
        return raw;
    }

    private Map<String, Object> base(String match, String attribute) {
        return new LinkedHashMap<>(Map
            .of("target_ref", "css=#engine-state", "read_mode", "attribute", "attribute", attribute, "match_mode", match, "expect", "running"));
    }

    private StepDO manual(Map<String, Object> values) throws Exception {
        StepDO step = new StepDO();
        step.setId("CASE_STEP_001");
        step.setName("检查状态属性");
        step.setConfigList(new ArrayList<>(List
            .of(config("method_code", "assertion.element.match"), config("method_version", "1"), config("method_config", mapper
                .writeValueAsString(values)))));
        return step;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

    private Map<String, String> configs(StepDO step) {
        return step.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));
    }
}
