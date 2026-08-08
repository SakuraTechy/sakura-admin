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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationOperationCatalogService;

class AutomationPlaywrightStepExtractorTest {

    private final AutomationOperationCatalogService catalogService = mock(AutomationOperationCatalogService.class);
    private final AutomationPlaywrightStepExtractor extractor = new AutomationPlaywrightStepExtractor(new ObjectMapper(), catalogService);

    @Test
    void shouldEnrichRawStepWithAdminSequenceAndNameWithoutChangingStoredJson() {
        String rawStep = "{\"action_type\":\"click\",\"target_selector\":\"#username\"}";
        StepDO stepDO = new StepDO();
        stepDO.setId("STEP_001");
        stepDO.setName("点击 input");
        stepDO.setConfigList(List.of(config("playwright_step", rawStep)));

        Map<String, Object> extracted = extractor.extract(stepDO, 0);

        assertThat(extracted).containsEntry("id", "STEP_001")
            .containsEntry("step_index", 0)
            .containsEntry("description", "点击 input")
            .containsEntry("action_type", "click");
        assertThat(stepDO.getConfigList().get(0).getParamsValue()).isEqualTo(rawStep);
    }

    @Test
    void shouldUseUniqueAdminStepIdAndPreserveRecordedIdForRawStep() {
        String rawStep = "{\"id\":1,\"step_index\":4,\"action_type\":\"click\",\"target_selector\":\"#mysql\"}";
        StepDO stepDO = new StepDO();
        stepDO.setId("CASE_STEP_005");
        stepDO.setName("选择数据库");
        stepDO.setConfigList(List.of(config("playwright_step", rawStep)));

        Map<String, Object> extracted = extractor.extract(stepDO, 4);

        assertThat(extracted).containsEntry("id", "CASE_STEP_005")
            .containsEntry("original_step_id", 1)
            .containsEntry("step_index", 4);
        assertThat(stepDO.getConfigList().get(0).getParamsValue()).isEqualTo(rawStep);
    }

    @Test
    void shouldRestoreLegacyStepThroughOperationCatalogWhenRawStepIsMissing() {
        AutomationOperationCatalog.OperationMethod method = new AutomationOperationCatalog.OperationMethod();
        method.setActionType("navigate");
        when(catalogService.findMethod("web-geturls")).thenReturn(Optional.of(method));
        StepDO stepDO = new StepDO();
        stepDO.setId("STEP_003");
        stepDO.setName("打开登录页");
        stepDO.setOperationValue("web-geturls");
        stepDO.setConfigList(List.of(config("value", "https://example.test/login")));

        Map<String, Object> extracted = extractor.extract(stepDO, 2);

        assertThat(extracted).containsEntry("action_type", "navigate")
            .containsEntry("url", "https://example.test/login")
            .containsEntry("value", "https://example.test/login")
            .containsEntry("step_index", 2);
    }

    @Test
    void shouldRebuildHistoricalManualStepInsteadOfExecutingStaleRawSnapshot() {
        AutomationOperationCatalog.OperationMethod method = new AutomationOperationCatalog.OperationMethod();
        method.setActionType("navigate");
        when(catalogService.findMethod("web-geturls")).thenReturn(Optional.of(method));
        StepDO stepDO = new StepDO();
        stepDO.setId("STEP_004");
        stepDO.setName("打开新地址");
        stepDO.setOperationValue("web-geturls");
        stepDO.setConfigList(List
            .of(config("source", "admin-manual"), config("playwright_step", "{\"action_type\":\"navigate\",\"url\":\"https://stale.example\"}"), config("value", "https://current.example")));

        Map<String, Object> extracted = extractor.extract(stepDO, 3);

        assertThat(extracted).containsEntry("action_type", "navigate")
            .containsEntry("url", "https://current.example")
            .doesNotContainValue("https://stale.example");
    }

    @Test
    void shouldAttachCatalogDiagnosticFieldsToRunnerStep() {
        AutomationOperationCatalog.OperationMethod method = new AutomationOperationCatalog.OperationMethod();
        method.setMethodCode("input.text");
        method.setMethodVersion(1);
        method.setLabel("输入文本");
        method.setActionType("input");
        method.setDiagnosticProfile("element_interaction");
        method.setFormSchema(List.of(Map
            .of("name", "value", "label", "输入值", "diagnostic_role", "input", "sensitivity", "inherit", "result_display", "effective_preview")));
        when(catalogService.findOperation("input.text")).thenReturn(Optional
            .of(new AutomationOperationCatalogService.OperationDescriptor("browser", "浏览器操作", method)));

        StepDO stepDO = new StepDO();
        stepDO.setId("STEP_006");
        stepDO.setName("输入用户名");
        stepDO.setConfigList(List.of(config("method_code", "input.text"), config("value", "sysadmin")));

        Map<String, Object> extracted = extractor.extract(stepDO, 0);

        assertThat(extracted).containsEntry("diagnostic_profile", "element_interaction");
        assertThat((List<?>)extracted.get("diagnostic_fields")).singleElement()
            .isEqualTo(Map
                .of("name", "value", "label", "输入值", "diagnostic_role", "input", "sensitivity", "inherit", "result_display", "effective_preview"));
    }

    @Test
    void shouldKeepCatalogIdentityMetadataOutOfLegacyFallbackExecutionParameters() {
        StepDO stepDO = new StepDO();
        stepDO.setId("STEP_007");
        stepDO.setName("旧步骤");
        stepDO.setOperationValue("web-click");
        stepDO.setConfigList(List
            .of(config("type_code", "click"), config("type_label", "点击操作"), config("method_code", "click.element"), config("method_version", "1"), config("method_label", "元素点击"), config("diagnostic_profile", "element_interaction"), config("target_ref", "#login")));

        Map<String, Object> extracted = extractor.extract(stepDO, 0);

        assertThat(extracted).containsEntry("target_ref", "#login")
            .doesNotContainKeys("type_code", "type_label", "method_code", "method_version", "method_label", "diagnostic_profile");
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
