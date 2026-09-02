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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.AutomationUiCaseFingerprint;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewBatchAssignReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewPolicyReq;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewMetricsResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewPolicyResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewQueueResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewReviewerOptionResp;
import top.continew.admin.automation.service.AutomationUiCaseReviewGovernanceService;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.system.enums.MessageTypeEnum;
import top.continew.admin.system.model.req.MessageReq;
import top.continew.admin.system.service.MessageService;
import top.continew.starter.core.exception.BusinessException;

/** Project policy, personal queues, metrics, and bulk reviewer assignment. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationUiCaseReviewGovernanceServiceImpl implements AutomationUiCaseReviewGovernanceService {

    private static final int DEFAULT_REVIEW_SLA_HOURS = 48;
    private static final Set<String> INVALIDATABLE_STATUSES = Set.of("IN_REVIEW", "CHANGES_REQUESTED", "APPROVED");
    static final String REVIEW_APPROVED_PREDICATE = "(SELECT COUNT(*) FROM automation_ui_case_review_reviewer ar" + " WHERE ar.review_id = r.id AND ar.decision = 'APPROVED') >= r.required_approvals";
    static final String REVIEW_COMPLETION_PREDICATE = "(r.completed_at IS NOT NULL AND (" + REVIEW_APPROVED_PREDICATE + " OR EXISTS (SELECT 1 FROM automation_ui_case_review_reviewer dr WHERE dr.review_id = r.id" + " AND dr.decision IN ('CHANGES_REQUESTED','REJECTED'))))";

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final AutomationUiSceneMapper sceneMapper;
    private final MessageService messageService;
    private final AutomationUiCaseReviewReviewerValidator reviewerValidator;
    private final AutomationUiCaseReviewProjectAccessValidator projectAccessValidator;

    @Override
    public AutomationUiCaseReviewPolicyResp getPolicy(Long projectId) {
        projectAccessValidator.requireAccess(projectId);
        PolicyRow row = findPolicy(projectId, false);
        return row == null ? defaultPolicy(projectId) : toPolicy(row);
    }

    @Override
    public List<AutomationUiCaseReviewReviewerOptionResp> listEligibleReviewers(Long projectId) {
        ProjectConfigDO project = projectAccessValidator.requireAccess(projectId);
        Set<Long> eligible = projectAccessValidator.eligibleReviewerIds(project);
        if (eligible.isEmpty()) {
            return List.of();
        }
        String placeholders = eligible.stream().map(ignored -> "?").collect(Collectors.joining(","));
        return jdbcTemplate
            .query("SELECT id, nickname FROM sys_user WHERE status = 1 AND id IN (" + placeholders + ")" + " ORDER BY nickname, id", (rs,
                                                                                                                                      rowNum) -> AutomationUiCaseReviewReviewerOptionResp
                                                                                                                                          .builder()
                                                                                                                                          .id(rs
                                                                                                                                              .getLong("id"))
                                                                                                                                          .name(rs
                                                                                                                                              .getString("nickname"))
                                                                                                                                          .build(), eligible
                                                                                                                                              .toArray());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewPolicyResp updatePolicy(Long projectId, AutomationUiCaseReviewPolicyReq request) {
        projectAccessValidator.requireAccess(projectId);
        Long actor = currentUser();
        PolicyRow current = findPolicy(projectId, true);
        long currentVersion = current == null ? 0L : current.version();
        if (!Objects.equals(currentVersion, request.getExpectedVersion())) {
            throw new BusinessException("REVIEW_POLICY_VERSION_CONFLICT：项目评审策略已更新，请刷新后重试");
        }
        if (current == null) {
            jdbcTemplate
                .update("INSERT INTO automation_ui_case_review_policy (project_id, mode, required_approvals," + " execution_evidence_required, execution_evidence_max_age_h, review_sla_hours, version, create_user," + " create_time, update_user, update_time) VALUES (?, ?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", projectId, request
                    .getMode(), request.getRequiredApprovals(), request.getExecutionEvidenceRequired(), request
                        .getExecutionEvidenceMaxAgeHours(), request.getReviewSlaHours(), actor, actor);
        } else {
            int changed = jdbcTemplate
                .update("UPDATE automation_ui_case_review_policy SET mode = ?, required_approvals = ?," + " execution_evidence_required = ?, execution_evidence_max_age_h = ?, review_sla_hours = ?," + " version = version + 1, update_user = ?, update_time = CURRENT_TIMESTAMP(3)" + " WHERE project_id = ? AND version = ?", request
                    .getMode(), request.getRequiredApprovals(), request.getExecutionEvidenceRequired(), request
                        .getExecutionEvidenceMaxAgeHours(), request
                            .getReviewSlaHours(), actor, projectId, currentVersion);
            if (changed != 1) {
                throw new BusinessException("REVIEW_POLICY_VERSION_CONFLICT：项目评审策略已更新，请刷新后重试");
            }
        }
        return toPolicy(Objects.requireNonNull(findPolicy(projectId, false)));
    }

    @Override
    public AutomationUiCaseReviewQueueResp getMyQueue(Long projectId) {
        Long actor = currentUser();
        if (projectId != null) {
            projectAccessValidator.requireAccess(projectId);
        }
        String projectFilter = projectId == null ? "" : " AND s.project_id = ?";
        List<Object> args = new ArrayList<>(List.of(actor, actor));
        if (projectId != null) {
            args.add(projectId);
        }
        String sql = "SELECT r.id, r.scene_id, s.name AS scene_name, s.project_id, s.project_name, r.case_id," + " r.case_content_hash, r.hash_schema_version, r.round_no, r.status, r.submitter_id, r.submitted_at," + " r.required_approvals, r.version, mine.decision AS my_decision," + " COALESCE(p.review_sla_hours, 48) AS review_sla_hours," + " (SELECT COUNT(*) FROM automation_ui_case_review_reviewer approved WHERE approved.review_id = r.id" + " AND approved.decision = 'APPROVED') AS approved_count" + " FROM automation_ui_case_review r JOIN automation_ui_scene s ON s.id = r.scene_id" + " LEFT JOIN automation_ui_case_review_reviewer mine ON mine.review_id = r.id AND mine.reviewer_id = ?" + " LEFT JOIN automation_ui_case_review_policy p ON p.project_id = s.project_id" + " WHERE (r.submitter_id = ? OR mine.id IS NOT NULL)" + projectFilter + " AND NOT EXISTS (SELECT 1 FROM automation_ui_case_review newer WHERE newer.scene_id = r.scene_id" + " AND newer.case_id = r.case_id AND newer.round_no > r.round_no)" + " ORDER BY r.submitted_at DESC LIMIT 300";
        List<QueueRow> rows = jdbcTemplate.query(sql, this::mapQueueRow, args.toArray());
        if (projectId == null) {
            Set<Long> accessibleProjects = rows.stream()
                .map(QueueRow::projectId)
                .distinct()
                .filter(projectAccessValidator::canAccess)
                .collect(Collectors.toSet());
            rows = rows.stream().filter(row -> accessibleProjects.contains(row.projectId())).toList();
        }
        Map<Long, AutomationUiSceneDO> scenes = loadScenes(rows);
        LocalDateTime now = LocalDateTime.now();
        List<AutomationUiCaseReviewQueueResp.Item> pending = new ArrayList<>();
        List<AutomationUiCaseReviewQueueResp.Item> submitted = new ArrayList<>();
        List<AutomationUiCaseReviewQueueResp.Item> outdated = new ArrayList<>();
        List<AutomationUiCaseReviewQueueResp.Item> dueSoon = new ArrayList<>();
        for (QueueRow row : rows) {
            AutomationUiCaseReviewQueueResp.Item item = toQueueItem(row, scenes.get(row.sceneId()), now);
            boolean minePending = "IN_REVIEW".equals(item.getStatus()) && "PENDING".equals(row.myDecision());
            if (minePending) {
                pending.add(item);
                if (item.getDueAt() != null && !item.getDueAt().isAfter(now.plusHours(24))) {
                    dueSoon.add(item);
                }
            }
            if (Objects.equals(actor, row.submitterId()) && !Set.of("OUTDATED", "WITHDRAWN")
                .contains(item.getStatus())) {
                submitted.add(item);
            }
            if ("OUTDATED".equals(item.getStatus())) {
                outdated.add(item);
            }
        }
        return AutomationUiCaseReviewQueueResp.builder()
            .pending(pending)
            .submitted(submitted)
            .outdated(outdated)
            .dueSoon(dueSoon)
            .build();
    }

    @Override
    public AutomationUiCaseReviewMetricsResp getMetrics(Long projectId, LocalDate from, LocalDate to) {
        projectAccessValidator.requireAccess(projectId);
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(29) : from;
        if (resolvedFrom.isAfter(resolvedTo) || ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > 366) {
            throw new BusinessException("REVIEW_METRICS_RANGE_INVALID：度量日期范围必须在 367 天以内");
        }
        Timestamp start = Timestamp.valueOf(resolvedFrom.atStartOfDay());
        Timestamp end = Timestamp.valueOf(resolvedTo.plusDays(1).atStartOfDay());
        MetricRow metric = jdbcTemplate
            .query("SELECT COUNT(*) AS review_count," + " SUM(" + REVIEW_COMPLETION_PREDICATE + ") AS completed_count," + " AVG(CASE WHEN " + REVIEW_COMPLETION_PREDICATE + " THEN TIMESTAMPDIFF(SECOND, r.submitted_at, r.completed_at) END) AS avg_seconds," + " SUM(r.round_no = 1 AND " + REVIEW_APPROVED_PREDICATE + ") AS first_pass_approved," + " SUM(r.round_no = 1 AND " + REVIEW_COMPLETION_PREDICATE + ") AS first_pass_decided," + " SUM(r.status = 'OUTDATED') AS outdated_count FROM automation_ui_case_review r" + " JOIN automation_ui_scene s ON s.id = r.scene_id WHERE s.project_id = ?" + " AND r.submitted_at >= ? AND r.submitted_at < ?", rs -> {
                if (!rs.next()) {
                    return new MetricRow(0, 0, null, 0, 0, 0);
                }
                return new MetricRow(rs.getLong("review_count"), rs
                    .getLong("completed_count"), nullableDouble(rs, "avg_seconds"), rs
                        .getLong("first_pass_approved"), rs.getLong("first_pass_decided"), rs
                            .getLong("outdated_count"));
            }, projectId, start, end);
        List<AutomationUiCaseReviewMetricsResp.RuleCount> blockers = jdbcTemplate
            .query("SELECT c.rule_code, COUNT(DISTINCT c.review_id) AS total FROM automation_ui_case_review_check c" + " JOIN automation_ui_case_review r ON r.id = c.review_id JOIN automation_ui_scene s ON s.id = r.scene_id" + " WHERE s.project_id = ? AND r.submitted_at >= ? AND r.submitted_at < ?" + " AND c.result = 'FAIL' AND c.effective_severity = 'BLOCKER' GROUP BY c.rule_code" + " ORDER BY total DESC, c.rule_code LIMIT 10", (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                  rowNum) -> AutomationUiCaseReviewMetricsResp.RuleCount
                                                                                                                                                                                                                                                                                                                                                                                                                                                      .builder()
                                                                                                                                                                                                                                                                                                                                                                                                                                                      .ruleCode(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                          .getString("rule_code"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                      .count(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                          .getLong("total"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                      .build(), projectId, start, end);
        return AutomationUiCaseReviewMetricsResp.builder()
            .projectId(projectId)
            .from(resolvedFrom)
            .to(resolvedTo)
            .reviewCount(metric.reviewCount())
            .completedCount(metric.completedCount())
            .averageReviewDurationHours(metric.averageSeconds() == null ? null : round(metric.averageSeconds() / 3600d))
            .firstPassApprovalRate(rate(metric.firstPassApproved(), metric.firstPassDecided()))
            .outdatedRate(rate(metric.outdatedCount(), metric.reviewCount()))
            .commonBlockerRules(blockers)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiCaseReviewQueueResp assignReviewers(AutomationUiCaseReviewBatchAssignReq request,
                                                           Long projectId) {
        Long actor = currentUser();
        LinkedHashSet<Long> reviewers = request.getReviewerIds()
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (reviewers.isEmpty()) {
            throw new BusinessException("REVIEW_REVIEWER_REQUIRED：至少选择一名评审人");
        }
        reviewerValidator.requireActive(reviewers);
        for (Long reviewId : new LinkedHashSet<>(request.getReviewIds())) {
            AssignmentRow review = jdbcTemplate
                .query("SELECT r.id, r.scene_id, r.case_id, r.submitter_id, r.status, r.version, s.project_id" + " FROM automation_ui_case_review r JOIN automation_ui_scene s ON s.id = r.scene_id" + " WHERE r.id = ? FOR UPDATE", this::mapAssignment, reviewId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("REVIEW_NOT_FOUND：评审单不存在，id=" + reviewId));
            if (projectId != null && !Objects.equals(projectId, review.projectId())) {
                throw new BusinessException("REVIEW_PROJECT_MISMATCH：评审单不属于当前项目，id=" + reviewId);
            }
            ProjectConfigDO project = projectAccessValidator.requireAccess(review.projectId());
            projectAccessValidator.requireAssignableReviewers(project, reviewers);
            Long expectedVersion = request.getExpectedVersions().get(reviewId);
            if (expectedVersion == null || !Objects.equals(expectedVersion, review.version())) {
                throw new BusinessException("REVIEW_VERSION_CONFLICT：评审单已更新，id=" + reviewId);
            }
            if (!"IN_REVIEW".equals(review.status())) {
                throw new BusinessException("REVIEW_STATE_INVALID：仅待评审状态可分配评审人，id=" + reviewId);
            }
            if (reviewers.contains(review.submitterId())) {
                throw new BusinessException("REVIEW_SELF_APPROVAL_NOT_ALLOWED：提交人不能被分配为本轮评审人，id=" + reviewId);
            }
            List<Long> added = new ArrayList<>();
            for (Long reviewer : reviewers) {
                int changed = jdbcTemplate
                    .update("INSERT IGNORE INTO automation_ui_case_review_reviewer (id, review_id, reviewer_id," + " decision, version, create_user, create_time, update_user, update_time)" + " VALUES (?, ?, ?, 'PENDING', 0, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", identifierGenerator
                        .nextId(reviewer)
                        .longValue(), reviewId, reviewer, actor, actor);
                if (changed == 1) {
                    added.add(reviewer);
                }
            }
            if (!added.isEmpty()) {
                int changed = jdbcTemplate
                    .update("UPDATE automation_ui_case_review SET version = version + 1, update_user = ?," + " update_time = CURRENT_TIMESTAMP(3) WHERE id = ? AND version = ?", actor, reviewId, review
                        .version());
                if (changed != 1) {
                    throw new BusinessException("REVIEW_VERSION_CONFLICT：评审单已更新，id=" + reviewId);
                }
                jdbcTemplate
                    .update("INSERT INTO automation_ui_case_review_event (id, review_id, event_type, actor_id," + " payload_json, create_time) VALUES (?, ?, 'REVIEWERS_ASSIGNED', ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))", identifierGenerator
                        .nextId(reviewId)
                        .longValue(), reviewId, actor, JSONUtil.toJsonStr(Map.of("reviewerIds", added)));
                notifyUsers("UI 用例待评审", "用例 " + review.caseId() + " 已分配给你评审", added);
            }
        }
        return getMyQueue(projectId);
    }

    private PolicyRow findPolicy(Long projectId, boolean lock) {
        String sql = "SELECT * FROM automation_ui_case_review_policy WHERE project_id = ?" + (lock
            ? " FOR UPDATE"
            : "");
        return jdbcTemplate.query(sql, this::mapPolicy, projectId).stream().findFirst().orElse(null);
    }

    private PolicyRow mapPolicy(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyRow(rs.getLong("project_id"), rs.getString("mode"), rs.getInt("required_approvals"), rs
            .getBoolean("execution_evidence_required"), rs.getInt("execution_evidence_max_age_h"), rs
                .getInt("review_sla_hours"), rs
                    .getLong("version"), nullableLong(rs, "update_user"), dateTime(rs, "update_time"));
    }

    private AutomationUiCaseReviewPolicyResp defaultPolicy(Long projectId) {
        return AutomationUiCaseReviewPolicyResp.builder()
            .projectId(projectId)
            .mode("OBSERVE")
            .requiredApprovals(1)
            .executionEvidenceRequired(false)
            .executionEvidenceMaxAgeHours(168)
            .reviewSlaHours(DEFAULT_REVIEW_SLA_HOURS)
            .version(0L)
            .build();
    }

    private AutomationUiCaseReviewPolicyResp toPolicy(PolicyRow row) {
        return AutomationUiCaseReviewPolicyResp.builder()
            .projectId(row.projectId())
            .mode(row.mode())
            .requiredApprovals(row.requiredApprovals())
            .executionEvidenceRequired(row.evidenceRequired())
            .executionEvidenceMaxAgeHours(row.evidenceMaxAgeHours())
            .reviewSlaHours(row.reviewSlaHours())
            .version(row.version())
            .updateUser(row.updateUser())
            .updateTime(row.updateTime())
            .build();
    }

    private QueueRow mapQueueRow(ResultSet rs, int rowNum) throws SQLException {
        return new QueueRow(rs.getLong("id"), rs.getLong("scene_id"), rs.getString("scene_name"), rs
            .getLong("project_id"), rs.getString("project_name"), rs.getString("case_id"), rs
                .getString("case_content_hash"), rs.getString("hash_schema_version"), rs.getInt("round_no"), rs
                    .getString("status"), rs.getLong("submitter_id"), dateTime(rs, "submitted_at"), rs
                        .getInt("required_approvals"), rs.getInt("approved_count"), rs.getLong("version"), rs
                            .getString("my_decision"), rs.getInt("review_sla_hours"));
    }

    private Map<Long, AutomationUiSceneDO> loadScenes(List<QueueRow> rows) {
        List<Long> ids = rows.stream().map(QueueRow::sceneId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sceneMapper.selectBatchIds(ids)
            .stream()
            .collect(Collectors.toMap(AutomationUiSceneDO::getId, item -> item));
    }

    private AutomationUiCaseReviewQueueResp.Item toQueueItem(QueueRow row,
                                                             AutomationUiSceneDO scene,
                                                             LocalDateTime now) {
        String caseName = row.caseId();
        CaseDO currentCase = null;
        if (scene != null && scene.getCaseList() != null) {
            currentCase = scene.getCaseList()
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> Objects.equals(row.caseId(), item.getId()))
                .findFirst()
                .orElse(null);
            caseName = currentCase == null || currentCase.getName() == null
                ? row.caseId() + "（旧版本）"
                : currentCase.getName();
        }
        String status = effectiveQueueStatus(row.status(), row.caseContentHash(), row.hashSchemaVersion(), currentCase);
        LocalDateTime dueAt = row.submittedAt() == null ? null : row.submittedAt().plusHours(row.reviewSlaHours());
        return AutomationUiCaseReviewQueueResp.Item.builder()
            .reviewId(row.id())
            .sceneId(row.sceneId())
            .sceneName(row.sceneName())
            .projectId(row.projectId())
            .projectName(row.projectName())
            .caseId(row.caseId())
            .caseName(caseName)
            .roundNo(row.roundNo())
            .status(status)
            .submitterId(row.submitterId())
            .submitterName(UserContextHolder.getNickname(row.submitterId()))
            .submittedAt(row.submittedAt())
            .dueAt(dueAt)
            .overdue(dueAt != null && dueAt.isBefore(now))
            .requiredApprovals(row.requiredApprovals())
            .approvedCount(row.approvedCount())
            .version(row.version())
            .build();
    }

    static String effectiveQueueStatus(String storedStatus,
                                       String reviewedHash,
                                       String reviewedSchemaVersion,
                                       CaseDO currentCase) {
        if ("OUTDATED".equals(storedStatus) || !INVALIDATABLE_STATUSES.contains(storedStatus)) {
            return storedStatus;
        }
        if (currentCase == null) {
            return "OUTDATED";
        }
        AutomationUiCaseFingerprint.Fingerprint current = AutomationUiCaseFingerprint.compute(currentCase);
        return Objects.equals(reviewedHash, current.hash()) && Objects.equals(reviewedSchemaVersion, current
            .schemaVersion()) ? storedStatus : "OUTDATED";
    }

    private AssignmentRow mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        return new AssignmentRow(rs.getLong("id"), rs.getLong("scene_id"), rs.getString("case_id"), rs
            .getLong("submitter_id"), rs.getString("status"), rs.getLong("version"), rs.getLong("project_id"));
    }

    private void notifyUsers(String title, String content, Collection<Long> users) {
        List<Long> recipients = users.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        try {
            MessageReq request = new MessageReq();
            request.setTitle(title);
            request.setContent(content);
            request.setType(MessageTypeEnum.SECURITY);
            messageService.add(request, recipients);
        } catch (RuntimeException e) {
            log.warn("Failed to send review assignment notification, recipients={}", recipients, e);
        }
    }

    private Long currentUser() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("REVIEW_USER_REQUIRED：无法识别当前用户");
        }
        return userId;
    }

    private LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number)value).longValue();
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number)value).doubleValue();
    }

    private Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : round(numerator * 100d / denominator);
    }

    private Double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private record PolicyRow(Long projectId, String mode, int requiredApprovals, boolean evidenceRequired,
                             int evidenceMaxAgeHours, int reviewSlaHours, long version, Long updateUser,
                             LocalDateTime updateTime) {
    }

    private record QueueRow(Long id, Long sceneId, String sceneName, Long projectId, String projectName, String caseId,
                            String caseContentHash, String hashSchemaVersion, int roundNo, String status,
                            Long submitterId, LocalDateTime submittedAt, int requiredApprovals, int approvedCount,
                            long version, String myDecision, int reviewSlaHours) {
    }

    private record AssignmentRow(Long id, Long sceneId, String caseId, Long submitterId, String status, long version,
                                 Long projectId) {
    }

    private record MetricRow(long reviewCount, long completedCount, Double averageSeconds, long firstPassApproved,
                             long firstPassDecided, long outdatedCount) {
    }
}
