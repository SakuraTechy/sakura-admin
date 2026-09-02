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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.AutomationUiCaseFingerprint;
import top.continew.admin.automation.converter.AutomationUiDefinitionSnapshotMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewChecklistReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewCommentReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewDecisionReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewResolveReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewSubmitReq;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewResp;
import top.continew.admin.automation.service.AutomationUiCaseReviewChecker;
import top.continew.admin.automation.service.AutomationUiCaseReviewService;
import top.continew.admin.automation.service.AutomationUiDefinitionRevisionService;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.system.enums.MessageTypeEnum;
import top.continew.admin.system.model.req.MessageReq;
import top.continew.admin.system.service.MessageService;
import top.continew.starter.core.exception.BusinessException;

/** Transactional state machine for version-bound case reviews. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationUiCaseReviewServiceImpl implements AutomationUiCaseReviewService {

    private static final String POLICY_VERSION = "CASE_REVIEW_POLICY_V1";
    private static final Set<String> INVALIDATABLE_STATUSES = Set.of("IN_REVIEW", "CHANGES_REQUESTED", "APPROVED");
    private static final Set<String> CASE_FIELD_PATHS = Set
        .of("name", "remark", "status", "cancel", "type", "executionConfig", "origin");
    private static final Set<String> STEP_FIELD_PATHS = Set
        .of("name", "remark", "status", "type", "operationType", "operationName", "operationValue", "setting", "configList", "configList.playwright_step", "configList.locator_meta");
    private static final List<ChecklistDefinition> CHECKLIST = List
        .of(new ChecklistDefinition("GOAL", "用例目标与验收条件一致"), new ChecklistDefinition("COVERAGE", "主流程、异常路径和边界覆盖合理"), new ChecklistDefinition("REPEATABLE", "前置条件、测试数据和清理动作可重复"), new ChecklistDefinition("ASSERTION", "关键业务结果有明确断言"), new ChecklistDefinition("MAINTAINABILITY", "定位、等待和环境依赖可维护"), new ChecklistDefinition("SECURITY", "敏感信息和产物展示符合安全要求"));
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;
    private final AutomationUiSceneMapper sceneMapper;
    private final AutomationUiDefinitionRevisionService definitionRevisionService;
    private final AutomationUiCaseReviewChecker checker;
    private final MessageService messageService;
    private final AutomationUiCaseReviewReviewerValidator reviewerValidator;
    private final AutomationUiCaseReviewProjectAccessValidator projectAccessValidator;

    @Override
    public AutomationUiCaseReviewResp getCurrent(Long sceneId, String caseId) {
        AutomationUiSceneDO scene = requireScene(sceneId, false);
        CaseDO caseDO = findCase(scene, caseId);
        ReviewRow review = findLatest(sceneId, caseId, false);
        if (review == null) {
            caseDO = requireCase(scene, caseId);
            AutomationUiCaseReviewResp empty = emptyReview(scene, caseDO, AutomationUiCaseFingerprint.compute(caseDO));
            return empty;
        }
        AutomationUiCaseFingerprint.Fingerprint current = caseDO == null
            ? null
            : AutomationUiCaseFingerprint.compute(caseDO);
        if (caseDO == null) {
            caseDO = requireRevisionCase(review, caseId);
        }
        return hydrate(review, scene, caseDO, current, true);
    }

    @Override
    public List<AutomationUiCaseReviewResp> listHistory(Long sceneId, String caseId) {
        AutomationUiSceneDO scene = requireScene(sceneId, false);
        CaseDO caseDO = findCase(scene, caseId);
        AutomationUiCaseFingerprint.Fingerprint current = caseDO == null
            ? null
            : AutomationUiCaseFingerprint.compute(caseDO);
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_case_review WHERE scene_id = ? AND case_id = ? ORDER BY round_no DESC", this::mapReview, sceneId, caseId)
            .stream()
            .map(review -> hydrate(review, scene, caseDO == null
                ? requireRevisionCase(review, caseId)
                : caseDO, current, false))
            .toList();
    }

    @Override
    public Map<String, Object> getDiff(Long sceneId, String caseId, Long reviewId) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, false);
        CaseDO target = loadRevisionCase(review.definitionRevisionId(), caseId);
        if (target == null) {
            throw new BusinessException("DEFINITION_REVISION_CASE_NOT_FOUND：评审快照中不存在目标用例");
        }
        Long baselineRevision = findPreviousApprovedRevision(sceneId, caseId, review.roundNo());
        CaseDO baseline = baselineRevision == null ? null : loadRevisionCase(baselineRevision, caseId);
        return structuredDiff(baseline, target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp submit(Long sceneId, String caseId, AutomationUiCaseReviewSubmitReq request) {
        AutomationUiSceneDO scene = requireScene(sceneId, true);
        if (!Objects.equals(normalizeVersion(scene.getDefinitionVersion()), request.getExpectedDefinitionVersion())) {
            throw new BusinessException("REVIEW_DEFINITION_VERSION_CONFLICT：用例已更新，请刷新后重新提交");
        }
        CaseDO caseDO = requireCase(scene, caseId);
        Long actor = currentUser();
        LinkedHashSet<Long> reviewers = request.getReviewerIds()
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (reviewers.isEmpty()) {
            throw new BusinessException("REVIEW_REVIEWER_REQUIRED：至少选择一名评审人");
        }
        if (reviewers.contains(actor)) {
            throw new BusinessException("REVIEW_SELF_APPROVAL_NOT_ALLOWED：提交人不能作为本轮评审人");
        }
        reviewerValidator.requireActive(reviewers);
        projectAccessValidator.requireAssignableReviewers(projectAccessValidator.requireAccess(scene
            .getProjectId()), reviewers);
        AutomationUiCaseFingerprint.Fingerprint fingerprint = AutomationUiCaseFingerprint.compute(caseDO);
        ReviewRow latest = findLatest(sceneId, caseId, true);
        requireResubmittable(latest == null ? null : latest.status(), latest == null
            ? null
            : latest.caseContentHash(), latest == null
                ? null
                : latest.hashSchemaVersion(), fingerprint, latest != null && hasBlockingFeedback(latest.id()));
        if (latest != null && INVALIDATABLE_STATUSES.contains(latest
            .status()) && !isCurrentVersion(latest, fingerprint)) {
            transition(latest, "OUTDATED", actor);
            appendEvent(latest.id(), "OUTDATED", actor, Map.of("reason", "CASE_CONTENT_OR_HASH_SCHEMA_CHANGED"));
        }
        AutomationUiDefinitionRevisionService.Revision revision = definitionRevisionService.ensure(scene);
        AutomationUiCaseReviewResp.Policy policy = loadPolicy(scene.getProjectId());
        int requiredApprovals = requireReviewerCapacity(reviewers.size(), policy.getRequiredApprovals());
        int round = queryInt("SELECT COALESCE(MAX(round_no), 0) + 1 FROM automation_ui_case_review" + " WHERE scene_id = ? AND case_id = ?", sceneId, caseId);
        Long reviewId = nextId(request);
        jdbcTemplate
            .update("INSERT INTO automation_ui_case_review (id, scene_id, case_id, definition_revision_id," + " definition_version, case_content_hash, hash_schema_version, round_no, status, submitter_id," + " submitted_at, required_approvals, summary, version, create_user, create_time, update_user, update_time)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IN_REVIEW', ?, CURRENT_TIMESTAMP(3), ?, ?, 0, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", reviewId, sceneId, caseId, revision
                .id(), scene.getDefinitionVersion(), fingerprint.hash(), fingerprint
                    .schemaVersion(), round, actor, requiredApprovals, cleanText(request
                        .getSummary(), 2000), actor, actor);
        for (Long reviewer : reviewers) {
            jdbcTemplate
                .update("INSERT INTO automation_ui_case_review_reviewer (id, review_id, reviewer_id, decision, version," + " create_user, create_time, update_user, update_time) VALUES (?, ?, ?, 'PENDING', 0, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", nextId(reviewer), reviewId, reviewer, actor, actor);
        }
        appendEvent(reviewId, "SUBMITTED", actor, Map.of("definitionVersion", scene
            .getDefinitionVersion(), "roundNo", round, "reviewerCount", reviewers.size()));
        runChecks(reviewId, sceneId, caseId, caseDO, fingerprint.hash(), fingerprint.schemaVersion(), "SUBMIT", actor);
        notifyUsers("UI 用例待评审", caseDO.getName() + " 已提交第 " + round + " 轮评审", reviewers);
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp decide(Long sceneId,
                                             String caseId,
                                             Long reviewId,
                                             AutomationUiCaseReviewDecisionReq request) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireExpectedVersion(review, request.getExpectedReviewVersion());
        requireCurrentHash(review);
        if (!"IN_REVIEW".equals(review.status())) {
            throw new BusinessException("REVIEW_STATE_INVALID：当前状态不能提交评审结论");
        }
        Long actor = currentUser();
        if ("APPROVED".equals(request.getDecision()) && Objects.equals(actor, review.submitterId())) {
            throw new BusinessException("REVIEW_SELF_APPROVAL_NOT_ALLOWED：提交人不能批准自己的评审单");
        }
        String existingDecision = findReviewerDecision(reviewId, actor);
        boolean assigned = existingDecision != null;
        if (!assigned && !StpUtil.hasPermission("automation:automationUiScene:review:admin")) {
            throw new BusinessException("REVIEW_REVIEWER_REQUIRED：当前用户不是本轮评审人");
        }
        if (assigned) {
            requirePendingReviewerDecision(existingDecision);
        }
        requireDecisionReason(reviewId, request);
        if ("APPROVED".equals(request.getDecision())) {
            requireApprovable(reviewId, review);
        }
        if (!assigned) {
            jdbcTemplate
                .update("INSERT INTO automation_ui_case_review_reviewer (id, review_id, reviewer_id, reviewer_role," + " decision, version, create_user, create_time, update_user, update_time)" + " VALUES (?, ?, ?, 'ADMIN', 'PENDING', 0, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", nextId(actor), reviewId, actor, actor, actor);
        }
        jdbcTemplate
            .update("UPDATE automation_ui_case_review_reviewer SET decision = ?, decision_summary = ?," + " decision_at = CURRENT_TIMESTAMP(3), version = version + 1, update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE review_id = ? AND reviewer_id = ?", request
                .getDecision(), cleanText(request.getComment(), 2000), actor, reviewId, actor);
        String status = decisionStatus(reviewId, review.requiredApprovals(), request.getDecision());
        int changed = jdbcTemplate
            .update("UPDATE automation_ui_case_review SET status = ?, completed_at = CASE WHEN ? = 'IN_REVIEW' THEN NULL" + " ELSE CURRENT_TIMESTAMP(3) END, version = version + 1, update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE id = ? AND version = ?", status, status, actor, reviewId, review
                .version());
        if (changed != 1) {
            throw new BusinessException("REVIEW_VERSION_CONFLICT：评审单已被其他操作更新");
        }
        Map<String, Object> decisionEvent = new LinkedHashMap<>();
        decisionEvent.put("status", status);
        if (StringUtils.isNotBlank(request.getComment())) {
            decisionEvent.put("comment", cleanText(request.getComment(), 2000));
        }
        appendEvent(reviewId, "DECISION_" + request.getDecision(), actor, decisionEvent);
        if (!"IN_REVIEW".equals(status)) {
            notifyUsers("UI 用例评审结论", "用例 " + caseId + " 的评审结论为 " + status, List.of(review.submitterId()));
        }
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp recheck(Long sceneId, String caseId, Long reviewId, Long expectedReviewVersion) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireExpectedVersion(review, expectedReviewVersion);
        requireCurrentHash(review);
        requireState(review, Set.of("IN_REVIEW"), "仅待评审状态可重新运行自动检查");
        CaseDO caseDO = requireCase(requireScene(sceneId, false), caseId);
        runChecks(reviewId, sceneId, caseId, caseDO, review.caseContentHash(), review
            .hashSchemaVersion(), "MANUAL", currentUser());
        bumpReview(review, currentUser());
        appendEvent(reviewId, "RECHECKED", currentUser(), Map.of());
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp addComment(Long sceneId,
                                                 String caseId,
                                                 Long reviewId,
                                                 AutomationUiCaseReviewCommentReq request) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireCurrentHash(review);
        requireState(review, Set.of("IN_REVIEW", "CHANGES_REQUESTED"), "仅待评审或需修改状态可新增意见");
        Long actor = currentUser();
        Long id = nextId(request);
        Long threadId = id;
        String severity = request.getSeverity();
        CommentAnchor anchor;
        if (request.getParentId() != null) {
            CommentRef parent = findComment(reviewId, request.getParentId());
            if (parent == null) {
                throw new BusinessException("REVIEW_COMMENT_NOT_FOUND：回复目标不存在");
            }
            threadId = parent.threadId();
            severity = null;
            CommentRef root = Objects.equals(parent.id(), parent.threadId())
                ? parent
                : findComment(reviewId, parent.threadId());
            if (root == null) {
                throw new BusinessException("REVIEW_COMMENT_NOT_FOUND：回复线程根意见不存在");
            }
            requireReplyableCommentThread(root.resolution());
            anchor = new CommentAnchor(root.nodeType(), root.stepId(), root.fieldPath());
        } else if (StringUtils.isBlank(severity)) {
            throw new BusinessException("REVIEW_COMMENT_SEVERITY_REQUIRED：根意见必须选择严重级别");
        } else {
            anchor = validateCommentAnchor(requireRevisionCase(review, caseId), StringUtils.defaultIfBlank(request
                .getNodeType(), "CASE"), cleanText(request.getStepId(), 128), cleanText(request.getFieldPath(), 255));
        }
        String content = requireCleanText(request.getContent(), 4000, "评审意见");
        jdbcTemplate
            .update("INSERT INTO automation_ui_case_review_comment (id, review_id, thread_id, parent_id, node_type," + " step_id, field_path, severity, resolution, content, create_user, create_time, update_user, update_time)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", id, reviewId, threadId, request
                .getParentId(), anchor.nodeType(), anchor.stepId(), anchor
                    .fieldPath(), severity, content, actor, actor);
        bumpReview(review, actor);
        appendEvent(reviewId, request.getParentId() == null ? "COMMENT_ADDED" : "COMMENT_REPLIED", actor, Map
            .of("commentId", id, "threadId", threadId));
        notifyCommentParticipants(review, threadId, actor);
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp resolveComment(Long sceneId,
                                                     String caseId,
                                                     Long reviewId,
                                                     Long commentId,
                                                     AutomationUiCaseReviewResolveReq request) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireCurrentHash(review);
        requireState(review, Set.of("IN_REVIEW", "CHANGES_REQUESTED"), "仅待评审或需修改状态可处理意见");
        CommentRef comment = findComment(reviewId, commentId);
        if (comment == null || !Objects.equals(comment.id(), comment.threadId())) {
            throw new BusinessException("REVIEW_COMMENT_NOT_FOUND：只能处理根意见");
        }
        Long actor = currentUser();
        if (!Objects.equals(actor, comment.authorId()) && !StpUtil
            .hasPermission("automation:automationUiScene:review:admin")) {
            throw new BusinessException("REVIEW_COMMENT_FORBIDDEN：只能处理本人创建的意见");
        }
        if ("WONT_FIX".equals(request.getResolutionType()) && StringUtils.isBlank(request.getReason())) {
            throw new BusinessException("REVIEW_RESOLUTION_REASON_REQUIRED：不处理必须填写理由");
        }
        boolean reopen = "REOPEN".equals(request.getResolutionType());
        requireCommentResolutionTransition(comment.resolution(), reopen);
        String resolution = reopen ? "OPEN" : "WONT_FIX".equals(request.getResolutionType()) ? "WONT_FIX" : "RESOLVED";
        jdbcTemplate
            .update("UPDATE automation_ui_case_review_comment SET resolution = ?, resolution_type = ?," + " resolved_by = ?, resolved_at = ?, resolution_reason = ?, update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE id = ?", resolution, reopen
                ? null
                : request.getResolutionType(), reopen ? null : actor, reopen
                    ? null
                    : Timestamp.valueOf(LocalDateTime.now()), reopen
                        ? null
                        : cleanText(request.getReason(), 1000), actor, commentId);
        bumpReview(review, actor);
        appendEvent(reviewId, reopen ? "COMMENT_REOPENED" : "COMMENT_RESOLVED", actor, Map
            .of("commentId", commentId, "resolution", resolution));
        notifyCommentParticipants(review, comment.threadId(), actor);
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp updateChecklist(Long sceneId,
                                                      String caseId,
                                                      Long reviewId,
                                                      AutomationUiCaseReviewChecklistReq request) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireCurrentHash(review);
        requireState(review, Set.of("IN_REVIEW"), "仅待评审状态可填写评审清单");
        Long actor = currentUser();
        String existingDecision = findReviewerDecision(reviewId, actor);
        if (existingDecision == null) {
            throw new BusinessException("REVIEW_REVIEWER_REQUIRED：只有本轮评审人可以填写清单");
        }
        requirePendingReviewerDecision(existingDecision);
        jdbcTemplate
            .update("INSERT INTO automation_ui_case_review_checklist_response" + " (id, review_id, reviewer_id, item_code, checked, checked_at, update_time)" + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE checked = VALUES(checked)," + " checked_at = VALUES(checked_at), update_time = CURRENT_TIMESTAMP(3)", nextId(request), reviewId, actor, request
                .getItemCode(), request.getChecked(), Boolean.TRUE.equals(request.getChecked())
                    ? Timestamp.valueOf(LocalDateTime.now())
                    : null);
        bumpReview(review, actor);
        appendEvent(reviewId, "CHECKLIST_UPDATED", actor, Map.of("itemCode", request.getItemCode(), "checked", request
            .getChecked()));
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp withdraw(Long sceneId, String caseId, Long reviewId, Long expectedReviewVersion) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireExpectedVersion(review, expectedReviewVersion);
        requireCurrentHash(review);
        Long actor = currentUser();
        if (!Objects.equals(actor, review.submitterId()) && !StpUtil
            .hasPermission("automation:automationUiScene:review:admin")) {
            throw new BusinessException("REVIEW_WITHDRAW_FORBIDDEN：只有提交人可以撤回评审");
        }
        if (!Set.of("IN_REVIEW", "CHANGES_REQUESTED").contains(review.status())) {
            throw new BusinessException("REVIEW_STATE_INVALID：当前状态不能撤回");
        }
        transition(review, "WITHDRAWN", actor);
        appendEvent(reviewId, "WITHDRAWN", actor, Map.of());
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewResp revokeApproval(Long sceneId,
                                                     String caseId,
                                                     Long reviewId,
                                                     Long expectedReviewVersion) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireExpectedVersion(review, expectedReviewVersion);
        requireCurrentHash(review);
        if (!Set.of("IN_REVIEW", "APPROVED").contains(review.status())) {
            throw new BusinessException("REVIEW_STATE_INVALID：只有待评审或已通过状态可以撤销批准");
        }
        Long actor = currentUser();
        int changed = jdbcTemplate
            .update("UPDATE automation_ui_case_review_reviewer SET decision = 'PENDING', decision_summary = NULL," + " decision_at = NULL, version = version + 1, update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE review_id = ? AND reviewer_id = ? AND decision = 'APPROVED'", actor, reviewId, actor);
        if (changed != 1) {
            throw new BusinessException("REVIEW_REVOKE_FORBIDDEN：只能撤销本人作出的批准");
        }
        if ("APPROVED".equals(review.status())) {
            transition(review, "IN_REVIEW", actor);
        } else {
            bumpReview(review, actor);
        }
        appendEvent(reviewId, "APPROVAL_REVOKED", actor, Map.of());
        return getCurrent(sceneId, caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remind(Long sceneId, String caseId, Long reviewId) {
        ReviewRow review = requireReview(sceneId, caseId, reviewId, true);
        requireCurrentHash(review);
        Long actor = currentUser();
        if (!Objects.equals(actor, review.submitterId()) && !StpUtil
            .hasPermission("automation:automationUiScene:review:admin")) {
            throw new BusinessException("REVIEW_REMIND_FORBIDDEN：只有提交人可以催办");
        }
        if (!"IN_REVIEW".equals(review.status())) {
            throw new BusinessException("REVIEW_STATE_INVALID：当前状态无需催办");
        }
        int recent = queryInt("SELECT COUNT(*) FROM automation_ui_case_review_event" + " WHERE review_id = ? AND event_type = 'REMINDED' AND create_time >= DATE_SUB(NOW(3), INTERVAL 24 HOUR)", reviewId);
        if (recent > 0) {
            throw new BusinessException("REVIEW_REMIND_RATE_LIMIT：24 小时内已催办过");
        }
        List<Long> pending = jdbcTemplate
            .query("SELECT reviewer_id FROM automation_ui_case_review_reviewer WHERE review_id = ? AND decision = 'PENDING'", (rs,
                                                                                                                               rowNum) -> rs
                                                                                                                                   .getLong(1), reviewId);
        appendEvent(reviewId, "REMINDED", actor, Map.of("pendingCount", pending.size()));
        notifyUsers("UI 用例评审催办", "用例 " + caseId + " 仍在等待您的评审", pending);
    }

    @Override
    public List<Map<String, Object>> getSceneSummary(Long sceneId) {
        AutomationUiSceneDO scene = requireScene(sceneId, false);
        List<Map<String, Object>> result = new ArrayList<>();
        if (scene.getCaseList() == null)
            return result;
        for (CaseDO caseDO : scene.getCaseList()) {
            if (caseDO == null)
                continue;
            AutomationUiCaseFingerprint.Fingerprint fingerprint = AutomationUiCaseFingerprint.compute(caseDO);
            ReviewRow review = findLatest(sceneId, caseDO.getId(), false);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("caseId", caseDO.getId());
            item.put("caseName", caseDO.getName());
            item.put("status", review == null ? "NOT_SUBMITTED" : effectiveStatus(review, fingerprint));
            item.put("roundNo", review == null ? null : review.roundNo());
            item.put("reviewId", review == null ? null : review.id());
            item.put("updatedAt", review == null ? null : review.updateTime());
            item.put("blockerCount", review == null
                ? 0
                : queryInt("SELECT COUNT(*) FROM automation_ui_case_review_check c" + " WHERE c.review_id = ? AND c.run_id = (SELECT cr.id FROM automation_ui_case_review_check_run cr" + " WHERE cr.review_id = ? AND cr.status = 'COMPLETED' ORDER BY cr.create_time DESC, cr.id DESC LIMIT 1)" + " AND c.effective_severity = 'BLOCKER' AND c.result = 'FAIL'", review
                    .id(), review.id()));
            result.add(item);
        }
        return result;
    }

    private AutomationUiCaseReviewResp hydrate(ReviewRow review,
                                               AutomationUiSceneDO scene,
                                               CaseDO caseDO,
                                               AutomationUiCaseFingerprint.Fingerprint current,
                                               boolean full) {
        List<AutomationUiCaseReviewResp.Reviewer> reviewers = loadReviewers(review.id());
        List<AutomationUiCaseReviewResp.Check> checks = full ? loadChecks(review.id()) : List.of();
        List<AutomationUiCaseReviewResp.Comment> comments = full ? loadComments(review.id()) : List.of();
        CheckRunState checkRun = full ? loadLatestCheckRun(review.id()) : null;
        int passed = full ? (int)checks.stream().filter(item -> "PASS".equals(item.getResult())).count() : 0;
        int blockers = full
            ? (int)checks.stream()
                .filter(item -> "BLOCKER".equals(item.getEffectiveSeverity()) && "FAIL".equals(item.getResult()))
                .count() + (int)comments.stream()
                    .filter(item -> item.getParentId() == null && "BLOCKER".equals(item.getSeverity()) && "OPEN"
                        .equals(item.getResolution()))
                    .count()
            : 0;
        int openComments = full
            ? (int)comments.stream()
                .filter(item -> item.getParentId() == null && "OPEN".equals(item.getResolution()))
                .count()
            : 0;
        int approved = (int)reviewers.stream().filter(item -> "APPROVED".equals(item.getDecision())).count();
        return AutomationUiCaseReviewResp.builder()
            .id(review.id())
            .sceneId(review.sceneId())
            .caseId(review.caseId())
            .caseName(caseDO.getName())
            .definitionRevisionId(review.definitionRevisionId())
            .definitionVersion(review.definitionVersion())
            .caseContentHash(review.caseContentHash())
            .hashSchemaVersion(review.hashSchemaVersion())
            .roundNo(review.roundNo())
            .status(effectiveStatus(review, current))
            .submitterId(review.submitterId())
            .submitterName(name(review.submitterId()))
            .submittedAt(review.submittedAt())
            .requiredApprovals(review.requiredApprovals())
            .summary(review.summary())
            .completedAt(review.completedAt())
            .version(review.version())
            .outdated("OUTDATED".equals(effectiveStatus(review, current)))
            .currentCaseContentHash(current == null ? null : current.hash())
            .checkRunStatus(checkRun == null ? null : checkRun.status())
            .policy(loadPolicy(scene.getProjectId()))
            .metrics(AutomationUiCaseReviewResp.Metrics.builder()
                .checkPassed(passed)
                .checkTotal(checks.size())
                .blockerCount(blockers)
                .openCommentCount(openComments)
                .approvedCount(approved)
                .build())
            .evidence(full
                ? loadEvidence(review.sceneId(), review.caseId(), review.caseContentHash(), review.hashSchemaVersion())
                : null)
            .reviewers(reviewers)
            .checks(checks)
            .comments(comments)
            .checklist(full ? loadChecklist(review.id()) : List.of())
            .events(full ? loadEvents(review.id()) : List.of())
            .build();
    }

    private AutomationUiCaseReviewResp emptyReview(AutomationUiSceneDO scene,
                                                   CaseDO caseDO,
                                                   AutomationUiCaseFingerprint.Fingerprint current) {
        return AutomationUiCaseReviewResp.builder()
            .sceneId(scene.getId())
            .caseId(caseDO.getId())
            .caseName(caseDO.getName())
            .definitionVersion(scene.getDefinitionVersion())
            .status("NOT_SUBMITTED")
            .currentCaseContentHash(current.hash())
            .hashSchemaVersion(current.schemaVersion())
            .checkRunStatus("NOT_RUN")
            .policy(loadPolicy(scene.getProjectId()))
            .metrics(AutomationUiCaseReviewResp.Metrics.builder()
                .checkPassed(0)
                .checkTotal(0)
                .blockerCount(0)
                .openCommentCount(0)
                .approvedCount(0)
                .build())
            .reviewers(List.of())
            .checks(List.of())
            .comments(List.of())
            .checklist(CHECKLIST.stream()
                .map(item -> AutomationUiCaseReviewResp.ChecklistItem.builder()
                    .code(item.code())
                    .label(item.label())
                    .checked(false)
                    .build())
                .toList())
            .events(List.of())
            .build();
    }

    private void runChecks(Long reviewId,
                           Long sceneId,
                           String caseId,
                           CaseDO caseDO,
                           String hash,
                           String hashSchemaVersion,
                           String trigger,
                           Long actor) {
        Long runId = nextId(caseDO);
        jdbcTemplate
            .update("INSERT INTO automation_ui_case_review_check_run (id, review_id, trigger_type, checker_version," + " policy_version, status, started_at, create_user, create_time)" + " VALUES (?, ?, ?, ?, ?, 'RUNNING', CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", runId, reviewId, trigger, AutomationUiCaseReviewChecker.CHECKER_VERSION, POLICY_VERSION, actor);
        try {
            AutomationUiCaseReviewChecker.ExecutionFacts facts = loadExecutionFacts(sceneId, caseId, hash, hashSchemaVersion);
            for (AutomationUiCaseReviewChecker.Result result : checker.check(caseDO, facts)) {
                jdbcTemplate
                    .update("INSERT INTO automation_ui_case_review_check (id, run_id, review_id, rule_code, result," + " severity, effective_severity, message, anchors_json, evidence_json, checked_at, create_user, create_time)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", nextId(result), runId, reviewId, result
                        .ruleCode(), result.result(), result.severity(), result.severity(), cleanText(result
                            .message(), 1000), boundedJson(result.anchors(), 8192), boundedJson(result
                                .evidence(), 8192), actor);
            }
            jdbcTemplate
                .update("UPDATE automation_ui_case_review_check_run SET status = 'COMPLETED', finished_at = CURRENT_TIMESTAMP(3) WHERE id = ?", runId);
        } catch (RuntimeException e) {
            jdbcTemplate
                .update("UPDATE automation_ui_case_review_check_run SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP(3)," + " error_message = ? WHERE id = ?", cleanText(e
                    .getMessage(), 1000), runId);
            appendEvent(reviewId, "CHECK_FAILED", actor, Map.of("runId", runId));
            log.warn("UI case review checker failed, reviewId={}, runId={}", reviewId, runId, e);
        }
    }

    AutomationUiCaseReviewChecker.ExecutionFacts loadExecutionFacts(Long sceneId,
                                                                    String caseId,
                                                                    String hash,
                                                                    String hashSchemaVersion) {
        List<Map<String, Object>> rows = jdbcTemplate
            .query("SELECT c.status, c.result, c.attempt_no FROM automation_ui_execution_case c" + " JOIN automation_ui_execution e ON e.id = c.execution_id WHERE e.scene_id = ? AND c.case_id = ?" + " AND c.case_content_hash = ? AND c.hash_schema_version = ? AND c.finished_at IS NOT NULL" + " ORDER BY c.finished_at DESC, c.id DESC LIMIT 10", (rs,
                                                                                                                                                                                                                                                                                                                                                         rowNum) -> Map
                                                                                                                                                                                                                                                                                                                                                             .of("status", StringUtils
                                                                                                                                                                                                                                                                                                                                                                 .defaultString(rs
                                                                                                                                                                                                                                                                                                                                                                     .getString("status")), "result", StringUtils
                                                                                                                                                                                                                                                                                                                                                                         .defaultString(rs
                                                                                                                                                                                                                                                                                                                                                                             .getString("result")), "attempt", rs
                                                                                                                                                                                                                                                                                                                                                                                 .getInt("attempt_no")), sceneId, caseId, hash, hashSchemaVersion);
        boolean success = rows.stream()
            .anyMatch(row -> isPassed(String.valueOf(row.get("status"))) || isPassed(String.valueOf(row
                .get("result"))));
        int unstable = (int)rows.stream()
            .filter(row -> Integer.parseInt(String.valueOf(row.get("attempt"))) > 1 || isFailed(String.valueOf(row
                .get("status"))) || isFailed(String.valueOf(row.get("result"))))
            .count();
        String lastResult = rows.isEmpty()
            ? null
            : StringUtils.firstNonBlank(String.valueOf(rows.get(0).get("result")), String.valueOf(rows.get(0)
                .get("status")));
        return new AutomationUiCaseReviewChecker.ExecutionFacts(success, rows.size(), unstable, lastResult);
    }

    private void requireApprovable(Long reviewId, ReviewRow review) {
        Long latestRunId = queryLong("SELECT id FROM automation_ui_case_review_check_run" + " WHERE review_id = ? ORDER BY create_time DESC, id DESC LIMIT 1", reviewId);
        if (latestRunId == null || queryInt("SELECT COUNT(*) FROM automation_ui_case_review_check_run" + " WHERE id = ? AND status = 'COMPLETED'", latestRunId) == 0) {
            throw new BusinessException("REVIEW_CHECK_NOT_COMPLETED：最新自动检查未成功完成，请重新检查后再批准");
        }
        if (queryInt("SELECT COUNT(*) FROM automation_ui_case_review_check" + " WHERE run_id = ? AND result = 'FAIL' AND effective_severity = 'BLOCKER'", latestRunId) > 0) {
            throw new BusinessException("REVIEW_BLOCKER_EXISTS：仍有自动检查阻断项");
        }
        if (queryInt("SELECT COUNT(*) FROM automation_ui_case_review_comment" + " WHERE review_id = ? AND parent_id IS NULL AND severity = 'BLOCKER' AND resolution = 'OPEN'", reviewId) > 0) {
            throw new BusinessException("REVIEW_BLOCKER_EXISTS：仍有未解决的人工阻断意见");
        }
        if (queryInt("SELECT COUNT(*) FROM automation_ui_case_review_comment" + " WHERE review_id = ? AND parent_id IS NULL AND severity = 'MAJOR' AND resolution = 'OPEN'", reviewId) > 0) {
            throw new BusinessException("REVIEW_COMMENT_OPEN：仍有未解决的重要意见");
        }
        AutomationUiCaseReviewResp.Policy policy = loadPolicy(requireScene(review.sceneId(), false).getProjectId());
        if (policy.isExecutionEvidenceRequired()) {
            AutomationUiCaseReviewResp.Evidence evidence = loadEvidence(review.sceneId(), review.caseId(), review
                .caseContentHash(), review.hashSchemaVersion());
            LocalDateTime oldestAllowed = LocalDateTime.now().minusHours(policy.getExecutionEvidenceMaxAgeHours());
            if (evidence == null || evidence.getFinishedAt() == null || evidence.getFinishedAt()
                .isBefore(oldestAllowed)) {
                throw new BusinessException("REVIEW_EXECUTION_EVIDENCE_REQUIRED：当前版本缺少时效内的成功执行证据");
            }
        }
    }

    void requireDecisionReason(Long reviewId, AutomationUiCaseReviewDecisionReq request) {
        String decision = request.getDecision();
        String comment = cleanText(request.getComment(), 2000);
        if ("REJECTED".equals(decision) && StringUtils.isBlank(comment)) {
            throw new BusinessException("REVIEW_REJECTION_REASON_REQUIRED：拒绝评审必须填写原因");
        }
        if (!"CHANGES_REQUESTED".equals(decision) || StringUtils.isNotBlank(comment)) {
            return;
        }
        int openIssues = queryInt("SELECT COUNT(*) FROM automation_ui_case_review_comment" + " WHERE review_id = ? AND parent_id IS NULL AND severity IN ('BLOCKER','MAJOR') AND resolution = 'OPEN'", reviewId);
        if (openIssues == 0) {
            throw new BusinessException("REVIEW_CHANGE_REASON_REQUIRED：要求修改必须填写说明或先创建阻断/重要意见");
        }
    }

    static int requireReviewerCapacity(int reviewerCount, Integer configuredApprovals) {
        int requiredApprovals = Math.max(1, configuredApprovals == null ? 1 : configuredApprovals);
        if (reviewerCount < requiredApprovals) {
            throw new BusinessException("REVIEW_REVIEWER_INSUFFICIENT：当前项目至少需要 " + requiredApprovals + " 名评审人");
        }
        return requiredApprovals;
    }

    static void requirePendingReviewerDecision(String decision) {
        if (!"PENDING".equals(decision)) {
            throw new BusinessException("REVIEW_DECISION_ALREADY_SUBMITTED：已提交结论；如需修改批准，请先撤销批准");
        }
    }

    static void requireCommentResolutionTransition(String currentResolution, boolean reopen) {
        if (reopen && "OPEN".equals(currentResolution)) {
            throw new BusinessException("REVIEW_COMMENT_STATE_INVALID：意见已经处于打开状态");
        }
        if (!reopen && !"OPEN".equals(currentResolution)) {
            throw new BusinessException("REVIEW_COMMENT_STATE_INVALID：意见已经处理，不能重复处理");
        }
    }

    static void requireReplyableCommentThread(String resolution) {
        if (!"OPEN".equals(resolution)) {
            throw new BusinessException("REVIEW_COMMENT_STATE_INVALID：已处理意见需重新打开后才能回复");
        }
    }

    static void requireResubmittable(String status,
                                     String previousHash,
                                     String previousSchemaVersion,
                                     AutomationUiCaseFingerprint.Fingerprint current,
                                     boolean hasBlockingFeedback) {
        boolean sameContent = current != null && Objects.equals(previousHash, current.hash()) && Objects
            .equals(previousSchemaVersion, current.schemaVersion());
        if ("IN_REVIEW".equals(status) && sameContent) {
            throw new BusinessException("REVIEW_ALREADY_IN_PROGRESS：当前用例已有待评审轮次");
        }
        if (!sameContent) {
            return;
        }
        if ("APPROVED".equals(status)) {
            throw new BusinessException("REVIEW_ALREADY_APPROVED：当前用例版本已通过评审，无需重复提交");
        }
        if (hasBlockingFeedback || Set.of("CHANGES_REQUESTED", "REJECTED").contains(status)) {
            throw new BusinessException("REVIEW_CONTENT_UNCHANGED：请先修改用例内容，再提交新一轮评审");
        }
    }

    private boolean hasBlockingFeedback(Long reviewId) {
        return queryInt("SELECT (EXISTS (SELECT 1 FROM automation_ui_case_review_reviewer" + " WHERE review_id = ? AND decision IN ('CHANGES_REQUESTED','REJECTED'))" + " OR EXISTS (SELECT 1 FROM automation_ui_case_review_comment WHERE review_id = ?" + " AND parent_id IS NULL AND severity IN ('BLOCKER','MAJOR') AND resolution = 'OPEN'))", reviewId, reviewId) > 0;
    }

    static CommentAnchor validateCommentAnchor(CaseDO caseDO, String nodeType, String stepId, String fieldPath) {
        String resolvedNodeType = StringUtils.defaultIfBlank(nodeType, "CASE");
        String resolvedStepId = StringUtils.trimToNull(stepId);
        String resolvedFieldPath = StringUtils.trimToNull(fieldPath);
        if ("STEP".equals(resolvedNodeType)) {
            if (resolvedStepId == null) {
                throw new BusinessException("REVIEW_COMMENT_STEP_REQUIRED：步骤意见必须选择锚定步骤");
            }
            boolean exists = caseDO != null && caseDO.getStepList() != null && caseDO.getStepList()
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(step -> Objects.equals(resolvedStepId, step.getId()));
            if (!exists) {
                throw new BusinessException("REVIEW_COMMENT_STEP_NOT_FOUND：评审快照中不存在锚定步骤");
            }
            if (resolvedFieldPath != null && !STEP_FIELD_PATHS.contains(resolvedFieldPath)) {
                throw new BusinessException("REVIEW_COMMENT_FIELD_INVALID：步骤字段锚点不受支持");
            }
            return new CommentAnchor("STEP", resolvedStepId, resolvedFieldPath);
        }
        if (!"CASE".equals(resolvedNodeType)) {
            throw new BusinessException("REVIEW_COMMENT_NODE_INVALID：意见锚点类型不受支持");
        }
        if (resolvedFieldPath != null && !CASE_FIELD_PATHS.contains(resolvedFieldPath)) {
            throw new BusinessException("REVIEW_COMMENT_FIELD_INVALID：用例字段锚点不受支持");
        }
        return new CommentAnchor("CASE", null, resolvedFieldPath);
    }

    Long findPreviousApprovedRevision(Long sceneId, String caseId, int roundNo) {
        return queryLong("SELECT r.definition_revision_id FROM automation_ui_case_review r" + " WHERE r.scene_id = ? AND r.case_id = ? AND r.round_no < ?" + " AND (SELECT COUNT(*) FROM automation_ui_case_review_reviewer rr" + " WHERE rr.review_id = r.id AND rr.decision = 'APPROVED') >= r.required_approvals" + " ORDER BY r.round_no DESC LIMIT 1", sceneId, caseId, roundNo);
    }

    private void requireState(ReviewRow review, Set<String> allowed, String message) {
        if (!allowed.contains(review.status())) {
            throw new BusinessException("REVIEW_STATE_INVALID：" + message);
        }
    }

    private String decisionStatus(Long reviewId, int required, String decision) {
        if ("CHANGES_REQUESTED".equals(decision))
            return "CHANGES_REQUESTED";
        if ("REJECTED".equals(decision))
            return "REJECTED";
        int approved = queryInt("SELECT COUNT(*) FROM automation_ui_case_review_reviewer" + " WHERE review_id = ? AND decision = 'APPROVED'", reviewId);
        return approved >= required ? "APPROVED" : "IN_REVIEW";
    }

    private void requireCurrentHash(ReviewRow review) {
        AutomationUiSceneDO scene = requireScene(review.sceneId(), false);
        CaseDO currentCase = findCase(scene, review.caseId());
        AutomationUiCaseFingerprint.Fingerprint current = currentCase == null
            ? null
            : AutomationUiCaseFingerprint.compute(currentCase);
        if (current == null || !isCurrentVersion(review, current)) {
            throw new BusinessException("REVIEW_OUTDATED：用例已更新，旧评审不能继续批准");
        }
    }

    private boolean isCurrentVersion(ReviewRow review, AutomationUiCaseFingerprint.Fingerprint current) {
        return current != null && Objects.equals(current.hash(), review.caseContentHash()) && Objects.equals(current
            .schemaVersion(), review.hashSchemaVersion());
    }

    private String effectiveStatus(ReviewRow review, AutomationUiCaseFingerprint.Fingerprint current) {
        if ("OUTDATED".equals(review.status())) {
            return "OUTDATED";
        }
        return INVALIDATABLE_STATUSES.contains(review.status()) && !isCurrentVersion(review, current)
            ? "OUTDATED"
            : review.status();
    }

    private void transition(ReviewRow review, String status, Long actor) {
        int changed = jdbcTemplate
            .update("UPDATE automation_ui_case_review SET status = ?, completed_at = CASE WHEN ? = 'IN_REVIEW' THEN NULL" + " ELSE CURRENT_TIMESTAMP(3) END, version = version + 1, update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE id = ? AND version = ?", status, status, actor, review
                .id(), review.version());
        if (changed != 1)
            throw new BusinessException("REVIEW_VERSION_CONFLICT：评审单已被其他操作更新");
    }

    private void bumpReview(ReviewRow review, Long actor) {
        int changed = jdbcTemplate
            .update("UPDATE automation_ui_case_review SET version = version + 1, update_user = ?," + " update_time = CURRENT_TIMESTAMP(3) WHERE id = ? AND version = ?", actor, review
                .id(), review.version());
        if (changed != 1)
            throw new BusinessException("REVIEW_VERSION_CONFLICT：评审单已被其他操作更新");
    }

    private void requireExpectedVersion(ReviewRow review, Long expected) {
        if (!Objects.equals(review.version(), expected))
            throw new BusinessException("REVIEW_VERSION_CONFLICT：评审单已被其他操作更新");
    }

    private List<AutomationUiCaseReviewResp.Reviewer> loadReviewers(Long reviewId) {
        return jdbcTemplate
            .query("SELECT reviewer_id, reviewer_role, decision, decision_summary, decision_at" + " FROM automation_ui_case_review_reviewer WHERE review_id = ? ORDER BY create_time, id", (rs,
                                                                                                                                                                                            rowNum) -> AutomationUiCaseReviewResp.Reviewer
                                                                                                                                                                                                .builder()
                                                                                                                                                                                                .id(rs
                                                                                                                                                                                                    .getLong("reviewer_id"))
                                                                                                                                                                                                .name(name(rs
                                                                                                                                                                                                    .getLong("reviewer_id")))
                                                                                                                                                                                                .role(rs
                                                                                                                                                                                                    .getString("reviewer_role"))
                                                                                                                                                                                                .decision(rs
                                                                                                                                                                                                    .getString("decision"))
                                                                                                                                                                                                .summary(rs
                                                                                                                                                                                                    .getString("decision_summary"))
                                                                                                                                                                                                .decisionAt(dateTime(rs, "decision_at"))
                                                                                                                                                                                                .build(), reviewId);
    }

    private List<AutomationUiCaseReviewResp.Check> loadChecks(Long reviewId) {
        Long runId = queryLong("SELECT id FROM automation_ui_case_review_check_run" + " WHERE review_id = ? AND status = 'COMPLETED' ORDER BY create_time DESC, id DESC LIMIT 1", reviewId);
        if (runId == null)
            return List.of();
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_case_review_check WHERE run_id = ? ORDER BY" + " FIELD(effective_severity, 'BLOCKER', 'MAJOR', 'MINOR', 'SUGGESTION'), rule_code", (rs,
                                                                                                                                                                                    rowNum) -> AutomationUiCaseReviewResp.Check
                                                                                                                                                                                        .builder()
                                                                                                                                                                                        .id(rs
                                                                                                                                                                                            .getLong("id"))
                                                                                                                                                                                        .ruleCode(rs
                                                                                                                                                                                            .getString("rule_code"))
                                                                                                                                                                                        .result(rs
                                                                                                                                                                                            .getString("result"))
                                                                                                                                                                                        .severity(rs
                                                                                                                                                                                            .getString("severity"))
                                                                                                                                                                                        .effectiveSeverity(rs
                                                                                                                                                                                            .getString("effective_severity"))
                                                                                                                                                                                        .message(rs
                                                                                                                                                                                            .getString("message"))
                                                                                                                                                                                        .anchors(parseList(rs
                                                                                                                                                                                            .getString("anchors_json")))
                                                                                                                                                                                        .evidence(parseMap(rs
                                                                                                                                                                                            .getString("evidence_json")))
                                                                                                                                                                                        .checkedAt(dateTime(rs, "checked_at"))
                                                                                                                                                                                        .build(), runId);
    }

    private CheckRunState loadLatestCheckRun(Long reviewId) {
        return jdbcTemplate
            .query("SELECT status FROM automation_ui_case_review_check_run" + " WHERE review_id = ? ORDER BY create_time DESC, id DESC LIMIT 1", (rs,
                                                                                                                                                  rowNum) -> new CheckRunState(rs
                                                                                                                                                      .getString("status")), reviewId)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private List<AutomationUiCaseReviewResp.Comment> loadComments(Long reviewId) {
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_case_review_comment WHERE review_id = ? ORDER BY thread_id, create_time, id", (rs,
                                                                                                                               rowNum) -> AutomationUiCaseReviewResp.Comment
                                                                                                                                   .builder()
                                                                                                                                   .id(rs
                                                                                                                                       .getLong("id"))
                                                                                                                                   .threadId(rs
                                                                                                                                       .getLong("thread_id"))
                                                                                                                                   .parentId(nullableLong(rs, "parent_id"))
                                                                                                                                   .nodeType(rs
                                                                                                                                       .getString("node_type"))
                                                                                                                                   .stepId(rs
                                                                                                                                       .getString("step_id"))
                                                                                                                                   .fieldPath(rs
                                                                                                                                       .getString("field_path"))
                                                                                                                                   .severity(rs
                                                                                                                                       .getString("severity"))
                                                                                                                                   .resolution(rs
                                                                                                                                       .getString("resolution"))
                                                                                                                                   .resolutionType(rs
                                                                                                                                       .getString("resolution_type"))
                                                                                                                                   .content(rs
                                                                                                                                       .getString("content"))
                                                                                                                                   .authorId(rs
                                                                                                                                       .getLong("create_user"))
                                                                                                                                   .authorName(name(rs
                                                                                                                                       .getLong("create_user")))
                                                                                                                                   .createTime(dateTime(rs, "create_time"))
                                                                                                                                   .resolvedBy(nullableLong(rs, "resolved_by"))
                                                                                                                                   .resolvedAt(dateTime(rs, "resolved_at"))
                                                                                                                                   .resolutionReason(rs
                                                                                                                                       .getString("resolution_reason"))
                                                                                                                                   .build(), reviewId);
    }

    private List<AutomationUiCaseReviewResp.ChecklistItem> loadChecklist(Long reviewId) {
        Long actor = UserContextHolder.getUserId();
        Map<String, ChecklistState> states = actor == null
            ? Map.of()
            : jdbcTemplate
                .query("SELECT item_code, checked, checked_at FROM automation_ui_case_review_checklist_response" + " WHERE review_id = ? AND reviewer_id = ?", (rs,
                                                                                                                                                                rowNum) -> new ChecklistEntry(rs
                                                                                                                                                                    .getString("item_code"), new ChecklistState(rs
                                                                                                                                                                        .getBoolean("checked"), dateTime(rs, "checked_at"))), reviewId, actor)
                .stream()
                .collect(Collectors.toMap(ChecklistEntry::code, ChecklistEntry::state));
        Map<String, ChecklistState> finalStates = states;
        return CHECKLIST.stream().map(item -> {
            ChecklistState state = finalStates.get(item.code());
            return AutomationUiCaseReviewResp.ChecklistItem.builder()
                .code(item.code())
                .label(item.label())
                .checked(state != null && state.checked())
                .checkedAt(state == null ? null : state.checkedAt())
                .build();
        }).toList();
    }

    private List<AutomationUiCaseReviewResp.Event> loadEvents(Long reviewId) {
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_case_review_event WHERE review_id = ? ORDER BY create_time DESC, id DESC", (rs,
                                                                                                                            rowNum) -> AutomationUiCaseReviewResp.Event
                                                                                                                                .builder()
                                                                                                                                .id(rs
                                                                                                                                    .getLong("id"))
                                                                                                                                .type(rs
                                                                                                                                    .getString("event_type"))
                                                                                                                                .actorId(rs
                                                                                                                                    .getLong("actor_id"))
                                                                                                                                .actorName(name(rs
                                                                                                                                    .getLong("actor_id")))
                                                                                                                                .payload(parseMap(rs
                                                                                                                                    .getString("payload_json")))
                                                                                                                                .createTime(dateTime(rs, "create_time"))
                                                                                                                                .build(), reviewId);
    }

    private AutomationUiCaseReviewResp.Evidence loadEvidence(Long sceneId,
                                                             String caseId,
                                                             String hash,
                                                             String hashSchemaVersion) {
        return jdbcTemplate
            .query("SELECT e.id, e.trigger_type, e.project_environment_name, c.status, c.result, c.finished_at," + " c.duration_ms, e.test_report_url FROM automation_ui_execution_case c" + " JOIN automation_ui_execution e ON e.id = c.execution_id WHERE e.scene_id = ? AND c.case_id = ?" + " AND c.case_content_hash = ? AND c.hash_schema_version = ? AND c.finished_at IS NOT NULL" + " AND (LOWER(c.status) IN ('passed','pass','success','successful')" + " OR LOWER(c.result) IN ('passed','pass','success','successful'))" + " ORDER BY c.finished_at DESC, c.id DESC LIMIT 1", (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             rowNum) -> AutomationUiCaseReviewResp.Evidence
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .builder()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .executionId(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     .getLong("id"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .triggerType(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     .getString("trigger_type"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .environmentName(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     .getString("project_environment_name"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .result(StringUtils
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     .firstNonBlank(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         .getString("result"), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             .getString("status")))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .finishedAt(dateTime(rs, "finished_at"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .durationMs(nullableLong(rs, "duration_ms"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .reportUrl(safeReportUrl(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     .getString("test_report_url")))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .exactVersion(true)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .build(), sceneId, caseId, hash, hashSchemaVersion)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private AutomationUiCaseReviewResp.Policy loadPolicy(Long projectId) {
        if (projectId == null)
            return defaultPolicy();
        return jdbcTemplate
            .query("SELECT mode, required_approvals, execution_evidence_required, execution_evidence_max_age_h," + " review_sla_hours FROM automation_ui_case_review_policy WHERE project_id = ?", (rs,
                                                                                                                                                                                                    rowNum) -> AutomationUiCaseReviewResp.Policy
                                                                                                                                                                                                        .builder()
                                                                                                                                                                                                        .mode(rs
                                                                                                                                                                                                            .getString("mode"))
                                                                                                                                                                                                        .requiredApprovals(rs
                                                                                                                                                                                                            .getInt("required_approvals"))
                                                                                                                                                                                                        .executionEvidenceRequired(rs
                                                                                                                                                                                                            .getBoolean("execution_evidence_required"))
                                                                                                                                                                                                        .executionEvidenceMaxAgeHours(rs
                                                                                                                                                                                                            .getInt("execution_evidence_max_age_h"))
                                                                                                                                                                                                        .reviewSlaHours(rs
                                                                                                                                                                                                            .getInt("review_sla_hours"))
                                                                                                                                                                                                        .build(), projectId)
            .stream()
            .findFirst()
            .orElseGet(this::defaultPolicy);
    }

    private AutomationUiCaseReviewResp.Policy defaultPolicy() {
        return AutomationUiCaseReviewResp.Policy.builder()
            .mode("OBSERVE")
            .requiredApprovals(1)
            .executionEvidenceRequired(false)
            .executionEvidenceMaxAgeHours(168)
            .reviewSlaHours(48)
            .build();
    }

    private Map<String, Object> structuredDiff(CaseDO baseline, CaseDO target) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (target == null)
            return Map.of("added", 0, "modified", 0, "deleted", 1, "changes", List.of(Map.of("type", "CASE_DELETED")));
        compareField(changes, "name", baseline == null ? null : baseline.getName(), target.getName());
        compareField(changes, "remark", baseline == null ? null : baseline.getRemark(), target.getRemark());
        compareField(changes, "status", baseline == null ? null : baseline.getStatus(), target.getStatus());
        compareField(changes, "executionConfig", baseline == null ? null : baseline.getExecutionConfig(), target
            .getExecutionConfig());
        List<StepDO> beforeSteps = baseline == null || baseline.getStepList() == null
            ? List.of()
            : baseline.getStepList();
        List<StepDO> afterSteps = target.getStepList() == null ? List.of() : target.getStepList();
        Map<String, StepDO> before = beforeSteps.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(StepDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, StepDO> after = afterSteps.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(StepDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (StepDO step : afterSteps) {
            if (step == null)
                continue;
            StepDO old = before.get(step.getId());
            if (old == null) {
                changes.add(change("STEP_ADDED", step.getId(), step.getName(), null));
                continue;
            }
            int oldIndex = beforeSteps.indexOf(old);
            int newIndex = afterSteps.indexOf(step);
            if (oldIndex != newIndex)
                changes.add(change("STEP_MOVED", step.getId(), step.getName(), Map
                    .of("from", oldIndex + 1, "to", newIndex + 1)));
            List<String> fields = new ArrayList<>();
            if (!Objects.equals(old.getName(), step.getName()))
                fields.add("name");
            if (!Objects.equals(old.getRemark(), step.getRemark()))
                fields.add("remark");
            if (!Objects.equals(old.getStatus(), step.getStatus()))
                fields.add("status");
            if (!Objects.equals(old.getOperationType(), step.getOperationType()) || !Objects.equals(old
                .getOperationName(), step.getOperationName()))
                fields.add("operation");
            if (!Objects.equals(configSummary(old), configSummary(step)))
                fields.add("configList");
            if (!fields.isEmpty())
                changes.add(change("STEP_MODIFIED", step.getId(), step.getName(), Map.of("fields", fields)));
        }
        before.values()
            .stream()
            .filter(step -> !after.containsKey(step.getId()))
            .forEach(step -> changes.add(change("STEP_DELETED", step.getId(), step.getName(), null)));
        int added = (int)changes.stream().filter(item -> String.valueOf(item.get("type")).contains("ADDED")).count();
        int deleted = (int)changes.stream()
            .filter(item -> String.valueOf(item.get("type")).contains("DELETED"))
            .count();
        int modified = changes.size() - added - deleted;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseline", baseline == null ? "EMPTY" : "PREVIOUS_APPROVED");
        result.put("added", added);
        result.put("modified", modified);
        result.put("deleted", deleted);
        result.put("changes", changes);
        return result;
    }

    private void compareField(List<Map<String, Object>> changes, String field, Object before, Object after) {
        if (!Objects.equals(JSONUtil.toJsonStr(before), JSONUtil.toJsonStr(after)))
            changes.add(change("CASE_FIELD_MODIFIED", null, null, Map.of("field", field)));
    }

    private Map<String, Object> change(String type, String stepId, String stepName, Map<String, Object> detail) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", type);
        if (stepId != null)
            value.put("stepId", stepId);
        if (stepName != null)
            value.put("stepName", stepName);
        if (detail != null)
            value.putAll(detail);
        return value;
    }

    private List<String> configSummary(StepDO step) {
        if (step.getConfigList() == null)
            return List.of();
        return step.getConfigList()
            .stream()
            .filter(Objects::nonNull)
            .map(item -> item.getParamsName() + ":" + safeConfigDigest(item.getParamsName(), item.getParamsValue()))
            .sorted()
            .toList();
    }

    private String safeConfigDigest(String name, String value) {
        if ("value".equals(name) || "operationValue".equals(name))
            return "present=" + StringUtils.isNotBlank(value);
        if (StringUtils.defaultString(value).startsWith("data:image/"))
            return "inline-image";
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(StringUtils.defaultString(value));
    }

    private CaseDO loadRevisionCase(Long revisionId, String caseId) {
        String json = jdbcTemplate
            .query("SELECT definition_json FROM automation_ui_scene_definition_revision WHERE id = ?", (rs,
                                                                                                        rowNum) -> rs
                                                                                                            .getString(1), revisionId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("DEFINITION_REVISION_NOT_FOUND：评审快照不存在"));
        try {
            return AutomationUiDefinitionSnapshotMapper.readCases(objectMapper, json)
                .stream()
                .filter(item -> Objects.equals(caseId, item.getId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            throw new BusinessException("DEFINITION_REVISION_INVALID：评审快照无法解析");
        }
    }

    private CaseDO requireRevisionCase(ReviewRow review, String caseId) {
        CaseDO caseDO = loadRevisionCase(review.definitionRevisionId(), caseId);
        if (caseDO == null) {
            throw new BusinessException("DEFINITION_REVISION_CASE_NOT_FOUND：评审快照中不存在目标用例");
        }
        return caseDO;
    }

    private AutomationUiSceneDO requireScene(Long sceneId, boolean lock) {
        if (lock && sceneMapper.selectByIdForUpdate(sceneId) == null)
            throw new BusinessException("SCENE_NOT_FOUND：场景不存在");
        AutomationUiSceneDO scene = sceneMapper.selectById(sceneId);
        if (scene == null)
            throw new BusinessException("SCENE_NOT_FOUND：场景不存在");
        projectAccessValidator.requireAccess(scene.getProjectId());
        return scene;
    }

    private CaseDO requireCase(AutomationUiSceneDO scene, String caseId) {
        CaseDO result = findCase(scene, caseId);
        if (result == null)
            throw new BusinessException("CASE_NOT_FOUND：用例不存在");
        return result;
    }

    private CaseDO findCase(AutomationUiSceneDO scene, String caseId) {
        if (scene.getCaseList() == null)
            return null;
        return scene.getCaseList()
            .stream()
            .filter(Objects::nonNull)
            .filter(item -> Objects.equals(caseId, item.getId()))
            .findFirst()
            .orElse(null);
    }

    private ReviewRow findLatest(Long sceneId, String caseId, boolean lock) {
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_case_review WHERE scene_id = ? AND case_id = ?" + " ORDER BY round_no DESC LIMIT 1" + (lock
                ? " FOR UPDATE"
                : ""), this::mapReview, sceneId, caseId)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private ReviewRow requireReview(Long sceneId, String caseId, Long reviewId, boolean lock) {
        requireScene(sceneId, false);
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_case_review WHERE id = ? AND scene_id = ? AND case_id = ?" + (lock
                ? " FOR UPDATE"
                : ""), this::mapReview, reviewId, sceneId, caseId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("REVIEW_NOT_FOUND：评审单不存在"));
    }

    private ReviewRow mapReview(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewRow(rs.getLong("id"), rs.getLong("scene_id"), rs.getString("case_id"), rs
            .getLong("definition_revision_id"), rs.getLong("definition_version"), rs.getString("case_content_hash"), rs
                .getString("hash_schema_version"), rs.getInt("round_no"), rs.getString("status"), rs
                    .getLong("submitter_id"), dateTime(rs, "submitted_at"), rs.getInt("required_approvals"), rs
                        .getString("summary"), dateTime(rs, "completed_at"), rs
                            .getLong("version"), dateTime(rs, "update_time"));
    }

    private CommentRef findComment(Long reviewId, Long commentId) {
        return jdbcTemplate
            .query("SELECT id, thread_id, create_user, node_type, step_id, field_path, resolution" + " FROM automation_ui_case_review_comment WHERE review_id = ? AND id = ?", (rs,
                                                                                                                                                                                rowNum) -> new CommentRef(rs
                                                                                                                                                                                    .getLong("id"), rs
                                                                                                                                                                                        .getLong("thread_id"), rs
                                                                                                                                                                                            .getLong("create_user"), rs
                                                                                                                                                                                                .getString("node_type"), rs
                                                                                                                                                                                                    .getString("step_id"), rs
                                                                                                                                                                                                        .getString("field_path"), rs
                                                                                                                                                                                                            .getString("resolution")), reviewId, commentId)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private String findReviewerDecision(Long reviewId, Long reviewerId) {
        return jdbcTemplate
            .query("SELECT decision FROM automation_ui_case_review_reviewer" + " WHERE review_id = ? AND reviewer_id = ?", (rs,
                                                                                                                            rowNum) -> rs
                                                                                                                                .getString(1), reviewId, reviewerId)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private void appendEvent(Long reviewId, String type, Long actor, Map<String, Object> payload) {
        jdbcTemplate
            .update("INSERT INTO automation_ui_case_review_event (id, review_id, event_type, actor_id, payload_json, create_time)" + " VALUES (?, ?, ?, ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))", nextId(payload), reviewId, type, actor, boundedJson(payload, 8192));
    }

    private void notifyCommentParticipants(ReviewRow review, Long threadId, Long actor) {
        Set<Long> users = new LinkedHashSet<>();
        users.add(review.submitterId());
        users.addAll(jdbcTemplate
            .query("SELECT DISTINCT create_user FROM automation_ui_case_review_comment WHERE review_id = ? AND thread_id = ?", (rs,
                                                                                                                                rowNum) -> rs
                                                                                                                                    .getLong(1), review
                                                                                                                                        .id(), threadId));
        users.remove(actor);
        notifyUsers("UI 用例评审意见更新", "用例 " + review.caseId() + " 的评审意见有更新", users);
    }

    private void notifyUsers(String title, String content, Collection<Long> users) {
        List<Long> recipients = users == null ? List.of() : users.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty())
            return;
        try {
            MessageReq request = new MessageReq();
            request.setTitle(cleanText(title, 50));
            request.setContent(cleanText(content, 255));
            request.setType(MessageTypeEnum.SECURITY);
            messageService.add(request, recipients);
        } catch (RuntimeException e) {
            log.warn("Failed to send case review notification, recipients={}", recipients, e);
        }
    }

    private String requireCleanText(String text, int maxLength, String label) {
        String cleaned = cleanText(text, maxLength);
        if (StringUtils.isBlank(cleaned))
            throw new BusinessException(label + "不能为空");
        return cleaned;
    }

    private String cleanText(String text, int maxLength) {
        if (text == null)
            return null;
        String cleaned = text.replaceAll("(?is)<[^>]*>", "").replaceAll("(?i)javascript\\s*:", "").trim();
        return StringUtils.abbreviate(cleaned, maxLength);
    }

    private String boundedJson(Object value, int maxBytes) {
        String json = JSONUtil.toJsonStr(value == null ? Map.of() : value);
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBytes
            ? json
            : "{\"truncated\":true}";
    }

    private Map<String, Object> parseMap(String json) {
        if (StringUtils.isBlank(json))
            return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        if (StringUtils.isBlank(json))
            return List.of();
        try {
            return objectMapper.readValue(json, LIST_MAP_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Long currentUser() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null)
            throw new BusinessException("REVIEW_USER_REQUIRED：无法识别当前用户");
        return userId;
    }

    private String name(Long userId) {
        return userId == null
            ? null
            : StringUtils.defaultIfBlank(UserContextHolder.getNickname(userId), String.valueOf(userId));
    }

    private Long nextId(Object source) {
        return identifierGenerator.nextId(source).longValue();
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> values = jdbcTemplate.query(sql, (rs, rowNum) -> nullableLong(rs, 1), args);
        return values.isEmpty() ? null : values.get(0);
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long normalizeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private boolean isPassed(String value) {
        return Set.of("passed", "pass", "success", "successful")
            .contains(StringUtils.defaultString(value).toLowerCase());
    }

    private boolean isFailed(String value) {
        return Set.of("failed", "failure", "error", "blocked").contains(StringUtils.defaultString(value).toLowerCase());
    }

    private LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number)value).longValue();
    }

    private Long nullableLong(ResultSet rs, int column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number)value).longValue();
    }

    private record ReviewRow(Long id, Long sceneId, String caseId, Long definitionRevisionId, Long definitionVersion,
                             String caseContentHash, String hashSchemaVersion, int roundNo, String status,
                             Long submitterId, LocalDateTime submittedAt, int requiredApprovals, String summary,
                             LocalDateTime completedAt, Long version, LocalDateTime updateTime) {
    }

    private record CommentRef(Long id, Long threadId, Long authorId, String nodeType, String stepId, String fieldPath,
                              String resolution) {
    }

    record CommentAnchor(String nodeType, String stepId, String fieldPath) {
    }

    private record ChecklistDefinition(String code, String label) {
    }

    private record ChecklistState(boolean checked, LocalDateTime checkedAt) {
    }

    private record ChecklistEntry(String code, ChecklistState state) {
    }

    static String safeReportUrl(String value) {
        String url = StringUtils.trimToNull(value);
        if (url == null) {
            return null;
        }
        boolean applicationPath = url.startsWith("/") && !url.startsWith("//") && !url.startsWith("/\\");
        return applicationPath || url.matches("(?i)^https?://.+") ? url : null;
    }

    private record CheckRunState(String status) {
    }
}
