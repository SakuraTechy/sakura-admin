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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.admin.test.mapper.TestReportSceneMapper;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestReportSceneDO;
import top.continew.starter.core.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class TestReportSceneSnapshotServiceTest {

    @Mock
    private AutomationUiSceneMapper sceneMapper;

    @Mock
    private ProjectVersionConfigMapper versionMapper;

    @Mock
    private TestReportSceneMapper reportSceneMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TestReportSceneSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new TestReportSceneSnapshotService(sceneMapper, versionMapper, reportSceneMapper, jdbcTemplate);
    }

    @Test
    void shouldRejectDuplicateScenesBeforeQuery() {
        assertThatThrownBy(() -> service.loadAndValidate(1L, 11L, List.of(101L, 101L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("重复");
    }

    @Test
    void shouldRejectDeletedAndCrossVersionScenes() {
        AutomationUiSceneDO deleted = scene(101L, 1L, 11L);
        deleted.setDelFlag(StatusTypeEnum.ABNORMAL);
        when(sceneMapper.selectBatchIds(List.of(101L))).thenReturn(List.of(deleted));
        assertThatThrownBy(() -> service.loadAndValidate(1L, 11L, List.of(101L))).hasMessageContaining("已删除");

        AutomationUiSceneDO otherVersion = scene(102L, 1L, 12L);
        when(sceneMapper.selectBatchIds(List.of(102L))).thenReturn(List.of(otherVersion));
        assertThatThrownBy(() -> service.loadAndValidate(1L, 11L, List.of(102L))).hasMessageContaining("不属于当前项目版本");
    }

    @Test
    void shouldTreatZeroVersionAsUnspecified() {
        AutomationUiSceneDO scene = scene(101L, 1L, 11L);
        ProjectVersionConfigDO version = new ProjectVersionConfigDO();
        version.setId(11L);
        version.setProjectId(1L);
        version.setDelFlag(StatusTypeEnum.NORMAL);
        when(sceneMapper.selectBatchIds(List.of(101L))).thenReturn(List.of(scene));
        when(versionMapper.selectById(11L)).thenReturn(version);

        List<AutomationUiSceneDO> scenes = service.loadAndValidate(1L, 0L, List.of(101L));

        assertThat(service.resolveVersionId(1L, 0L, scenes)).isEqualTo(11L);
    }

    @Test
    void shouldRejectSnapshotOverwrite() {
        TestReportDO report = new TestReportDO();
        report.setId(201L);
        when(reportSceneMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.saveSnapshot(report, List.of(scene(101L, 1L, 11L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不允许覆盖");

        verify(reportSceneMapper, never()).insert(any(TestReportSceneDO.class));
        verify(jdbcTemplate, never())
            .query(any(String.class), any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));
    }

    private AutomationUiSceneDO scene(Long id, Long projectId, Long versionId) {
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(id);
        scene.setProjectId(projectId);
        scene.setVersionId(versionId);
        scene.setStatus(StatusTypeEnum.ENABLE);
        scene.setDelFlag(StatusTypeEnum.NORMAL);
        return scene;
    }
}
