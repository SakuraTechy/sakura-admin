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

import org.junit.jupiter.api.Test;

class PlaywrightActionMappingTest {

    @Test
    void shouldResolveKnownActions() {
        assertThat(PlaywrightActionMapping.resolve("navigate").operationValue()).isEqualTo("pw-navigate");
        assertThat(PlaywrightActionMapping.resolve("click").operationValue()).isEqualTo("pw-click");
        assertThat(PlaywrightActionMapping.resolve("input").operationValue()).isEqualTo("pw-input");
        assertThat(PlaywrightActionMapping.resolve("key").operationValue()).isEqualTo("pw-key");
        assertThat(PlaywrightActionMapping.resolve("assert_text").operationValue()).isEqualTo("pw-assert-text");
    }

    @Test
    void shouldFallbackUnknownActionToCustom() {
        PlaywrightActionMapping.ActionDisplay display = PlaywrightActionMapping.resolve("custom_drag_magic");

        assertThat(display.operationType()).isEqualTo("Playwright 操作");
        assertThat(display.operationName()).isEqualTo("自定义动作");
        assertThat(display.operationValue()).isEqualTo("pw-custom");
    }
}
