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

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

class AutomationUiCaseFingerprintTest {

    @Test
    void shouldIgnoreCasePositionAndRedundantStepOrder() {
        CaseDO first = caseWithSteps("STEP-1", "STEP-2");
        first.setOrder(1);
        first.getStepList().get(0).setOrder(1);
        first.getStepList().get(1).setOrder(2);
        CaseDO second = caseWithSteps("STEP-1", "STEP-2");
        second.setOrder(9);
        second.getStepList().get(0).setOrder(20);
        second.getStepList().get(1).setOrder(30);

        assertThat(fingerprint(first)).isEqualTo(fingerprint(second));
    }

    @Test
    void shouldInvalidateWhenStepListOrderChanges() {
        CaseDO first = caseWithSteps("STEP-1", "STEP-2");
        CaseDO second = caseWithSteps("STEP-2", "STEP-1");

        assertThat(fingerprint(first)).isNotEqualTo(fingerprint(second));
    }

    @Test
    void shouldCanonicalizeConfigAndNestedJsonOrder() {
        CaseDO first = caseWithSteps("STEP-1");
        first.getStepList()
            .get(0)
            .setConfigList(List
                .of(config("locator_meta", "{\"score\":0.8,\"types\":[\"role\",\"css\"]}"), config("source", "sakura-playwright")));
        CaseDO second = caseWithSteps("STEP-1");
        second.getStepList()
            .get(0)
            .setConfigList(List
                .of(config("source", "sakura-playwright"), config("locator_meta", "{\"types\":[\"role\",\"css\"],\"score\":0.8}")));

        assertThat(fingerprint(first)).isEqualTo(fingerprint(second));
    }

    @Test
    void shouldExcludeInlineScreenshotButIncludeSensitiveDefinitionChanges() {
        CaseDO first = caseWithSteps("STEP-1");
        first.getStepList()
            .get(0)
            .setConfigList(new ArrayList<>(List
                .of(config("screenshot", "data:image/png;base64,AAAA"), config("value_masked", "1"), config("value", "secret-a"))));
        CaseDO second = caseWithSteps("STEP-1");
        second.getStepList()
            .get(0)
            .setConfigList(new ArrayList<>(List
                .of(config("screenshot", "data:image/png;base64,BBBB"), config("value_masked", "1"), config("value", "secret-a"))));
        CaseDO changedSecret = caseWithSteps("STEP-1");
        changedSecret.getStepList()
            .get(0)
            .setConfigList(List.of(config("value_masked", "1"), config("value", "secret-b")));

        assertThat(fingerprint(first)).isEqualTo(fingerprint(second));
        assertThat(fingerprint(first)).isNotEqualTo(fingerprint(changedSecret));
        assertThat(AutomationUiCaseFingerprint.compute(first).canonicalJson()).doesNotContain("AAAA", "data:image");
    }

    @Test
    void shouldInvalidateWhenExecutionConfigChanges() {
        CaseDO first = caseWithSteps("STEP-1");
        CaseExecutionConfigDO firstConfig = new CaseExecutionConfigDO();
        firstConfig.setWindowSizeMode("viewport");
        firstConfig.setViewportWidth(1280);
        first.setExecutionConfig(firstConfig);
        CaseDO second = caseWithSteps("STEP-1");
        CaseExecutionConfigDO secondConfig = new CaseExecutionConfigDO();
        secondConfig.setWindowSizeMode("maximized");
        second.setExecutionConfig(secondConfig);

        assertThat(fingerprint(first)).isNotEqualTo(fingerprint(second));
    }

    private String fingerprint(CaseDO caseDO) {
        return AutomationUiCaseFingerprint.compute(caseDO).hash();
    }

    private CaseDO caseWithSteps(String... stepIds) {
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE-1");
        caseDO.setName("下载机器码");
        List<StepDO> steps = new ArrayList<>();
        for (String stepId : stepIds) {
            StepDO step = new StepDO();
            step.setId(stepId);
            step.setPid(caseDO.getId());
            step.setName(stepId);
            step.setOperationType("浏览器操作");
            step.setOperationName("click");
            steps.add(step);
        }
        caseDO.setStepList(steps);
        return caseDO;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
