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

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AutomationUiSceneQueryMapperXmlTest {

    @Test
    void mapperShouldParseSummaryRevisionAndDefinitionStatements() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/AutomationUiSceneQueryMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/AutomationUiSceneQueryMapper.xml", configuration
                .getSqlFragments()).parse();
        }

        assertThat(configuration.getMappedStatementNames())
            .contains("top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectAuthorizedProjectId", "top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectAuthorizedSceneDbIdByKey", "top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectSummaryPage", "top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectRevisions", "top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectInlineDefinition", "top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectProjectedCases", "top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper.selectProjectedSteps");
    }

    @Test
    void operationCatalogSceneLookupShouldReadOnlyAuthorizedProjectId() throws IOException {
        String lookup = between(mapperXml(), "<select id=\"selectAuthorizedProjectId\"", "</select>");

        assertThat(lookup).contains("SELECT s.project_id", "authorizedSceneJoins", "sceneAccessPredicate")
            .doesNotContain("case_list", "debug_record", "test_record", "SELECT *");
    }

    @Test
    void legacyArtifactScopeLookupShouldResolveOnlyAuthorizedSceneId() throws IOException {
        String lookup = between(mapperXml(), "<select id=\"selectAuthorizedSceneDbIdByKey\"", "</select>");

        assertThat(lookup).contains("SELECT s.id", "s.scene_id = #{sceneKey}", "sceneAccessPredicate")
            .doesNotContain("case_list", "debug_record", "test_record", "SELECT *");
    }

    @Test
    void summaryQueriesShouldUseNarrowColumnsAndObjectScope() throws IOException {
        String xml = mapperXml();
        String columns = between(xml, "<sql id=\"summaryColumns\">", "</sql>");
        String summaryPage = between(xml, "<select id=\"selectSummaryPage\"", "</select>");
        String scopedJoin = between(xml, "<sql id=\"scopedLatestExecutionJoin\">", "</sql>");
        String scopedFilter = between(xml, "<sql id=\"scopedLatestExecutionFilter\">", "</sql>");
        String scopedPage = between(xml, "<select id=\"selectScopedSummaryPage\"", "</select>");

        assertThat(columns).contains("global_execution_revision", "create_user_string", "update_user_string")
            .doesNotContain("case_list", "debug_record", "test_record", "summary_json", "diagnostics");
        assertThat(summaryPage).contains("automation_ui_scene_execution_state", "LEFT JOIN sys_user")
            .doesNotContain("SELECT *");
        assertThat(scopedJoin)
            .contains("candidate.scene_id = s.id", "candidate.record_source", "ORDER BY candidate.create_time DESC, candidate.id DESC", "LIMIT 1")
            .contains("candidate.record_type IS NULL", "LOWER(TRIM(candidate.record_type))", "candidate.test_plan_id IS NULL", "candidate.test_report_id IS NULL", "candidate.trigger_type IS NULL", "LOWER(TRIM(candidate.trigger_type))")
            .doesNotContain("case_list", "debug_record", "test_record", "summary_json");
        assertThat(scopedFilter).contains("query.executionMatchedOnly == true", "scoped_latest.id IS NOT NULL");
        assertThat(scopedPage).contains("scopedLatestExecutionJoin", "scopedLatestExecutionFilter")
            .doesNotContain("SELECT *", "case_list", "debug_record", "test_record", "summary_json");
        assertThat(xml).contains("OR s.create_user = #{userId}", "JSON_CONTAINS(COALESCE(p.member, JSON_ARRAY())")
            .contains("pv.project_id = s.project_id")
            .contains("pm.version_id = s.version_id")
            .contains("query.moduleIds", "s.module_id IN");
    }

    @Test
    void definitionShouldMeasureBeforeReadingBodyAndGuardVersion() throws IOException {
        String xml = mapperXml();
        String metadata = between(xml, "<select id=\"selectDefinitionMetadata\"", "</select>");
        String inline = between(xml, "<select id=\"selectInlineDefinition\"", "</select>");

        assertThat(metadata).contains("s.definition_size_bytes", "s.definition_step_count")
            .doesNotContain("SELECT s.case_list", "OCTET_LENGTH", "JSON_TABLE", "JSON_EXTRACT");
        assertThat(inline).contains("SELECT s.case_list", "definition_version", "sceneAccessPredicate")
            .doesNotContain("SELECT *", "debug_record", "test_record");
    }

    @Test
    void projectedNodesShouldBindCurrentReadyPublishedProjectionAndUseStableOrder() throws IOException {
        String xml = mapperXml();
        String cases = between(xml, "<select id=\"selectProjectedCases\"", "</select>");
        String steps = between(xml, "<select id=\"selectProjectedSteps\"", "</select>");

        assertThat(xml)
            .contains("rs.status = 'ready'", "rs.published_projection_id = #{projectionId}", "sceneAccessPredicate");
        assertThat(cases).contains("c.projection_id = rs.published_projection_id", "ORDER BY c.case_index, c.id")
            .contains("c.case_id LIKE", "c.case_name LIKE", "ESCAPE")
            .doesNotContain("case_list", "SELECT *");
        assertThat(steps).contains("st.projection_id = rs.published_projection_id", "ORDER BY st.step_index, st.id")
            .doesNotContain("case_list", "SELECT *");
    }

    @Test
    void revisionsShouldReadOnlyGlobalStateAndApplyAccessScope() throws IOException {
        String revisions = between(mapperXml(), "<select id=\"selectRevisions\"", "</select>");

        assertThat(revisions)
            .contains("global_execution_revision", "automation_ui_scene_execution_state", "sceneAccessPredicate")
            .doesNotContain("latest_execution_id", "execute_status", "execute_result", "case_list", "SELECT *");
    }

    private String mapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/AutomationUiSceneQueryMapper.xml")) {
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
