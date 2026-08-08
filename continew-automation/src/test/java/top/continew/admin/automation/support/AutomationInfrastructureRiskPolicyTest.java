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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;

class AutomationInfrastructureRiskPolicyTest {

    @Test
    void queryModeShouldUseLexicalClassificationInsteadOfKeywordRegex() {
        AutomationInfrastructureRiskPolicy policy = new AutomationInfrastructureRiskPolicy("");

        AutomationInfrastructureRiskPolicy.Assessment assessment = policy.assess("database_sql", Map
            .of("sql_mode", "query", "sql", "SELECT 'delete from users' AS message /* DROP TABLE ignored */"));

        assertThat(assessment.riskLevel()).isEqualTo("read");
        assertThat(assessment.readOnlyTransaction()).isTrue();
        assertThatThrownBy(() -> policy.assess("database_sql", Map
            .of("sql_mode", "query", "sql", "SELECT 1; DELETE FROM users")))
            .hasMessageContaining("INFRA_SQL_QUERY_MODE_VIOLATION");
        assertThatThrownBy(() -> policy.assess("database_sql", Map
            .of("sql_mode", "query", "sql", "WITH changed AS (UPDATE users SET enabled = 0 RETURNING id) SELECT * FROM changed")))
            .hasMessageContaining("INFRA_SQL_QUERY_MODE_VIOLATION");
    }

    @Test
    void freeCommandShouldRemainPrivilegedAndConfiguredTemplateMustMatchDigest() {
        String command = "systemctl status sakura-agent";
        AutomationInfrastructureRiskPolicy policy = new AutomationInfrastructureRiskPolicy("agent-health:" + DigestUtil
            .sha256Hex(command));

        assertThat(policy.assess("server_command", Map.of("command", command)).riskLevel())
            .isEqualTo("host-privileged");
        AutomationInfrastructureRiskPolicy.Assessment template = policy.assess("server_command", Map
            .of("command", command, "command_template_id", "agent-health"));
        assertThat(template.riskLevel()).isEqualTo("write");
        assertThat(template.commandTemplateId()).isEqualTo("agent-health");
        assertThatThrownBy(() -> policy.assess("server_command", Map
            .of("command", "systemctl restart sakura-agent", "command_template_id", "agent-health")))
            .hasMessageContaining("INFRA_COMMAND_TEMPLATE_MISMATCH");
    }
}
