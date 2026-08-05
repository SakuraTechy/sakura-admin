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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;

class AutomationRecordingActionResolverTest {

    private AutomationRecordingActionResolver resolver;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationOperationCatalogServiceImpl catalogService = new AutomationOperationCatalogServiceImpl(objectMapper);
        catalogService.initialize();
        resolver = new AutomationRecordingActionResolver(new AutomationOperationStepReverseAdapter(objectMapper, catalogService));
    }

    @Test
    void shouldUseExplicitRecordingSourceAsRecordingFact() {
        StepDO step = step("web-click", config("source", "sakura-playwright"), config("playwright_step", "{\"action_type\":\"web-click\"}"));

        AutomationRecordingActionResolver.Resolution result = resolver.resolve(step);

        assertThat(result.recording()).isTrue();
        assertThat(result.source()).isEqualTo("sakura-playwright");
    }

    @Test
    void shouldNotTreatCanonicalSnapshotAloneAsRecording() {
        StepDO step = step("web-click", config("playwright_step", "{\"action_type\":\"web-click\"}"), config("method_code", "click.element"), config("method_config", "{}"));

        AutomationRecordingActionResolver.Resolution result = resolver.resolve(step);

        assertThat(result.recording()).isFalse();
        assertThat(result.source()).isEqualTo("admin-manual");
    }

    private StepDO step(String operationValue, StepDO.Config... configs) {
        StepDO step = new StepDO();
        step.setOperationValue(operationValue);
        step.setConfigList(List.of(configs));
        return step;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
