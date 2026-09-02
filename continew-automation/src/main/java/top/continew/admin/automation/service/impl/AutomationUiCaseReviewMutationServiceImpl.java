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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.converter.AutomationUiCaseFingerprint;
import top.continew.admin.automation.converter.AutomationUiDefinitionSnapshotMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.service.AutomationUiCaseReviewMutationService;
import top.continew.admin.automation.service.AutomationUiDefinitionRevisionService;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.system.enums.MessageTypeEnum;
import top.continew.admin.system.model.req.MessageReq;
import top.continew.admin.system.service.MessageService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationUiCaseReviewMutationServiceImpl implements AutomationUiCaseReviewMutationService {

    private static final Set<String> INVALIDATABLE_STATUSES = Set.of("IN_REVIEW", "CHANGES_REQUESTED", "APPROVED");

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;
    private final AutomationUiDefinitionRevisionService revisionService;
    private final MessageService messageService;

    @Override
    public void captureBefore(AutomationUiSceneDO scene) {
        revisionService.ensure(scene);
    }

    @Override
    public void afterDefinitionChanged(Long sceneId,
                                       Long definitionVersion,
                                       List<CaseDO> currentCases,
                                       String changeType) {
        Long actor = UserContextHolder.getUserId();
        Map<String, CaseDO> before = loadPreviousCases(sceneId, definitionVersion);
        Map<String, CaseDO> after = index(currentCases);
        LinkedHashSet<String> caseIds = new LinkedHashSet<>(before.keySet());
        caseIds.addAll(after.keySet());
        for (String caseId : caseIds) {
            CaseDO oldCase = before.get(caseId);
            CaseDO newCase = after.get(caseId);
            AutomationUiCaseFingerprint.Fingerprint oldFingerprint = oldCase == null
                ? null
                : AutomationUiCaseFingerprint.compute(oldCase);
            AutomationUiCaseFingerprint.Fingerprint newFingerprint = newCase == null
                ? null
                : AutomationUiCaseFingerprint.compute(newCase);
            if (sameFingerprint(oldFingerprint, newFingerprint)) {
                continue;
            }
            String effectiveChange = oldCase == null ? "CREATED" : newCase == null ? "DELETED" : changeType;
            jdbcTemplate
                .update("INSERT INTO automation_ui_case_definition_audit (id, scene_id, case_id, definition_version," + " case_content_hash, hash_schema_version, change_type, editor_id, edited_at)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))", nextId(caseId), sceneId, caseId, definitionVersion, newFingerprint == null
                    ? null
                    : newFingerprint.hash(), newFingerprint == null
                        ? null
                        : newFingerprint.schemaVersion(), effectiveChange, actor);
            expireReviews(sceneId, caseId, newFingerprint == null ? null : newFingerprint.hash(), newFingerprint == null
                ? null
                : newFingerprint.schemaVersion(), actor);
        }
    }

    private void expireReviews(Long sceneId,
                               String caseId,
                               String currentHash,
                               String currentSchemaVersion,
                               Long actor) {
        List<ExpiredReview> expired = jdbcTemplate
            .query("SELECT id, case_content_hash, hash_schema_version, submitter_id FROM automation_ui_case_review" + " WHERE scene_id = ? AND case_id = ? AND status IN ('IN_REVIEW','CHANGES_REQUESTED','APPROVED')" + " FOR UPDATE", (rs,
                                                                                                                                                                                                                                         rowNum) -> new ExpiredReview(rs
                                                                                                                                                                                                                                             .getLong("id"), rs
                                                                                                                                                                                                                                                 .getString("case_content_hash"), rs
                                                                                                                                                                                                                                                     .getString("hash_schema_version"), rs
                                                                                                                                                                                                                                                         .getLong("submitter_id")), sceneId, caseId)
            .stream()
            .filter(review -> !Objects.equals(review.hash(), currentHash) || !Objects.equals(review
                .schemaVersion(), currentSchemaVersion))
            .toList();
        for (ExpiredReview review : expired) {
            int changed = jdbcTemplate
                .update("UPDATE automation_ui_case_review SET status = 'OUTDATED'," + " completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP(3)), version = version + 1," + " update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE id = ? AND status IN ('IN_REVIEW','CHANGES_REQUESTED','APPROVED')", actor, review
                    .id());
            if (changed == 0)
                continue;
            Long eventActor = actor == null ? 0L : actor;
            jdbcTemplate
                .update("INSERT INTO automation_ui_case_review_event (id, review_id, event_type, actor_id, payload_json, create_time)" + " VALUES (?, ?, 'OUTDATED', ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))", nextId(review), review
                    .id(), eventActor, JSONUtil.toJsonStr(Map.of("reason", "CASE_CONTENT_CHANGED")));
            List<Long> recipients = new ArrayList<>();
            recipients.add(review.submitterId());
            recipients.addAll(jdbcTemplate
                .query("SELECT reviewer_id FROM automation_ui_case_review_reviewer WHERE review_id = ?", (rs,
                                                                                                          rowNum) -> rs
                                                                                                              .getLong(1), review
                                                                                                                  .id()));
            notifyOutdated(caseId, recipients.stream().filter(Objects::nonNull).distinct().toList());
        }
    }

    private Map<String, CaseDO> loadPreviousCases(Long sceneId, Long definitionVersion) {
        if (definitionVersion == null || definitionVersion <= 0)
            return Map.of();
        String json = jdbcTemplate
            .query("SELECT definition_json FROM automation_ui_scene_definition_revision" + " WHERE scene_id = ? AND definition_version = ? LIMIT 1", (rs,
                                                                                                                                                      rowNum) -> rs
                                                                                                                                                          .getString(1), sceneId, definitionVersion - 1)
            .stream()
            .findFirst()
            .orElse(null);
        if (json == null)
            return Map.of();
        try {
            return index(AutomationUiDefinitionSnapshotMapper.readCases(objectMapper, json));
        } catch (Exception e) {
            log.warn("Failed to read previous UI scene definition revision, sceneId={}, version={}", sceneId, definitionVersion, e);
            return Map.of();
        }
    }

    private Map<String, CaseDO> index(List<CaseDO> cases) {
        if (cases == null)
            return Map.of();
        return cases.stream()
            .filter(Objects::nonNull)
            .filter(item -> item.getId() != null)
            .collect(Collectors.toMap(CaseDO::getId, Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    static boolean sameFingerprint(AutomationUiCaseFingerprint.Fingerprint left,
                                   AutomationUiCaseFingerprint.Fingerprint right) {
        return Objects.equals(left == null ? null : left.hash(), right == null ? null : right.hash()) && Objects
            .equals(left == null ? null : left.schemaVersion(), right == null ? null : right.schemaVersion());
    }

    private void notifyOutdated(String caseId, List<Long> recipients) {
        if (recipients.isEmpty())
            return;
        try {
            MessageReq request = new MessageReq();
            request.setTitle("UI 用例评审已过期");
            request.setContent("用例 " + caseId + " 的定义已更新，请提交新版本评审");
            request.setType(MessageTypeEnum.SECURITY);
            messageService.add(request, recipients);
        } catch (RuntimeException e) {
            log.warn("Failed to send outdated case review notification, caseId={}", caseId, e);
        }
    }

    private Long nextId(Object source) {
        return identifierGenerator.nextId(source).longValue();
    }

    private record ExpiredReview(Long id, String hash, String schemaVersion, Long submitterId) {
    }
}
