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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.admin.test.mapper.TestReportSceneMapper;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestReportSceneDO;
import top.continew.starter.core.exception.BusinessException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 固化报告执行范围，避免历史统计依赖可变场景定义或 JSON 字段。
 */
@Service
@RequiredArgsConstructor
public class TestReportSceneSnapshotService {

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final ProjectVersionConfigMapper projectVersionConfigMapper;
    private final TestReportSceneMapper testReportSceneMapper;
    private final JdbcTemplate jdbcTemplate;

    public void validateVersion(Long projectId, Long versionId) {
        if (versionId == null) {
            return;
        }
        ProjectVersionConfigDO version = projectVersionConfigMapper.selectById(versionId);
        if (version == null || !Objects.equals(projectId, version.getProjectId()) || !StatusTypeEnum.NORMAL
            .equals(version.getDelFlag())) {
            throw new BusinessException("项目版本不存在或不属于当前项目");
        }
    }

    public List<AutomationUiSceneDO> loadAndValidate(Long projectId, Long expectedVersionId, List<Long> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = sceneIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.size() != sceneIds.size()) {
            throw new BusinessException("测试场景不能为空或重复");
        }
        List<AutomationUiSceneDO> loaded = automationUiSceneMapper.selectBatchIds(distinctIds);
        Map<Long, AutomationUiSceneDO> sceneById = new HashMap<>();
        loaded.forEach(scene -> sceneById.put(scene.getId(), scene));
        List<AutomationUiSceneDO> ordered = new ArrayList<>(sceneIds.size());
        for (Long sceneId : sceneIds) {
            AutomationUiSceneDO scene = sceneById.get(sceneId);
            if (scene == null || !StatusTypeEnum.NORMAL.equals(scene.getDelFlag())) {
                throw new BusinessException("测试场景不存在或已删除，sceneId=" + sceneId);
            }
            if (!StatusTypeEnum.ENABLE.equals(scene.getStatus())) {
                throw new BusinessException("测试场景未启用，sceneId=" + sceneId);
            }
            if (!Objects.equals(projectId, scene.getProjectId())) {
                throw new BusinessException("测试场景不属于当前项目，sceneId=" + sceneId);
            }
            if (expectedVersionId != null && !Objects.equals(expectedVersionId, scene.getVersionId())) {
                throw new BusinessException("测试场景不属于当前项目版本，sceneId=" + sceneId);
            }
            ordered.add(scene);
        }
        long versionCount = ordered.stream().map(AutomationUiSceneDO::getVersionId).distinct().count();
        if (versionCount != 1) {
            throw new BusinessException("同一测试计划不能关联多个项目版本的场景");
        }
        return ordered;
    }

    public Long resolveVersionId(Long projectId, Long expectedVersionId, List<AutomationUiSceneDO> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            validateVersion(projectId, expectedVersionId);
            return expectedVersionId;
        }
        Long versionId = scenes.get(0).getVersionId();
        validateVersion(projectId, versionId);
        if (expectedVersionId != null && !Objects.equals(expectedVersionId, versionId)) {
            throw new BusinessException("测试场景版本与测试计划版本不一致");
        }
        return versionId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshot(TestReportDO report, List<AutomationUiSceneDO> scenes) {
        if (report == null || report.getId() == null) {
            throw new BusinessException("测试报告尚未创建，无法保存执行范围");
        }
        testReportSceneMapper.lambdaUpdate().eq(TestReportSceneDO::getTestReportId, report.getId()).remove();
        Map<Long, Long> revisionIds = findLatestRevisionIds(scenes);
        int sort = 0;
        for (AutomationUiSceneDO scene : scenes) {
            TestReportSceneDO snapshot = new TestReportSceneDO();
            snapshot.setTestReportId(report.getId());
            snapshot.setTestPlanId(report.getTestPlanId());
            snapshot.setProjectId(scene.getProjectId());
            snapshot.setVersionId(scene.getVersionId());
            snapshot.setModuleId(scene.getModuleId());
            snapshot.setSceneId(scene.getId());
            snapshot.setSceneKey(scene.getSceneId());
            snapshot.setSceneName(scene.getName());
            snapshot.setSceneLevel(scene.getLevel());
            snapshot.setDefinitionRevisionId(revisionIds.get(scene.getId()));
            snapshot.setSort(sort++);
            testReportSceneMapper.insert(snapshot);
        }
    }

    private Map<Long, Long> findLatestRevisionIds(List<AutomationUiSceneDO> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return Map.of();
        }
        List<Long> sceneIds = scenes.stream().map(AutomationUiSceneDO::getId).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(sceneIds.size(), "?"));
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbcTemplate
            .query("SELECT r.scene_id, r.id FROM automation_ui_scene_definition_revision r " + "JOIN (SELECT scene_id, MAX(revision_no) revision_no FROM automation_ui_scene_definition_revision " + "WHERE scene_id IN (" + placeholders + ") GROUP BY scene_id) latest " + "ON latest.scene_id = r.scene_id AND latest.revision_no = r.revision_no", rs -> {
                result.put(rs.getLong("scene_id"), rs.getLong("id"));
            }, sceneIds.toArray());
        return result;
    }
}
