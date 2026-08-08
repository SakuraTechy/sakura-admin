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

package top.continew.admin.automation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.support.AutomationInfrastructureResultSanitizer;

/**
 * 基础设施结果预览的受限映射测试：只保留结构化摘要，不允许命令、SQL、凭据或完整大结果进入任务表。
 */
class AutomationInfrastructureTaskResultPreviewTest {

    @Test
    void shouldPreserveV2MetadataOrderedRowsAndOutParameters() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureResultSanitizer sanitizer = new AutomationInfrastructureResultSanitizer(objectMapper);
        Map<String, Object> raw = Map
            .of("schemaVersion", 2, "kind", "DATABASE_CALL", "durationMs", 18L, "truncated", false, "stdout", "token=secret-value", "results", List
                .of(Map.of("type", "ROW_SET", "columns", List.of(Map
                    .of("name", "id", "label", "duplicate", "jdbcType", -5, "typeName", "BIGINT", "nullable", false), Map
                        .of("name", "name", "label", "duplicate", "jdbcType", 12, "typeName", "VARCHAR", "nullable", true)), "rows", List
                            .of(List.of(1L, "admin")), "rowCount", 1L, "truncated", false), Map
                                .of("type", "UPDATE_COUNT", "affectedRows", 3L), Map
                                    .of("type", "OUT_PARAMETERS", "parameters", List.of(Map
                                        .of("name", "status", "position", 1, "jdbcType", 12, "typeName", "VARCHAR", "value", "OK")))));

        Map<String, Object> safe = sanitizer.sanitize(raw);
        String json = sanitizer.serializePreview(safe);
        Map<?, ?> decoded = objectMapper.readValue(json, Map.class);

        assertThat(decoded.get("schemaVersion")).isEqualTo(2);
        assertThat(json).contains("DATABASE_CALL", "duplicate", "OUT_PARAMETERS", "status", "admin");
        assertThat(json).doesNotContain("secret-value", "resultSets");
    }

    @Test
    void shouldKeepV1ReadOnlyProjection() {
        AutomationInfrastructureResultSanitizer sanitizer = new AutomationInfrastructureResultSanitizer(new ObjectMapper());
        Map<String, Object> safe = sanitizer.sanitize(Map.of("kind", "DATABASE_QUERY", "resultSets", List.of(Map
            .of("columns", List.of("id"), "rows", List.of(Map.of("id", 1))))));

        assertThat(safe).containsEntry("schemaVersion", 1);
        assertThat(safe).containsKey("resultSets");
        assertThat(safe).doesNotContainKey("results");
    }

    @Test
    void oversizedPreviewShouldRemainValidJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationInfrastructureResultSanitizer sanitizer = new AutomationInfrastructureResultSanitizer(objectMapper);
        Map<String, Object> safe = sanitizer.sanitize(Map.of("schemaVersion", 2, "kind", "SERVER_COMMAND", "stdout", "x"
            .repeat(100_000), "results", List.of(), "warnings", List.of(), "truncated", true));

        String json = sanitizer.serializePreview(safe);

        assertThat(json.length()).isLessThanOrEqualTo(64 * 1024);
        assertThat(objectMapper.readTree(json).path("truncated").asBoolean()).isTrue();
    }

    @Test
    void unsupportedSchemaShouldKeepStableErrorCode() {
        AutomationInfrastructureResultSanitizer sanitizer = new AutomationInfrastructureResultSanitizer(new ObjectMapper());

        Map<String, Object> safe = sanitizer.sanitize(Map.of("schemaVersion", 3, "kind", "DATABASE_QUERY"));

        assertThat(safe.get("schemaVersion")).isEqualTo(3);
        assertThat(safe.get("warnings").toString()).contains("RESULT_SCHEMA_UNSUPPORTED");
    }
}
