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

package top.continew.admin.automation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

class AutomationUiDefinitionDisplayMaskerTest {

    private final AutomationUiDefinitionDisplayMasker masker = new AutomationUiDefinitionDisplayMasker();

    @Test
    void shouldMaskDisplayCopyWithoutChangingExecutionFacts() {
        StepDO step = new StepDO();
        step.setOperationValue("secret");
        step.setConfigList(List
            .of(config("value_masked", "1"), config("value", "secret"), config("playwright_step", "{\"action_type\":\"input\",\"value\":\"secret\",\"locator_meta\":{\"role\":\"textbox\"}}"), config("original_playwright_step", "{\"action_type\":\"input\",\"value\":\"secret\"}"), config("locator_meta", "{\"role\":\"textbox\"}")));
        CaseDO caseDO = new CaseDO();
        caseDO.setStepList(List.of(step));

        List<CaseDO> masked = masker.mask(List.of(caseDO));
        StepDO maskedStep = masked.get(0).getStepList().get(0);

        assertThat(maskedStep.getOperationValue()).isEqualTo("******");
        assertThat(configValue(maskedStep, "value")).isEqualTo("******");
        assertThat(configValue(maskedStep, "playwright_step")).contains("\"value\":\"******\"")
            .contains("\"locator_meta\"");
        assertThat(configValue(maskedStep, "original_playwright_step")).contains("\"value\":\"******\"");
        assertThat(configValue(maskedStep, "locator_meta")).isEqualTo("{\"role\":\"textbox\"}");
        assertThat(step.getOperationValue()).isEqualTo("secret");
        assertThat(configValue(step, "playwright_step")).contains("\"value\":\"secret\"");
    }

    @Test
    void shouldFailClosedForMalformedCanonicalStep() {
        StepDO step = new StepDO();
        step.setConfigList(List.of(config("value_masked", "true"), config("playwright_step", "not-json")));
        CaseDO caseDO = new CaseDO();
        caseDO.setStepList(List.of(step));

        StepDO maskedStep = masker.mask(List.of(caseDO)).get(0).getStepList().get(0);

        assertThat(configValue(maskedStep, "playwright_step")).isEqualTo("******");
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

    private String configValue(StepDO step, String name) {
        return step.getConfigList()
            .stream()
            .filter(config -> name.equals(config.getParamsName()))
            .map(StepDO.Config::getParamsValue)
            .findFirst()
            .orElse(null);
    }
}
