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
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.StepDO;

class AutomationPlaywrightStepExtractorTest {

    private final AutomationPlaywrightStepExtractor extractor = new AutomationPlaywrightStepExtractor(new ObjectMapper());

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

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
