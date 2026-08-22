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

package top.continew.admin.controller.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AutomationUiDefinitionProjectionLiquibaseContractTest {

    @Test
    void masterShouldIncludeAdditiveProjectionSchema() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String recordSourceSql = resource("db/changelog/mysql/automation_ui_query_read_performance.sql");
        String projectionSql = resource("db/changelog/mysql/automation_ui_definition_projection.sql");

        assertThat(master)
            .contains("db/changelog/mysql/automation_ui_query_read_performance.sql", "db/changelog/mysql/automation_ui_definition_projection.sql");
        assertThat(recordSourceSql).contains("record_source", "varchar(16) DEFAULT NULL")
            .doesNotContain("record_source` varchar(16) NOT NULL", "definition_size_bytes", "automation_ui_scene_definition_read_state", "-- rollback ALTER TABLE `automation_ui_execution` DROP");
        assertThat(projectionSql)
            .contains("definition_size_bytes", "definition_step_count", "automation_ui_scene_definition_read_state", "automation_ui_scene_definition_case_read", "automation_ui_scene_definition_step_read", "published_projection_id", "building_projection_id", "build_token", "lease_until", "node_sha256")
            .contains("uk_automation_ui_definition_case_id", "uk_automation_ui_definition_step_id")
            .doesNotContain("record_source", "-- rollback DROP TABLE", "-- rollback ALTER TABLE `automation_ui_scene` DROP");
        assertThat(count(projectionSql, "precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns"))
            .isEqualTo(2);
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String source, String needle) {
        int result = 0;
        int from = 0;
        while ((from = source.indexOf(needle, from)) >= 0) {
            result++;
            from += needle.length();
        }
        return result;
    }
}
