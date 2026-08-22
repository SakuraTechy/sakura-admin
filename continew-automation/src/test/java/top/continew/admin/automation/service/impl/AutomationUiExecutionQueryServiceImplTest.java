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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper;
import top.continew.admin.automation.model.query.AutomationUiExecutionAccessRow;
import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.req.AutomationUiPageReq;
import top.continew.admin.automation.model.resp.AutomationUiExecutionArtifactResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionPageResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionSummaryResp;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.automation.support.AutomationUiExecutionCursorCodec;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver.AccessScope;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AutomationUiExecutionQueryServiceImplTest {

    @Mock
    private AutomationUiExecutionQueryMapper queryMapper;
    @Mock
    private AutomationUiSceneAccessScopeResolver accessScopeResolver;

    private AutomationUiExecutionQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationUiExecutionQueryServiceImpl(queryMapper, accessScopeResolver, new AutomationUiExecutionCursorCodec("0123456789abcdef0123456789abcdef", 900));
    }

    @Test
    void pageShouldUseExplicitScopeDatabaseFiltersAndGlobalRevision() {
        stubScope();
        AutomationUiExecutionAccessRow access = access(12L, 1L);
        when(queryMapper.selectExecutionPageCount(any(), any(), eq(9L), eq(false))).thenReturn(access);
        AutomationUiExecutionSummaryResp execution = new AutomationUiExecutionSummaryResp();
        execution.setExecutionDbId(21L);
        when(queryMapper.selectExecutionPage(any(), any(), eq(9L), eq(false), eq(0L), eq(20), eq(false)))
            .thenReturn(List.of(execution));
        AutomationUiExecutionQuery query = query(8L, "test");
        query.setTestPlanId(7L);
        query.setStatus("PASSED");
        AutomationUiPageReq pageQuery = page(1, 20);

        var response = service.page(query, pageQuery);

        ArgumentCaptor<AutomationUiExecutionScopeReq> scopeCaptor = ArgumentCaptor
            .forClass(AutomationUiExecutionScopeReq.class);
        verify(queryMapper).selectExecutionPageCount(eq(query), scopeCaptor.capture(), eq(9L), eq(false));
        assertThat(scopeCaptor.getValue().getRecordSource()).isEqualTo("test");
        assertThat(scopeCaptor.getValue().getTestPlanId()).isEqualTo(7L);
        assertThat(query.getStatus()).isEqualTo("passed");
        assertThat(response.getMode()).isEqualTo("page");
        assertThat(response.getGlobalExecutionRevision()).isEqualTo(12L);
        assertThat(response.getList()).containsExactly(execution);
    }

    @Test
    void pageShouldRejectMissingScopeBeforeDatabaseAccess() {
        AutomationUiExecutionQuery query = new AutomationUiExecutionQuery();
        query.setSceneDbId(8L);

        assertThatThrownBy(() -> service.page(query, page(1, 20))).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("EXECUTION_SCOPE_REQUIRED");
        verify(queryMapper, never()).selectSceneAccess(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void childPageShouldHideUnauthorizedAndMissingExecution() {
        stubScope();
        when(queryMapper.selectCasePageCount(88L, 9L, false)).thenReturn(null);

        assertThatThrownBy(() -> service.cases(88L, page(1, 20))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("NOT_FOUND_OR_ACCESS_DENIED");
        verify(queryMapper, never()).selectCasePage(anyLong(), anyLong(), anyBoolean(), anyLong(), anyInt());
    }

    @Test
    void artifactPageShouldReturnOnlyCurrentPageAfterAccessCheck() {
        stubScope();
        when(queryMapper.selectArtifactPageCount(88L, 9L, false)).thenReturn(access(3L, 1L));
        AutomationUiExecutionArtifactResp artifact = new AutomationUiExecutionArtifactResp();
        artifact.setArtifactDbId(31L);
        when(queryMapper.selectArtifactPage(88L, 9L, false, 0L, 20)).thenReturn(List.of(artifact));

        var response = service.artifacts(88L, page(1, 20));

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getList()).containsExactly(artifact);
    }

    @Test
    void childPagesShouldRejectClientSortBecauseOrderIsFixed() {
        AutomationUiPageReq pageQuery = page(1, 20);
        pageQuery.setSort(new String[] {"createTime", "desc"});

        assertThatThrownBy(() -> service.steps(3L, pageQuery)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_SORT_FIELD");
        verify(accessScopeResolver, never()).currentScope();
    }

    @Test
    void executionPageShouldRejectDeepOffsetAndUnapprovedSort() {
        assertThatThrownBy(() -> service.page(query(8L, "debug"), page(501, 20)))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("OFFSET_LIMIT_EXCEEDED");
        AutomationUiPageReq invalidSort = page(1, 20);
        invalidSort.setSort(new String[] {"status", "desc"});
        assertThatThrownBy(() -> service.page(query(8L, "debug"), invalidSort)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_SORT_FIELD");
    }

    @Test
    void cursorPageShouldUseKeysetWithoutExactCount() {
        stubScope();
        when(queryMapper.selectSceneAccess(8L, 9L, false)).thenReturn(access(15L));
        List<AutomationUiExecutionSummaryResp> candidates = LongStream.rangeClosed(1, 21).mapToObj(id -> {
            AutomationUiExecutionSummaryResp item = new AutomationUiExecutionSummaryResp();
            item.setExecutionDbId(id);
            item.setCreateTime(LocalDateTime.of(2026, 8, 18, 10, 0).minusSeconds(id));
            return item;
        }).toList();
        when(queryMapper.selectExecutionCursor(any(), any(), eq(9L), eq(false), isNull(), isNull(), eq(21), eq(false)))
            .thenReturn(candidates);
        AutomationUiExecutionQuery query = query(8L, "debug");
        query.setCursor("start");

        AutomationUiExecutionPageResp response = service.page(query, page(1, 20));

        assertThat(response).isInstanceOf(AutomationUiExecutionPageResp.Cursor.class);
        AutomationUiExecutionPageResp.Cursor cursorResponse = (AutomationUiExecutionPageResp.Cursor)response;
        assertThat(cursorResponse.getList()).hasSize(20);
        assertThat(cursorResponse.getHasMore()).isTrue();
        assertThat(cursorResponse.getNextCursor()).isNotBlank();
        assertThat(cursorResponse.getGlobalExecutionRevision()).isEqualTo(15L);
        verify(queryMapper, never()).selectExecutionPageCount(any(), any(), anyLong(), anyBoolean());
    }

    private AutomationUiExecutionQuery query(Long sceneDbId, String recordSource) {
        AutomationUiExecutionQuery query = new AutomationUiExecutionQuery();
        query.setSceneDbId(sceneDbId);
        query.setRecordSource(recordSource);
        return query;
    }

    private AutomationUiPageReq page(int page, int size) {
        AutomationUiPageReq query = new AutomationUiPageReq();
        query.setPage(page);
        query.setSize(size);
        return query;
    }

    private AutomationUiExecutionAccessRow access(long revision) {
        return access(revision, null);
    }

    private AutomationUiExecutionAccessRow access(long revision, Long total) {
        AutomationUiExecutionAccessRow access = new AutomationUiExecutionAccessRow();
        access.setSceneDbId(8L);
        access.setGlobalExecutionRevision(revision);
        access.setTotal(total);
        return access;
    }

    private void stubScope() {
        when(accessScopeResolver.currentScope()).thenReturn(new AccessScope(9L, false, Set.of("scene:get"), Set
            .of("developer")));
    }
}
