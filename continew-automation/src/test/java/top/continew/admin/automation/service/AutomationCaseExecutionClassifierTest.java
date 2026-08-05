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

package top.continew.admin.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

class AutomationCaseExecutionClassifierTest {

    @Test
    void shouldUseSameClassificationForBrowserAndInfrastructureEntrypoints() {
        AutomationPlaywrightStepExtractor extractor = mock(AutomationPlaywrightStepExtractor.class);
        AutomationCaseExecutionClassifier classifier = new AutomationCaseExecutionClassifier(extractor);
        CaseDO caseDO = new CaseDO();
        StepDO step = new StepDO();
        caseDO.setStepList(List.of(step));

        when(extractor.extract(any(StepDO.class), anyInt())).thenReturn(Map.of("action_type", "database_sql"));
        assertThat(classifier.hasBrowserSteps(caseDO)).isFalse();

        when(extractor.extract(any(StepDO.class), anyInt())).thenReturn(Map.of("action_type", "click"));
        assertThat(classifier.hasBrowserSteps(caseDO)).isTrue();
    }
}
