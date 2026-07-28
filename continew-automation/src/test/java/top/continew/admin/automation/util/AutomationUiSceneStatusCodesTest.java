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

package top.continew.admin.automation.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutomationUiSceneStatusCodesTest {

    @Test
    void shouldNormalizeCancelledResultToDictionaryValue() {
        assertThat(AutomationUiSceneStatusCodes.normalizeResult("cancelled", null, null, null, null))
            .isEqualTo(AutomationUiSceneStatusCodes.RESULT_CANCELLED);
        assertThat(AutomationUiSceneStatusCodes.normalizeResult("已取消", null, null, null, null))
            .isEqualTo(AutomationUiSceneStatusCodes.RESULT_CANCELLED);
        assertThat(AutomationUiSceneStatusCodes.normalizeResult("17", null, null, null, null))
            .isEqualTo(AutomationUiSceneStatusCodes.RESULT_CANCELLED);
    }

    @Test
    void shouldNormalizeCancelledStatusToDictionaryValue() {
        assertThat(AutomationUiSceneStatusCodes.normalizeStatus("cancelled"))
            .isEqualTo(AutomationUiSceneStatusCodes.STATUS_CANCELLED);
        assertThat(AutomationUiSceneStatusCodes.normalizeStatus("17"))
            .isEqualTo(AutomationUiSceneStatusCodes.STATUS_CANCELLED);
    }
}
