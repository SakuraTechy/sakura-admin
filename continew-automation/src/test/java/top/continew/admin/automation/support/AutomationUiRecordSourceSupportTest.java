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

import org.junit.jupiter.api.Test;

class AutomationUiRecordSourceSupportTest {

    @Test
    void shouldApplyFixedPriorityWithoutUsingBuildNumber() {
        assertThat(AutomationUiRecordSourceSupport.classify("internal-interactive-context", "schedule", 1L, 2L))
            .isEqualTo("internal");
        assertThat(AutomationUiRecordSourceSupport.classify("interactive-execution-context", "manual", null, null))
            .isEqualTo("internal");
        assertThat(AutomationUiRecordSourceSupport.classify("execution", "schedule", null, null)).isEqualTo("test");
        assertThat(AutomationUiRecordSourceSupport.classify("execution", "manual", null, 2L)).isEqualTo("test");
        assertThat(AutomationUiRecordSourceSupport.classify("execution", "jenkins", null, null)).isEqualTo("debug");
        assertThat(AutomationUiRecordSourceSupport.classify(null, null, null, null)).isEqualTo("debug");
        assertThat(AutomationUiRecordSourceSupport.classify(null, null, 1L, null)).isEqualTo("test");
    }
}
