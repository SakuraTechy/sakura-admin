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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;

class AutomationUiExecutionRecordServiceImplTest {

    @Test
    void shouldFindPreparedJenkinsExecutionByBuildNumber() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper());

        assertThat(service.findBatch(1L, "42")).isNull();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getAllValues().get(0)).contains("batch_id = ?");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("build_number = ?");
    }

    @Test
    void shouldCommitExecutionAndDefinitionRevisionBeforeExternalExecutionStarts() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(identifierGenerator.nextId(any())).thenReturn(7L, 8L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper());
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SCENE_001");
        scene.setDefinitionVersion(3L);
        scene.setCaseList(List.of());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("batchId", "JENKINS_BATCH_001");
        record.put("executionType", "jenkins");
        record.put("executeStatus", "queued");

        service.saveExternalExecutionRecord(scene, record);

        Transactional transactional = AutomationUiExecutionRecordServiceImpl.class
            .getMethod("saveExternalExecutionRecord", AutomationUiSceneDO.class, Map.class)
            .getAnnotation(Transactional.class);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        boolean revisionInserted = false;
        boolean executionInserted = false;
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            String sql = sqlCaptor.getAllValues().get(i);
            Object[] args = argsCaptor.getAllValues().get(i);
            if (sql.startsWith("INSERT INTO automation_ui_scene_definition_revision")) {
                revisionInserted = true;
                assertThat(args[0]).isEqualTo(7L);
                assertThat(args[1]).isEqualTo(1L);
                assertThat(args[2]).isEqualTo(3L);
            }
            if (sql.startsWith("INSERT INTO automation_ui_execution")) {
                executionInserted = true;
                assertThat(args[0]).isEqualTo(8L);
                assertThat(args[4]).isEqualTo(7L);
                assertThat(args[7]).isEqualTo("JENKINS_BATCH_001");
            }
        }
        assertThat(revisionInserted).isTrue();
        assertThat(executionInserted).isTrue();
    }

    @Test
    void shouldKeepBoundRevisionWhenExistingExecutionReceivesLaterResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM automation_ui_execution WHERE execution_key")) {
                return List.of(100L);
            }
            if (sql.contains("SELECT definition_revision_id FROM automation_ui_execution")) {
                return List.of(7L);
            }
            return List.of();
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        AutomationUiExecutionRecordServiceImpl service = new AutomationUiExecutionRecordServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper());
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SCENE_001");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("batchId", "BATCH_001");
        record.put("executionType", "playwright-runner");
        record.put("executeStatus", "completed");
        record.put("executeResult", "passed");

        service.saveRecord(scene, record, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        boolean updatedWithFrozenRevision = false;
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            if (sqlCaptor.getAllValues().get(i).startsWith("UPDATE automation_ui_execution SET")) {
                updatedWithFrozenRevision = true;
                assertThat(argsCaptor.getAllValues().get(i)[0]).isEqualTo(7L);
            }
        }
        assertThat(updatedWithFrozenRevision).isTrue();
        verify(jdbcTemplate, never())
            .update(eq("INSERT INTO automation_ui_scene_definition_revision"), any(Object[].class));
    }
}
