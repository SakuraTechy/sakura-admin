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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.starter.core.exception.BusinessException;

class AutomationUiCaseReviewGateServiceImplTest {

    @Test
    void observeModeMustRemainNoOp() throws Exception {
        Fixture fixture = fixture("OBSERVE", false);

        assertThatCode(() -> fixture.service.assertExecutionAllowed(List.of(scene()), Map.of(), "MANUAL", null, false))
            .doesNotThrowAnyException();

        verify(fixture.jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }

    @Test
    void enforceModeMustRejectCurrentCaseWithoutApproval() throws Exception {
        Fixture fixture = fixture("ENFORCE", false);
        when(fixture.jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> fixture.service.assertExecutionAllowed(List.of(scene()), Map
            .of(), "TEST_PLAN", null, false)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_GATE_BLOCKED");
    }

    @Test
    void enforceModeMustAllowExactApprovedHash() throws Exception {
        Fixture fixture = fixture("ENFORCE", false);
        when(fixture.jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThatCode(() -> fixture.service.assertExecutionAllowed(List.of(scene()), Map
            .of(), "TEST_PLAN", null, false)).doesNotThrowAnyException();
    }

    @Test
    void enforceModeMustRecheckEvidenceAgeAtExecutionTime() throws Exception {
        Fixture fixture = fixture("ENFORCE", true);
        when(fixture.jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0);

        assertThatThrownBy(() -> fixture.service.assertExecutionAllowed(List.of(scene()), Map
            .of(), "TEST_PLAN", null, false)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_GATE_BLOCKED");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(fixture.jdbcTemplate, org.mockito.Mockito.times(2)).queryForObject(sql
            .capture(), eq(Integer.class), any(Object[].class));
        assertThat(sql.getAllValues().get(1)).contains("e.scene_id = ?").contains("c.case_id = ?");
    }

    @Test
    void preauthorizedBypassMustAppendAuditInIndependentTransaction() throws Exception {
        Fixture fixture = fixture("ENFORCE", false);
        when(fixture.jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(fixture.identifierGenerator.nextId(any())).thenReturn(99L);
        when(fixture.transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThatCode(() -> fixture.service.assertExecutionAllowed(List.of(scene()), Map
            .of(), "TEST_PLAN", "紧急回归放行", true)).doesNotThrowAnyException();

        verify(fixture.jdbcTemplate).update(anyString(), any(Object[].class));
        verify(fixture.transactionManager).commit(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Fixture fixture(String mode, boolean evidenceRequired) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("mode")).thenReturn(mode);
            when(resultSet.getBoolean("execution_evidence_required")).thenReturn(evidenceRequired);
            when(resultSet.getInt("execution_evidence_max_age_h")).thenReturn(24);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        return new Fixture(jdbcTemplate, identifierGenerator, transactionManager, new AutomationUiCaseReviewGateServiceImpl(jdbcTemplate, identifierGenerator, transactionManager));
    }

    private AutomationUiSceneDO scene() {
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE-1");
        caseDO.setName("登录成功");
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(10L);
        scene.setProjectId(1L);
        scene.setCaseList(List.of(caseDO));
        return scene;
    }

    private record Fixture(JdbcTemplate jdbcTemplate, IdentifierGenerator identifierGenerator,
                           PlatformTransactionManager transactionManager,
                           AutomationUiCaseReviewGateServiceImpl service) {
    }
}
