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

import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.converter.AutomationUiDefinitionSnapshotMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.service.AutomationUiDefinitionRevisionService;
import top.continew.starter.core.exception.BusinessException;

/** Shared revision boundary used by execution facts and case reviews. */
@Service
@RequiredArgsConstructor
public class AutomationUiDefinitionRevisionServiceImpl implements AutomationUiDefinitionRevisionService {

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;

    @Override
    public Revision ensure(AutomationUiSceneDO scene) {
        if (scene == null || scene.getId() == null || scene.getDefinitionVersion() == null || scene
            .getDefinitionVersion() < 0) {
            throw new BusinessException("DEFINITION_VERSION_REQUIRED：场景定义版本不能为空");
        }
        AutomationUiDefinitionSnapshotMapper.Snapshot snapshot = AutomationUiDefinitionSnapshotMapper.map(scene
            .getCaseList());
        Revision existing = find(scene.getId(), scene.getDefinitionVersion(), false);
        if (existing != null) {
            requireSameContent(existing, snapshot.contentHash());
            return existing;
        }
        Long id = identifierGenerator.nextId(scene).longValue();
        try {
            jdbcTemplate
                .update("INSERT INTO automation_ui_scene_definition_revision" + " (id, scene_id, revision_no, definition_version, content_hash, definition_json, create_user, create_time)" + " VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))", id, scene
                    .getId(), scene.getDefinitionVersion(), scene.getDefinitionVersion(), snapshot
                        .contentHash(), snapshot.definitionJson(), scene.getUpdateUser() == null
                            ? scene.getCreateUser()
                            : scene.getUpdateUser());
            return new Revision(id, scene.getDefinitionVersion(), snapshot.contentHash(), snapshot.definitionJson());
        } catch (DuplicateKeyException ignored) {
            Revision raced = find(scene.getId(), scene.getDefinitionVersion(), true);
            if (raced == null) {
                throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：并发创建后未找到定义 revision");
            }
            requireSameContent(raced, snapshot.contentHash());
            return raced;
        }
    }

    private Revision find(Long sceneId, Long definitionVersion, boolean forUpdate) {
        List<Revision> rows = jdbcTemplate
            .query("SELECT id, definition_version, content_hash, definition_json" + " FROM automation_ui_scene_definition_revision WHERE scene_id = ? AND definition_version = ? LIMIT 1" + (forUpdate
                ? " FOR UPDATE"
                : ""), (rs, rowNum) -> new Revision(rs.getLong("id"), rs.getLong("definition_version"), rs
                    .getString("content_hash"), rs.getString("definition_json")), sceneId, definitionVersion);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireSameContent(Revision revision, String contentHash) {
        if (!Objects.equals(revision.contentHash(), contentHash)) {
            throw new BusinessException("DEFINITION_REVISION_CONFLICT：同一 definitionVersion 已绑定不同定义内容");
        }
    }
}
