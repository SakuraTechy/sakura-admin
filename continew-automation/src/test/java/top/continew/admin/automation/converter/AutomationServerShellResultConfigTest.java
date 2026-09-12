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

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;
import top.continew.admin.automation.util.AutomationUiSceneXmlUtils;

class AutomationServerShellResultConfigTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AutomationOperationCatalogServiceImpl catalog = new AutomationOperationCatalogServiceImpl(mapper);

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n\r", "$1", "\\$", "\\\\"})
    void preservesConfigCanonicalAndXml(String replacement, @TempDir Path directory) throws Exception {
        catalog.initialize();
        Map<String, Object> config = config();
        config.put("replace_regex", "(x)");
        config.put("replace_value", replacement);
        StepDO step = assemble(config);
        Map<String, String> values = configs(step);
        assertThat(values).containsEntry("key", "(x)")
            .containsEntry("value", replacement)
            .containsEntry("details", "key:disk");
        assertThat(mapper.readTree(values.get("method_config")).path("replace_value").asText()).isEqualTo(replacement);
        assertThat(mapper.readTree(values.get("playwright_step")).path("replace_value").asText())
            .isEqualTo(replacement);
        var extracted = new AutomationPlaywrightStepExtractor(mapper, catalog).extract(step, 0);
        assertThat(extracted).containsEntry("replace_value", replacement);

        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE_1");
        caseDO.setOrder(1);
        caseDO.setStepList(List.of(step));
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SHELL");
        scene.setVersionName("V1");
        scene.setCaseList(List.of(caseDO));
        var bundle = AutomationUiSceneXmlUtils.createBundle(List
            .of(scene), "test", "test", "V1", "chrome", "local", "", "", directory, Map.of());
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element xmlStep = (Element)factory.newDocumentBuilder()
            .parse(bundle.testCaseDir().resolve("SHELL.xml").toFile())
            .getElementsByTagName("step")
            .item(0);
        assertThat(xmlStep.hasAttribute("value")).isTrue();
        assertThat(xmlStep.getAttribute("value")).isEqualTo(replacement);
        assertThat(Files.readString(bundle.testCaseDir().resolve("SHELL.xml"))).doesNotContain("playwright_step");
    }

    @Test
    void clearingBindingRemovesBothLegacyAndCanonicalFields() throws Exception {
        catalog.initialize();
        Map<String, Object> config = config();
        config.put("replace_regex", "\\n");
        StepDO step = assemble(config);
        config.remove("replace_regex");
        config.remove("variable_name");
        step.getConfigList().removeIf(item -> "method_config".equals(item.getParamsName()));
        step.getConfigList().add(field("method_config", mapper.writeValueAsString(config)));
        assembler().assembleManualStep(step);
        assertThat(configs(step)).doesNotContainKeys("key", "value", "details", "variable_name");
        assertThat(configs(step).get("playwright_step"))
            .doesNotContain("replace_regex", "replace_value", "variable_name");
    }

    @Test
    void reversesLegacyFieldsAndKeepsWhitespace() {
        catalog.initialize();
        StepDO legacy = new StepDO();
        legacy.setOperationValue("exe-shell");
        legacy.setConfigList(List
            .of(field("device", "AAS_P"), field("shell", "printf 'x'"), field("key", " "), field("value", "  "), field("details", "key:disk")));
        var reverse = new AutomationOperationStepReverseAdapter(mapper, catalog, new CuecastRecordingOperationProjector(mapper, catalog, new AutomationOperationConfigValidator()));
        var result = reverse.adapt(legacy);
        assertThat(result.methodConfig()).containsEntry("shell", "bash")
            .containsEntry("command", "printf 'x'")
            .containsEntry("replace_regex", " ")
            .containsEntry("replace_value", "  ")
            .containsEntry("variable_name", "disk");
        assertThat(result.warnings()).isNotEmpty();
        assertThat(legacy.getConfigList()).hasSize(5);
    }

    @Test
    void rejectsInvalidNamesExpressionsCombinationsAndTypedValues() {
        for (Map<String, Object> values : List.<Map<String, Object>>of(Map.of("replace_regex", "\\n"), Map
            .of("replace_value", " "), Map.of("variable_name", "system.disk"), Map.of("variable_name", "123disk"), Map
                .of("variable_name", "disk", "replace_regex", "["), Map
                    .of("variable_name", "disk", "replace_regex", "(x)", "replace_value", "$9"), Map
                        .of("variable_name", "disk", "replace_regex", "(x)", "replace_value", "\\"), Map
                            .of("variable_name", "disk", "replace_regex", "${input}"), Map.of("variable_name", 12))) {
            assertThatThrownBy(() -> AutomationServerShellResultConfig.from(values))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"regex", "method_config", "playwright_step"})
    void keepsHistoricalExtractionReadOnlyRegardlessOfStorage(String location) throws Exception {
        catalog.initialize();
        StepDO legacy = new StepDO();
        legacy.setOperationValue("exe-shell");
        Map<String, Object> original = config();
        original.put("action_type", "server_command");
        original.put("regex", "\\d+");
        List<StepDO.Config> fields = new ArrayList<>(List.of(field("shell", "printf 'size=12'")));
        if ("method_config".equals(location)) {
            fields.add(field("method_code", "server.shell"));
        }
        fields.add(field(location, "regex".equals(location) ? "\\d+" : mapper.writeValueAsString(original)));
        legacy.setConfigList(fields);
        String before = mapper.writeValueAsString(legacy);
        var reverse = new AutomationOperationStepReverseAdapter(mapper, catalog, new CuecastRecordingOperationProjector(mapper, catalog, new AutomationOperationConfigValidator()));

        var result = reverse.adapt(legacy);

        assertThat(result.recognized()).isFalse();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("正则提取"));
        assertThat(mapper.writeValueAsString(legacy)).isEqualTo(before);
    }

    @Test
    void canonicalShellConversionCannotDiscardOldRawExtraction() throws Exception {
        catalog.initialize();
        StepDO step = assemble(config());
        step.getConfigList().removeIf(item -> "playwright_step".equals(item.getParamsName()));
        step.getConfigList().add(field("playwright_step", "{\"action_type\":\"server_command\",\"regex\":\"\\\\d+\"}"));

        assertThatThrownBy(() -> assembler().assembleManualStep(step)).hasMessageContaining("正则提取");
    }

    @Test
    void executionCannotSilentlyIgnoreLegacyExtraction() {
        assertThatThrownBy(() -> AutomationServerShellResultConfig.from(Map
            .of("variable_name", "disk", "regex", "\\d+"))).hasMessageContaining("正则提取");
    }

    private Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("target_ref", Map.of("scope", "project_config", "kind", "server", "config_id", 1));
        config.put("shell", "bash");
        config.put("command", "printf 'x'");
        config.put("variable_name", "disk");
        return config;
    }

    private StepDO assemble(Map<String, Object> config) throws Exception {
        StepDO step = new StepDO();
        step.setId("STEP_1");
        step.setName("读取磁盘");
        step.setOrder(1);
        step.setConfigList(new ArrayList<>(List
            .of(field("method_code", "server.shell"), field("method_version", "1"), field("method_config", mapper
                .writeValueAsString(config)))));
        return assembler().assembleManualStep(step);
    }

    private AutomationOperationStepAssembler assembler() {
        return new AutomationOperationStepAssembler(mapper, catalog, new AutomationOperationConfigValidator());
    }

    private Map<String, String> configs(StepDO step) {
        return step.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));
    }

    private StepDO.Config field(String name, String value) {
        StepDO.Config field = new StepDO.Config();
        field.setParamsName(name);
        field.setParamsValue(value);
        return field;
    }
}
