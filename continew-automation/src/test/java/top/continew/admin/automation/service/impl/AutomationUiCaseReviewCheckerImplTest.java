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

package top.continew.admin.automation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationUiCaseReviewChecker;

class AutomationUiCaseReviewCheckerImplTest {

    private final AutomationUiCaseReviewChecker checker = new AutomationUiCaseReviewCheckerImpl();

    @Test
    void shouldProduceBoundedBlockersWithoutRawSensitiveEvidence() {
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE-1");
        StepDO step = new StepDO();
        step.setId("STEP-1");
        step.setName("输入口令");
        step.setOperationName("fill");
        step.setOperationValue("top-secret");
        step.setConfigList(List
            .of(config("source", "sakura-playwright"), config("value_masked", "1"), config("value", "top-secret"), config("screenshot", "data:image/png;base64,AAAA")));
        caseDO.setStepList(List.of(step));

        List<AutomationUiCaseReviewChecker.Result> results = checker
            .check(caseDO, new AutomationUiCaseReviewChecker.ExecutionFacts(false, 0, 0, null));

        assertThat(results).hasSize(12);
        assertThat(results).anySatisfy(result -> {
            assertThat(result.ruleCode()).isEqualTo("PLAYWRIGHT_STEP_PRESERVED");
            assertThat(result.result()).isEqualTo("FAIL");
            assertThat(result.severity()).isEqualTo("BLOCKER");
        });
        assertThat(results).anySatisfy(result -> {
            assertThat(result.ruleCode()).isEqualTo("SCREENSHOT_NOT_INLINE");
            assertThat(result.result()).isEqualTo("FAIL");
        });
        assertThat(results.toString()).doesNotContain("top-secret", "AAAA", "data:image");
    }

    @Test
    void shouldWarnForPreservedCustomActionInsteadOfBlockingIt() {
        CaseDO caseDO = new CaseDO();
        StepDO step = new StepDO();
        step.setId("STEP-1");
        step.setName("兼容动作");
        step.setOperationName("pw-custom");
        step.setConfigList(List.of(config("playwright_step", "{\"action_type\":\"future-action\"}")));
        caseDO.setStepList(List.of(step));

        AutomationUiCaseReviewChecker.Result result = checker
            .check(caseDO, new AutomationUiCaseReviewChecker.ExecutionFacts(true, 1, 0, "passed"))
            .stream()
            .filter(item -> "CUSTOM_ACTION_PRESERVED".equals(item.ruleCode()))
            .findFirst()
            .orElseThrow();

        assertThat(result.result()).isEqualTo("WARNING");
        assertThat(result.severity()).isEqualTo("MAJOR");
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
