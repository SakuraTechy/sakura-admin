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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper;
import top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.query.AutomationUiSceneDefinitionRow;
import top.continew.admin.automation.model.query.AutomationUiSceneInlineDefinitionRow;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.query.AutomationUiDefinitionProjectionStateRow;
import top.continew.admin.automation.model.query.AutomationUiDefinitionCaseReadRow;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDefinitionResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionSummaryResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneGlobalRevisionResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneSummaryResp;
import top.continew.admin.automation.support.AutomationUiDefinitionDisplayMasker;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver.AccessScope;
import top.continew.admin.automation.support.AutomationUiDefinitionProjectionUnavailableException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.extension.crud.model.query.PageQuery;

@ExtendWith(MockitoExtension.class)
class AutomationUiSceneQueryServiceImplTest {

    @Mock
    private AutomationUiSceneQueryMapper queryMapper;
    @Mock
    private AutomationUiExecutionQueryMapper executionQueryMapper;
    @Mock
    private AutomationUiSceneAccessScopeResolver accessScopeResolver;

    private AutomationUiSceneQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationUiSceneQueryServiceImpl(queryMapper, executionQueryMapper, accessScopeResolver, new AutomationUiDefinitionDisplayMasker());
        ReflectionTestUtils.setField(service, "inlineMaxBytes", 1_048_576L);
        ReflectionTestUtils.setField(service, "inlineMaxSteps", 1_000L);
        ReflectionTestUtils.setField(service, "maskPolicyVersion", 3L);
    }

    @Test
    void summariesShouldDedupeIdsAndUseCurrentObjectScope() {
        stubScope();
        AutomationUiSceneSummaryResp summary = new AutomationUiSceneSummaryResp();
        summary.setSceneDbId(3L);
        when(queryMapper.selectSummaries(any(), eq(9L), eq(false))).thenReturn(List.of(summary));

        List<AutomationUiSceneSummaryResp> result = service.summaries(List.of(3L, 3L, 5L));

        ArgumentCaptor<LinkedHashSet<Long>> ids = ArgumentCaptor.forClass(LinkedHashSet.class);
        verify(queryMapper).selectSummaries(ids.capture(), eq(9L), eq(false));
        assertThat(ids.getValue()).containsExactly(3L, 5L);
        assertThat(result).containsExactly(summary);
    }

    @Test
    void summariesShouldRejectMoreThanOneHundredDistinctIds() {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> service.summaries(ids)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("SCENE_ID_LIMIT_EXCEEDED");
        verify(queryMapper, never()).selectSummaries(any(), anyLong(), anyBoolean());
    }

    @Test
    void revisionsShouldDedupeIdsAndUseCurrentObjectScope() {
        stubScope();
        AutomationUiSceneGlobalRevisionResp revision = new AutomationUiSceneGlobalRevisionResp();
        revision.setSceneDbId(3L);
        when(queryMapper.selectRevisions(any(), eq(9L), eq(false))).thenReturn(List.of(revision));

        List<AutomationUiSceneGlobalRevisionResp> result = service.revisions(List.of(3L, 3L, 5L));

        ArgumentCaptor<LinkedHashSet<Long>> ids = ArgumentCaptor.forClass(LinkedHashSet.class);
        verify(queryMapper).selectRevisions(ids.capture(), eq(9L), eq(false));
        assertThat(ids.getValue()).containsExactly(3L, 5L);
        assertThat(result).containsExactly(revision);
    }

    @Test
    void summariesShouldAttachLatestOnlyFromExplicitScope() {
        stubScope();
        AutomationUiSceneSummaryResp summary = new AutomationUiSceneSummaryResp();
        summary.setSceneDbId(3L);
        AutomationUiExecutionSummaryResp latest = new AutomationUiExecutionSummaryResp();
        latest.setSceneDbId(3L);
        latest.setExecutionDbId(99L);
        when(queryMapper.selectSummaries(any(), eq(9L), eq(false))).thenReturn(List.of(summary));
        when(executionQueryMapper.selectScopedLatestBatch(any(), any(), eq(9L), eq(false))).thenReturn(List.of(latest));
        AutomationUiExecutionScopeReq scope = new AutomationUiExecutionScopeReq();
        scope.setRecordSource("debug");

        List<AutomationUiSceneSummaryResp> result = service.summaries(List.of(3L), scope);

        assertThat(result).singleElement()
            .extracting(AutomationUiSceneSummaryResp::getLatestExecution)
            .isSameAs(latest);
    }

    @Test
    void latestShouldHideUnauthorizedSceneBeforeExecutionQuery() {
        stubScope();
        when(executionQueryMapper.selectSceneAccess(3L, 9L, false)).thenReturn(null);
        AutomationUiExecutionScopeReq scope = new AutomationUiExecutionScopeReq();
        scope.setRecordSource("debug");

        assertThatThrownBy(() -> service.latestExecution(3L, scope)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("NOT_FOUND_OR_ACCESS_DENIED");
        verify(executionQueryMapper, never()).selectScopedLatest(anyLong(), any(), anyLong(), anyBoolean());
    }

    @Test
    void definitionShouldNotReadBodyWhenProjectionThresholdIsReached() {
        stubScope();
        AutomationUiSceneDefinitionRow metadata = metadata();
        metadata.setDefinitionBytes(1_048_576L);
        when(queryMapper.selectDefinitionMetadata(8L, 9L, false)).thenReturn(metadata);

        assertThatThrownBy(() -> service.definition(8L))
            .isInstanceOf(AutomationUiDefinitionProjectionUnavailableException.class)
            .hasMessageContaining("PENDING");
        verify(queryMapper, never()).selectInlineDefinition(anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void definitionShouldReturnStrictProjectedBranchOnlyAfterReadyPublish() {
        stubScope();
        AutomationUiSceneDefinitionRow metadata = metadata();
        metadata.setDefinitionBytes(1_048_576L);
        AutomationUiDefinitionProjectionStateRow state = readyState();
        when(queryMapper.selectDefinitionMetadata(8L, 9L, false)).thenReturn(metadata);
        when(queryMapper.selectProjectionState(8L, 9L, false)).thenReturn(state);

        var view = service.definition(8L);

        assertThat(view.body()).isInstanceOf(AutomationUiSceneDefinitionResp.Projected.class);
        AutomationUiSceneDefinitionResp.Projected body = (AutomationUiSceneDefinitionResp.Projected)view.body();
        assertThat(body.getProjectionId()).isEqualTo(88L);
        assertThat(body.getCaseCount()).isEqualTo(1);
        assertThat(body.getStepCount()).isEqualTo(2);
        assertThat(view.etag()).contains("projection-88").contains("source-source-hash");
        verify(queryMapper, never()).selectInlineDefinition(anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void definitionCaseShouldKeepExactNodeIdAndNeverExposeStepList() {
        stubScope();
        AutomationUiSceneDefinitionRow metadata = metadata();
        metadata.setDefinitionBytes(1_048_576L);
        when(queryMapper.selectDefinitionMetadata(8L, 9L, false)).thenReturn(metadata);
        when(queryMapper.selectProjectionState(8L, 9L, false)).thenReturn(readyState());
        AutomationUiDefinitionCaseReadRow row = new AutomationUiDefinitionCaseReadRow();
        row.setCaseId(" Case-A ");
        row.setStepCount(2);
        row.setCaseJson("{\"id\":\" Case-A \",\"name\":\"demo\"}");
        row.setNodeSha256(sha256(row.getCaseJson()));
        when(queryMapper.selectProjectedCase(8L, 88L, 4L, " Case-A ", 9L, false)).thenReturn(row);

        var view = service.definitionCase(8L, " Case-A ");

        assertThat(view.body().getCaseId()).isEqualTo(" Case-A ");
        assertThat(view.body().getCaseBody().has("stepList")).isFalse();
        verify(queryMapper).selectProjectedCase(8L, 88L, 4L, " Case-A ", 9L, false);
    }

    @Test
    void definitionCasesShouldSearchProjectedMetadataWithoutSkippingCompletenessCheck() {
        stubScope();
        AutomationUiSceneDefinitionRow metadata = metadata();
        metadata.setDefinitionBytes(1_048_576L);
        when(queryMapper.selectDefinitionMetadata(8L, 9L, false)).thenReturn(metadata);
        when(queryMapper.selectProjectionState(8L, 9L, false)).thenReturn(readyState());
        when(queryMapper.countProjectedCases(8L, 88L, 4L, null, 9L, false)).thenReturn(1L);
        when(queryMapper.countProjectedCases(8L, 88L, 4L, "demo\\%\\_", 9L, false)).thenReturn(1L);
        AutomationUiDefinitionCaseReadRow row = new AutomationUiDefinitionCaseReadRow();
        row.setCaseId("case-1");
        row.setCaseName("demo%_");
        row.setCaseJson("{\"id\":\"case-1\",\"name\":\"demo%_\"}");
        row.setNodeSha256(sha256(row.getCaseJson()));
        when(queryMapper.selectProjectedCases(8L, 88L, 4L, "demo\\%\\_", 9L, false, 0L, 50)).thenReturn(List.of(row));

        var view = service.definitionCases(8L, 1, 50, " demo%_ ");

        assertThat(view.body().getTotal()).isEqualTo(1L);
        assertThat(view.body().getItems()).singleElement().extracting(item -> item.getCaseId()).isEqualTo("case-1");
        assertThat(view.etag()).doesNotContain("demo").matches("W/\\\".*node-[0-9a-f]{64}-representation-.*\\\"");
        verify(queryMapper).countProjectedCases(8L, 88L, 4L, null, 9L, false);
    }

    @Test
    void definitionShouldUseVersionGuardAndBuildScopeBoundEtag() {
        stubScope();
        AutomationUiSceneDefinitionRow metadata = metadata();
        AutomationUiSceneInlineDefinitionRow inlineRow = new AutomationUiSceneInlineDefinitionRow();
        inlineRow.setCaseList(List.of(new CaseDO()));
        when(queryMapper.selectDefinitionMetadata(8L, 9L, false)).thenReturn(metadata);
        when(queryMapper.selectInlineDefinition(8L, 4L, 9L, false)).thenReturn(inlineRow);

        var view = service.definition(8L);

        assertThat(view.body()).isInstanceOf(AutomationUiSceneDefinitionResp.Inline.class);
        AutomationUiSceneDefinitionResp.Inline body = (AutomationUiSceneDefinitionResp.Inline)view.body();
        assertThat(body.getDefinitionVersion()).isEqualTo(4L);
        assertThat(body.getMaskPolicyVersion()).isEqualTo(3L);
        assertThat(body.getRepresentationScopeDigest()).matches("[0-9a-f]{64}");
        assertThat(view.etag()).isEqualTo("W/\"scene-8-definition-4-mode-inline-mask-3-scope-" + body
            .getRepresentationScopeDigest() + "\"");
        verify(queryMapper).selectInlineDefinition(8L, 4L, 9L, false);
    }

    @Test
    void pageShouldRejectUnsafeBoundsBeforeQueryingDatabase() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(201);
        pageQuery.setSize(50);

        assertThatThrownBy(() -> service.page(null, pageQuery)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("OFFSET_LIMIT_EXCEEDED");
        verify(queryMapper, never()).countSummaries(any(), anyLong(), anyBoolean());
    }

    @Test
    void scopedPageShouldFilterBeforePagingAndAttachLatestInOneBatch() {
        stubScope();
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setExecuteStatus("RUNNING");
        query.setExecuteResult("FAILED");
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(20);
        AutomationUiExecutionScopeReq executionScope = new AutomationUiExecutionScopeReq();
        executionScope.setRecordSource("DEBUG");
        AutomationUiSceneSummaryResp summary = new AutomationUiSceneSummaryResp();
        summary.setSceneDbId(8L);
        AutomationUiExecutionSummaryResp latest = new AutomationUiExecutionSummaryResp();
        latest.setSceneDbId(8L);
        latest.setExecutionDbId(81L);
        when(queryMapper.countScopedSummaries(any(), any(), eq(9L), eq(false))).thenReturn(1L);
        when(queryMapper
            .selectScopedSummaryPage(any(), any(), eq(9L), eq(false), eq(0L), eq(20), eq("createTime"), eq(false)))
            .thenReturn(List.of(summary));
        when(executionQueryMapper.selectScopedLatestBatch(any(), any(), eq(9L), eq(false))).thenReturn(List.of(latest));

        var result = service.page(query, pageQuery, executionScope);

        assertThat(query.getExecuteStatus()).isEqualTo("11");
        assertThat(query.getExecuteResult()).isEqualTo("15");
        assertThat(result.getList()).singleElement()
            .extracting(AutomationUiSceneSummaryResp::getLatestExecution)
            .isSameAs(latest);
        verify(queryMapper, never()).countSummaries(any(), anyLong(), anyBoolean());
        verify(executionQueryMapper).selectScopedLatestBatch(any(), any(), eq(9L), eq(false));
    }

    @Test
    void pageShouldRejectExecutionFilterWithoutExplicitScope() {
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setExecuteStatus("11");
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(20);

        assertThatThrownBy(() -> service.page(query, pageQuery)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("EXECUTION_SCOPE_REQUIRED");
        verify(queryMapper, never()).countSummaries(any(), anyLong(), anyBoolean());
        verify(queryMapper, never()).countScopedSummaries(any(), any(), anyLong(), anyBoolean());
    }

    @Test
    void pageShouldRejectMatchedOnlyWithoutExplicitScope() {
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setExecutionMatchedOnly(true);
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(20);

        assertThatThrownBy(() -> service.page(query, pageQuery)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("EXECUTION_SCOPE_REQUIRED");
        verify(queryMapper, never()).countSummaries(any(), anyLong(), anyBoolean());
    }

    @Test
    void scopedPageShouldRejectBlankRecordSourceInsteadOfFallingBackToGlobalSummary() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(20);
        AutomationUiExecutionScopeReq executionScope = new AutomationUiExecutionScopeReq();
        executionScope.setTestPlanId(3L);

        assertThatThrownBy(() -> service.page(new AutomationUiSceneQuery(), pageQuery, executionScope))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("EXECUTION_SCOPE_REQUIRED");
        verify(queryMapper, never()).countSummaries(any(), anyLong(), anyBoolean());
        verify(queryMapper, never()).countScopedSummaries(any(), any(), anyLong(), anyBoolean());
    }

    private AutomationUiSceneDefinitionRow metadata() {
        AutomationUiSceneDefinitionRow row = new AutomationUiSceneDefinitionRow();
        row.setSceneDbId(8L);
        row.setSceneKey("scene-8");
        row.setProjectDbId(11L);
        row.setVersionDbId(12L);
        row.setDefinitionVersion(4L);
        row.setDefinitionBytes(100L);
        row.setDefinitionStepCount(2L);
        return row;
    }

    private AutomationUiDefinitionProjectionStateRow readyState() {
        AutomationUiDefinitionProjectionStateRow state = new AutomationUiDefinitionProjectionStateRow();
        state.setDefinitionVersion(4L);
        state.setPublishedProjectionId(88L);
        state.setProjectionId(88L);
        state.setStatus("ready");
        state.setSourceSha256("source-hash");
        state.setCaseCount(1);
        state.setStepCount(2);
        return state;
    }

    private void stubScope() {
        when(accessScopeResolver.currentScope()).thenReturn(new AccessScope(9L, false, Set.of("scene:get"), Set
            .of("developer")));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
