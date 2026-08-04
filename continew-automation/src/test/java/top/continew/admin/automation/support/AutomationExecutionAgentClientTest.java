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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationExecutionAgentClientTest {

    private final AutomationExecutionAgentClient client = new AutomationExecutionAgentClient(new ObjectMapper());

    @Test
    void shouldLocalizeLegacyUnauthorizedErrorCode() {
        String message = ReflectionTestUtils.invokeMethod(client, "responseError", "{\"error\":\"UNAUTHORIZED\"}");

        assertThat(message).isEqualTo("执行 Agent 请求认证失败：Bearer 令牌缺失、格式错误或与 Agent 配置不匹配");
    }

    @Test
    void shouldPreferDetailedChineseMessage() {
        String message = ReflectionTestUtils
            .invokeMethod(client, "responseError", "{\"error\":\"UNAUTHORIZED\",\"message\":\"Bearer令牌与Agent配置不匹配\"}");

        assertThat(message).isEqualTo("Bearer令牌与Agent配置不匹配");
    }
}
