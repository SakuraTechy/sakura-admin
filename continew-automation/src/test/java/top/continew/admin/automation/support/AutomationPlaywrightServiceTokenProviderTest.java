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
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AutomationPlaywrightServiceTokenProviderTest {

    private final AutomationPlaywrightServiceTokenProvider provider = new AutomationPlaywrightServiceTokenProvider(mock(UserService.class), mock(RoleService.class));

    @Test
    void shouldReturnEmptyTokenWhenProviderIsDisabled() {
        ReflectionTestUtils.setField(provider, "enabled", false);

        assertThat(provider.getToken()).isBlank();
    }

    @Test
    void shouldFailClearlyWhenServiceAccountIsMissing() {
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "serviceAccountUserId", null);

        assertThatThrownBy(provider::getToken).isInstanceOf(RuntimeException.class).hasMessageContaining("服务账号未配置");
    }
}
