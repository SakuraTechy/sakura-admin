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
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;

class AutomationInfrastructureRuntimeBindingResolverTest {

    private final AutomationInfrastructureRuntimeBindingResolver resolver = new AutomationInfrastructureRuntimeBindingResolver(new ObjectMapper());

    @Test
    void shouldOnlyResolveVariablesFrozenInRawStep() {
        Map<String, Object> frozen = Map
            .of("action_type", "host_command", "command", "echo {{user}}", "parameters", List.of("${count}"));

        Map<String, Object> result = resolver.resolve(frozen, Map.of("user", "alice", "count", 3));

        assertThat(result).containsEntry("command", "echo alice");
        assertThat(result.get("parameters")).isEqualTo(List.of(3));
        assertThat(frozen.get("command")).isEqualTo("echo {{user}}");
    }

    @Test
    void shouldRejectMissingOrUnreferencedBindings() {
        Map<String, Object> frozen = Map.of("action_type", "host_command", "command", "echo ${user}");

        assertThatThrownBy(() -> resolver.resolve(frozen, Map.of("ignored", "value"))).hasMessageContaining("未引用");
        assertThatThrownBy(() -> resolver.resolve(frozen, Map.of())).hasMessageContaining("缺少运行时变量：user");
    }

    @Test
    void shouldResolveAvailableIpWithoutBindingCatalogExamples() throws Exception {
        List<Map<String, Object>> diagnosticFields;
        try (var input = getClass().getResourceAsStream("/automation/automation-operation-catalog.json")) {
            var catalog = new ObjectMapper().readValue(input, AutomationOperationCatalog.class);
            diagnosticFields = catalog.getTypes()
                .stream()
                .flatMap(type -> type.getMethods().stream())
                .filter(method -> "global.available-ip".equals(method.getMethodCode()))
                .findFirst()
                .orElseThrow()
                .getFormSchema();
        }
        Map<String, Object> frozen = Map
            .of("action_type", "global_variable_available_ip", "variable_name", "new_ip", "ip_prefix", "{{new_ip_3}}", "start", 1, "end", 254, "diagnostic_fields", diagnosticFields);

        Map<String, Object> result = resolver.resolve(frozen, Map.of("new_ip_3", "192.168.1"));

        assertThat(resolver.references(frozen)).containsExactly("new_ip_3");
        assertThat(result).containsEntry("ip_prefix", "192.168.1").containsEntry("variable_name", "new_ip");
        assertThat(result.get("diagnostic_fields")).isEqualTo(diagnosticFields).isNotSameAs(diagnosticFields);
        assertThat(frozen.get("ip_prefix")).isEqualTo("{{new_ip_3}}");
        assertThatThrownBy(() -> resolver.resolve(frozen, Map.of())).hasMessageContaining("缺少运行时变量：new_ip_3");
    }

    @Test
    void shouldPreserveMetadataEvenWhenInputReferencesTheSameVariable() {
        Map<String, Object> frozen = Map
            .of("action_type", "host_command", "command", "echo ${user}", "description", "command for {{user}}", "source", "${recording_source}", "diagnostic_fields", List
                .of(Map.of("name", "command", "help", "echo ${user}")));

        Map<String, Object> result = resolver.resolve(frozen, Map.of("user", "alice"));

        assertThat(resolver.references(frozen)).containsExactly("user");
        assertThat(result).containsEntry("command", "echo alice")
            .containsEntry("description", "command for {{user}}")
            .containsEntry("source", "${recording_source}")
            .containsEntry("diagnostic_fields", frozen.get("diagnostic_fields"));
    }

    @Test
    void shouldRejectBindingsReferencedOnlyInMetadata() {
        Map<String, Object> frozen = Map
            .of("action_type", "host_command", "command", "echo ok", "diagnostic_fields", List.of(Map
                .of("help", "${example}")));

        assertThat(resolver.references(frozen)).isEmpty();
        assertThat(resolver.resolve(frozen, Map.of())).isEqualTo(frozen);
        assertThatThrownBy(() -> resolver.resolve(frozen, Map.of("example", "injected"))).hasMessageContaining("未引用");
    }

    @Test
    void shouldResolveMetadataNamedFieldsInsideExecutionParameters() {
        Map<String, Object> frozen = Map.of("action_type", "host_command", "parameters", List.of(Map
            .of("description", "${user}", "diagnostic_fields", Map.of("help", "{{count}}"))));

        Map<String, Object> result = resolver.resolve(frozen, Map.of("user", "alice", "count", 3));

        assertThat(resolver.references(frozen)).containsExactlyInAnyOrder("user", "count");
        assertThat(result.get("parameters")).isEqualTo(List.of(Map.of("description", "alice", "diagnostic_fields", Map
            .of("help", 3))));
    }

    @Test
    void shouldRejectRoutingVariables() {
        assertThatThrownBy(() -> resolver.rejectVariablesInRoutingFields(Map.of("action_type", "${action}")))
            .hasMessageContaining("action_type 不允许");
        assertThatThrownBy(() -> resolver.rejectVariablesInRoutingFields(Map
            .of("action_type", "host_command", "target_ref", Map.of("config_id", "${target}"))))
            .hasMessageContaining("target_ref 不允许");
    }
}
