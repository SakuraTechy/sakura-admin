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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CDP 三模式灰度策略测试。
 */
class AutomationCdpPlaybackPolicyTest {

    @Test
    void shouldDefaultToDenyWhenFeatureIsDisabled() {
        AutomationCdpPlaybackPolicy policy = new AutomationCdpPlaybackPolicy(false, "tester");

        assertThat(policy.isManagedContextAllowed("tester")).isFalse();
        assertThat(policy.unavailableReason()).contains("灰度开关未开启");
    }

    @Test
    void shouldAllowOnlyConfiguredUsernamesCaseInsensitively() {
        AutomationCdpPlaybackPolicy policy = new AutomationCdpPlaybackPolicy(true, " qa-user, TestUser ");

        assertThat(policy.isManagedContextAllowed("TESTUSER")).isTrue();
        assertThat(policy.isManagedContextAllowed("other-user")).isFalse();
    }
}
