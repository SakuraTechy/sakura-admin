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

package top.continew.admin.test.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.admin.test.model.query.TestMetricScopeQuery;
import top.continew.starter.core.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class TestMetricQueryServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ProjectConfigMapper projectConfigMapper;

    @Mock
    private ProjectVersionConfigMapper projectVersionConfigMapper;

    private TestMetricQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestMetricQueryServiceImpl(jdbcTemplate, projectConfigMapper, projectVersionConfigMapper);
        setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clearContext();
    }

    @Test
    void shouldCalculatePassRateFromPassedAndFailedOnly() {
        assertThat(TestMetricQueryServiceImpl.percent(7, 10)).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(TestMetricQueryServiceImpl.percent(0, 0)).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void shouldRejectVersionOutsideProject() {
        when(projectConfigMapper.selectById(1L)).thenReturn(project(1L));
        when(projectVersionConfigMapper.selectById(11L)).thenReturn(version(11L, 2L));

        TestMetricScopeQuery query = query(1L, LocalDate.now(), LocalDate.now());
        query.setVersionId(11L);

        assertThatThrownBy(() -> service.getTrends(query)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前项目");
    }

    @Test
    void shouldRejectDeletedProjectAndInvalidDateRange() {
        ProjectConfigDO deleted = project(1L);
        deleted.setDelFlag(StatusTypeEnum.ABNORMAL);
        when(projectConfigMapper.selectById(1L)).thenReturn(deleted);
        assertThatThrownBy(() -> service.getTrends(query(1L, LocalDate.now(), LocalDate.now())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("项目不存在或已删除");

        when(projectConfigMapper.selectById(2L)).thenReturn(project(2L));
        assertThatThrownBy(() -> service.getTrends(query(2L, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("开始日期不能晚于结束日期");
    }

    @Test
    void shouldUseHalfOpenTimeRangeAndFillDateGaps() {
        when(projectConfigMapper.selectById(1L)).thenReturn(project(1L));
        doNothing().when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        TestMetricScopeQuery query = query(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));

        var result = service.getTrends(query);

        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getPoints()).allSatisfy(point -> {
            assertThat(point.getPassCount()).isZero();
            assertThat(point.getPassRate()).isEqualByComparingTo(new BigDecimal("0.00"));
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), any(RowCallbackHandler.class), args.capture());
        assertThat(sql.getValue()).contains("e.metric_time >= ? AND e.metric_time < ?")
            .contains("GROUP BY DATE(e.metric_time)");
        assertThat(args.getValue()[1]).isEqualTo(Timestamp.valueOf("2026-08-01 00:00:00"));
        assertThat(args.getValue()[2]).isEqualTo(Timestamp.valueOf("2026-08-03 00:00:00"));
    }

    @Test
    void shouldKeepTerminalCategoriesMutuallyExclusiveInDocumentedPrecedence() {
        String expression = TestMetricSqlExpressions.CATEGORY_EXPR;

        assertThat(expression.indexOf(TestMetricSqlExpressions.CANCEL_EXPR)).isLessThan(expression
            .indexOf(TestMetricSqlExpressions.INFRA_EXPR));
        assertThat(expression.indexOf(TestMetricSqlExpressions.INFRA_EXPR)).isLessThan(expression
            .indexOf(TestMetricSqlExpressions.PASS_EXPR));
        assertThat(expression.indexOf(TestMetricSqlExpressions.PASS_EXPR)).isLessThan(expression
            .indexOf(TestMetricSqlExpressions.FAIL_EXPR));
        assertThat(expression.indexOf(TestMetricSqlExpressions.FAIL_EXPR)).isLessThan(expression
            .indexOf(TestMetricSqlExpressions.SKIP_EXPR));
        assertThat(TestMetricSqlExpressions.sumTerminal("total")).contains(TestMetricSqlExpressions.TERMINAL_EXPR);
        for (String category : List.of("CANCELLED", "INFRA_FAILED", "PASSED", "FAILED", "SKIPPED", "OTHER")) {
            assertThat(TestMetricSqlExpressions.sumCategory(category, category.toLowerCase()))
                .contains(TestMetricSqlExpressions.CATEGORY_EXPR + " = '" + category + "'");
        }
    }

    @Test
    void shouldAllowListedProjectMemberAndRejectUnlistedUser() {
        ProjectConfigDO project = project(1L);
        project.setCreateUser(7L);
        project.setMember(List.of("42"));
        when(projectConfigMapper.selectById(1L)).thenReturn(project);
        doNothing().when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        setUser(42L);
        assertThat(service.getTrends(query(1L, LocalDate.now(), LocalDate.now())).getPoints()).hasSize(1);

        setUser(43L);
        assertThatThrownBy(() -> service.getTrends(query(1L, LocalDate.now(), LocalDate.now())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void shouldRejectMetricQueryWithoutUserContext() {
        UserContextHolder.clearContext();
        when(projectConfigMapper.selectById(1L)).thenReturn(project(1L));

        assertThatThrownBy(() -> service.getTrends(query(1L, LocalDate.now(), LocalDate.now())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未获取到当前用户");
    }

    @Test
    void shouldNormalizeLegacyEngineAliasInFilter() {
        when(projectConfigMapper.selectById(1L)).thenReturn(project(1L));
        doNothing().when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        TestMetricScopeQuery query = query(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));
        query.setExecutionEngine("PLAYWRIGHT_RUNNER");

        service.getTrends(query);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), any(RowCallbackHandler.class), args.capture());
        assertThat(sql.getValue()).contains("REPLACE(COALESCE(e.execution_engine");
        assertThat(args.getValue()[3]).isEqualTo("playwright-runner");
    }

    private TestMetricScopeQuery query(Long projectId, LocalDate startDate, LocalDate endDate) {
        TestMetricScopeQuery query = new TestMetricScopeQuery();
        query.setProjectId(projectId);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        return query;
    }

    private ProjectConfigDO project(Long id) {
        ProjectConfigDO project = new ProjectConfigDO();
        project.setId(id);
        project.setCreateUser(1L);
        project.setDelFlag(StatusTypeEnum.NORMAL);
        return project;
    }

    private ProjectVersionConfigDO version(Long id, Long projectId) {
        ProjectVersionConfigDO version = new ProjectVersionConfigDO();
        version.setId(id);
        version.setProjectId(projectId);
        version.setDelFlag(StatusTypeEnum.NORMAL);
        return version;
    }

    private void setUser(Long userId) {
        UserContext context = new UserContext();
        context.setId(userId);
        context.setRoleCodes(Set.of());
        UserContextHolder.setContext(context, false);
    }
}
