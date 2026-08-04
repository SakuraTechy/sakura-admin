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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AutomationInfrastructureRuntimeBindingResolverTest {

    private final AutomationInfrastructureRuntimeBindingResolver resolver = new AutomationInfrastructureRuntimeBindingResolver(new ObjectMapper());

    @Test
    void shouldOnlyResolveVariablesFrozenInRawStep() {
        Map<String, Object> frozen = Map.of("action_type", "host_command", "command", "echo ${user}", "parameters", List
            .of("${count}"));

        Map<String, Object> result = resolver.resolve(frozen, Map.of("user", "alice", "count", 3));

        assertThat(result).containsEntry("command", "echo alice");
        assertThat(result.get("parameters")).isEqualTo(List.of(3));
        assertThat(frozen.get("command")).isEqualTo("echo ${user}");
    }

    @Test
    void shouldRejectMissingOrUnreferencedBindings() {
        Map<String, Object> frozen = Map.of("action_type", "host_command", "command", "echo ${user}");

        assertThatThrownBy(() -> resolver.resolve(frozen, Map.of("ignored", "value"))).hasMessageContaining("未引用");
        assertThatThrownBy(() -> resolver.resolve(frozen, Map.of())).hasMessageContaining("缺少运行时变量：user");
    }

    @Test
    void shouldRejectRoutingVariables() {
        assertThatThrownBy(() -> resolver.rejectVariablesInRoutingFields(Map
            .of("action_type", "host_command", "target_ref", Map.of("config_id", "${target}"))))
            .hasMessageContaining("target_ref 不允许");
    }
}
