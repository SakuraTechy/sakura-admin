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

package top.continew.admin.automation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.mapping.BoundSql;
import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.query.AutomationUiExecutionCaseHistoryQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AutomationUiExecutionQueryMapperXmlTest {

    @Test
    void mapperShouldParseAllLayeredReadStatements() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/AutomationUiExecutionQueryMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/AutomationUiExecutionQueryMapper.xml", configuration
                .getSqlFragments()).parse();
        }

        assertThat(configuration.getMappedStatementNames())
            .contains("top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper.selectExecutionPageCount", "top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper.selectExecutionCursor", "top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper.selectCaseHistoryPageCount", "top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper.selectCaseHistoryPage", "top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper.selectArtifactPage");
    }

    @Test
    void historyAndChildPagesShouldNeverSelectLargeBodies() throws IOException {
        String xml = mapperXml();
        String summaryColumns = between(xml, "<sql id=\"executionSummaryColumns\">", "</sql>");
        String caseColumns = between(xml, "<sql id=\"caseColumns\">", "</sql>");
        String stepPage = between(xml, "<select id=\"selectStepPage\"", "</select>");
        String artifacts = between(xml, "<select id=\"selectArtifactPage\"", "</select>");

        assertThat(xml).doesNotContain("SELECT *");
        assertThat(summaryColumns).doesNotContain("summary_json", "execution_config", "diagnostics", "case_list");
        assertThat(caseColumns).doesNotContain("summary_json", "steps", "diagnostics");
        assertThat(stepPage).doesNotContain("diagnostics", "locator_value");
        assertThat(artifacts).doesNotContain("file_id", "sha256", "relative_path", "url");
    }

    @Test
    void scopedLatestShouldPreferStoredSourceAndKeepNullRowCompatibility() throws IOException {
        String xml = mapperXml();
        String scope = between(xml, "<sql id=\"executionScopePredicate\">", "</sql>");
        String sourceColumn = between(xml, "<sql id=\"recordSourceColumn\">", "</sql>");
        String latest = between(xml, "<select id=\"selectScopedLatest\"", "</select>");
        String batch = between(xml, "<select id=\"selectScopedLatestBatch\"", "</select>");

        assertThat(scope).contains("internal-interactive-context", "interactive-execution-context")
            .contains("e.record_source = 'debug'", "e.record_source = 'test'", "e.record_source IS NULL", "test_plan_id IS NOT NULL", "test_report_id IS NOT NULL")
            .contains("e.record_type IS NULL", "LOWER(TRIM(e.record_type)) NOT IN", "e.test_plan_id IS NULL", "e.test_report_id IS NULL", "e.trigger_type IS NULL OR LOWER(TRIM(e.trigger_type)) NOT IN", "LOWER(TRIM(e.trigger_type)) IN")
            .doesNotContain("build_number IS NOT NULL");
        assertThat(sourceColumn)
            .contains("COALESCE(e.record_source, CASE", "LOWER(TRIM(e.record_type))", "LOWER(TRIM(e.trigger_type))");
        assertThat(latest).contains("ORDER BY e.create_time DESC, e.id DESC");
        assertThat(batch).contains("PARTITION BY candidate.scene_id")
            .contains("candidate.record_source = 'debug'", "candidate.record_source = 'test'", "candidate.record_source IS NULL", "candidate.record_type IS NULL", "LOWER(TRIM(candidate.record_type))", "candidate.trigger_type IS NULL", "LOWER(TRIM(candidate.trigger_type))", "ORDER BY candidate.create_time DESC, candidate.id DESC");
    }

    @Test
    void everyResourcePathShouldReuseSceneObjectScope() throws IOException {
        String xml = mapperXml();

        for (String statement : new String[] {"selectExecutionCursor", "selectExecutionDetail", "selectCasePageCount",
            "selectCasePage", "selectCaseHistoryPageCount", "selectCaseHistoryPage", "selectStepPageCount",
            "selectStepPage", "selectStepDetail", "selectArtifactPageCount", "selectArtifactPage"}) {
            assertThat(between(xml, "<select id=\"" + statement + "\"", "</select>")).as(statement)
                .contains("authorizedExecutionJoins", "executionAccessPredicate");
        }
    }

    @Test
    void parentAccessAndCountShouldBeCombinedForTwoQueryPaging() throws IOException {
        String xml = mapperXml();

        for (String statement : new String[] {"selectExecutionPageCount", "selectCasePageCount", "selectStepPageCount",
            "selectArtifactPageCount"}) {
            String count = between(xml, "<select id=\"" + statement + "\"", "</select>");
            assertThat(count).as(statement).contains("AS total", "executionAccessPredicate");
        }
        assertThat(xml)
            .doesNotContain("id=\"countExecutions\"", "id=\"countCases\"", "id=\"countSteps\"", "id=\"countArtifacts\"");
        assertThat(xml).contains("OR s.create_user = #{userId}", "JSON_CONTAINS(COALESCE(p.member, JSON_ARRAY())");
    }

    @Test
    void cursorPageShouldUseCreateTimeAndIdKeysetWithoutOffset() throws IOException {
        String cursor = between(mapperXml(), "<select id=\"selectExecutionCursor\"", "</select>");

        assertThat(cursor)
            .contains("e.create_time &gt; #{cursorTime}", "e.id &gt; #{cursorId}", "e.create_time &lt; #{cursorTime}", "e.id &lt; #{cursorId}", "LIMIT #{limit}")
            .doesNotContain("OFFSET", "COUNT(");
    }

    @Test
    void latestSelectionShouldFilterEngineBeforePagingAndKeepScopeAndAuthorization() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/AutomationUiExecutionQueryMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/AutomationUiExecutionQueryMapper.xml", configuration
                .getSqlFragments()).parse();
        }
        AutomationUiExecutionScopeReq scope = new AutomationUiExecutionScopeReq();
        scope.setRecordSource("test");
        scope.setTestPlanId(7L);
        scope.setTestReportId(6L);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("scope", scope);
        parameters.put("admin", false);
        parameters.put("userId", 9L);
        parameters.put("offset", 0L);
        parameters.put("limit", 1);
        parameters.put("ascending", false);
        parameters.put("cursorTime", null);
        parameters.put("cursorId", null);
        for (String statement : new String[] {"selectCaseHistoryPageCount", "selectCaseHistoryPage",
            "selectExecutionPageCount", "selectExecutionPage", "selectExecutionCursor"}) {
            for (String engine : new String[] {"playwright-runner", "extension-cdp", "jenkins", null}) {
                if (statement.startsWith("selectCaseHistory")) {
                    AutomationUiExecutionCaseHistoryQuery query = new AutomationUiExecutionCaseHistoryQuery();
                    query.setSceneDbId(8L);
                    query.setCaseId("case-001");
                    query.setExecutionEngine(engine);
                    parameters.put("query", query);
                } else {
                    AutomationUiExecutionQuery query = new AutomationUiExecutionQuery();
                    query.setSceneDbId(8L);
                    query.setExecutionEngine(engine);
                    parameters.put("query", query);
                }
                BoundSql bound = configuration
                    .getMappedStatement("top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper." + statement)
                    .getBoundSql(parameters);
                assertThat(bound.getSql()).as(statement)
                    .contains("e.test_plan_id = ?", "e.test_report_id = ?", "JSON_CONTAINS");
                if (engine == null) {
                    // 不传引擎时保持现有 Jenkins 和跨引擎历史列表的查询行为。
                    assertThat(bound.getSql()).doesNotContain("LOWER(e.execution_engine)");
                } else {
                    assertThat(bound.getSql())
                        .contains("LOWER(e.execution_engine) LIKE '%playwright%'", "LOWER(e.execution_engine) LIKE '%cdp%'", "LOWER(e.execution_engine) LIKE '%extension%'", "ELSE 'jenkins'");
                    assertThat(bound.getParameterMappings()).extracting("property").contains("query.executionEngine");
                    if (!statement.endsWith("Count")) {
                        assertThat(bound.getSql().indexOf("LOWER(e.execution_engine)")).isLessThan(bound.getSql()
                            .indexOf("ORDER BY"));
                    }
                }
            }
        }
    }

    private String mapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/AutomationUiExecutionQueryMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
