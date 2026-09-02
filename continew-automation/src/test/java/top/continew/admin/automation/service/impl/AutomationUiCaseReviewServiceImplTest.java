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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.automation.converter.AutomationUiCaseFingerprint;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewDecisionReq;
import top.continew.admin.automation.service.AutomationUiCaseReviewChecker;
import top.continew.admin.automation.service.AutomationUiDefinitionRevisionService;
import top.continew.admin.system.service.MessageService;
import top.continew.admin.system.model.req.MessageReq;
import top.continew.starter.core.exception.BusinessException;

class AutomationUiCaseReviewServiceImplTest {

    @Test
    void configuredApprovalCountMustNotBeSilentlyLowered() {
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl.requireReviewerCapacity(1, 2))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_REVIEWER_INSUFFICIENT");
        assertThat(AutomationUiCaseReviewServiceImpl.requireReviewerCapacity(2, 2)).isEqualTo(2);
    }

    @Test
    void rejectionMustContainReason() {
        AutomationUiCaseReviewServiceImpl service = service(mock(JdbcTemplate.class));
        AutomationUiCaseReviewDecisionReq request = decision("REJECTED", "   ");

        assertThatThrownBy(() -> service.requireDecisionReason(10L, request)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_REJECTION_REASON_REQUIRED");
    }

    @Test
    void changeRequestMustContainReasonOrOpenSubstantialIssue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AutomationUiCaseReviewServiceImpl service = service(jdbcTemplate);
        AutomationUiCaseReviewDecisionReq request = decision("CHANGES_REQUESTED", null);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers
            .eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(0, 1);

        assertThatThrownBy(() -> service.requireDecisionReason(10L, request)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_CHANGE_REASON_REQUIRED");
        assertThatCode(() -> service.requireDecisionReason(10L, request)).doesNotThrowAnyException();
    }

    @Test
    void sameContentMustNotBypassExistingDecision() {
        AutomationUiCaseFingerprint.Fingerprint current = new AutomationUiCaseFingerprint.Fingerprint("v1", "hash", "{}");

        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl
            .requireResubmittable("APPROVED", "hash", "v1", current, false)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_ALREADY_APPROVED");
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl
            .requireResubmittable("CHANGES_REQUESTED", "hash", "v1", current, true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_CONTENT_UNCHANGED");
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl
            .requireResubmittable("CHANGES_REQUESTED", "new-hash", "v1", current, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl
            .requireResubmittable("WITHDRAWN", "hash", "v1", current, true)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_CONTENT_UNCHANGED");
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl
            .requireResubmittable("WITHDRAWN", "hash", "v1", current, false)).doesNotThrowAnyException();
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl
            .requireResubmittable("IN_REVIEW", "hash", "old-schema", current, false)).doesNotThrowAnyException();
    }

    @Test
    void submittedApprovalMustBeRevokedBeforeAnotherDecision() {
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl.requirePendingReviewerDecision("PENDING"))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl.requirePendingReviewerDecision("APPROVED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_DECISION_ALREADY_SUBMITTED");
    }

    @Test
    void commentResolutionMustFollowOpenAndResolvedStateTransitions() {
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl.requireCommentResolutionTransition("OPEN", false))
            .doesNotThrowAnyException();
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl.requireCommentResolutionTransition("RESOLVED", true))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl.requireCommentResolutionTransition("OPEN", true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_COMMENT_STATE_INVALID");
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl
            .requireCommentResolutionTransition("RESOLVED", false)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_COMMENT_STATE_INVALID");
    }

    @Test
    void resolvedCommentThreadMustBeReopenedBeforeReplying() {
        assertThatCode(() -> AutomationUiCaseReviewServiceImpl.requireReplyableCommentThread("OPEN"))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl.requireReplyableCommentThread("RESOLVED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_COMMENT_STATE_INVALID");
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl.requireReplyableCommentThread("WONT_FIX"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_COMMENT_STATE_INVALID");
    }

    @Test
    void stepCommentAnchorMustReferenceSnapshotStepAndControlledField() {
        CaseDO caseDO = new CaseDO();
        StepDO step = new StepDO();
        step.setId("STEP-1");
        caseDO.setStepList(List.of(step));

        AutomationUiCaseReviewServiceImpl.CommentAnchor anchor = AutomationUiCaseReviewServiceImpl
            .validateCommentAnchor(caseDO, "STEP", "STEP-1", "configList.locator_meta");

        assertThat(anchor.stepId()).isEqualTo("STEP-1");
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl
            .validateCommentAnchor(caseDO, "STEP", "MISSING", null)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_COMMENT_STEP_NOT_FOUND");
        assertThatThrownBy(() -> AutomationUiCaseReviewServiceImpl
            .validateCommentAnchor(caseDO, "STEP", "STEP-1", "arbitrary.path")).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_COMMENT_FIELD_INVALID");
    }

    @Test
    void reportLinksMustUseWebOrApplicationProtocols() {
        assertThat(AutomationUiCaseReviewServiceImpl.safeReportUrl("https://reports.example/case/1"))
            .isEqualTo("https://reports.example/case/1");
        assertThat(AutomationUiCaseReviewServiceImpl.safeReportUrl("/test/reports/1")).isEqualTo("/test/reports/1");
        assertThat(AutomationUiCaseReviewServiceImpl.safeReportUrl("javascript:alert(1)")).isNull();
        assertThat(AutomationUiCaseReviewServiceImpl.safeReportUrl("data:text/html,test")).isNull();
        assertThat(AutomationUiCaseReviewServiceImpl.safeReportUrl("//example.invalid/report")).isNull();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void previousApprovedBaselineMustUseApprovalFactsInsteadOfMutableStatus() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(77L));
        AutomationUiCaseReviewServiceImpl service = service(jdbcTemplate);

        assertThat(service.findPreviousApprovedRevision(1L, "CASE-1", 3)).isEqualTo(77L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("rr.decision = 'APPROVED'")
            .contains(">= r.required_approvals")
            .contains("r.round_no < ?")
            .doesNotContain("r.status = 'APPROVED'");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void executionFactsMustIgnoreUnfinishedRecords() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AutomationUiCaseReviewServiceImpl service = service(jdbcTemplate);

        AutomationUiCaseReviewChecker.ExecutionFacts facts = service.loadExecutionFacts(9L, "CASE-1", "hash", "v1");

        assertThat(facts.sampleSize()).isZero();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("e.scene_id = ?")
            .contains("c.case_id = ?")
            .contains("c.finished_at IS NOT NULL");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void commentParticipantNotificationExcludesActorAndIncludesThreadAuthors() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MessageService messageService = mock(MessageService.class);
        AutomationUiCaseReviewServiceImpl service = new AutomationUiCaseReviewServiceImpl(jdbcTemplate, mock(IdentifierGenerator.class), new ObjectMapper(), mock(AutomationUiSceneMapper.class), mock(AutomationUiDefinitionRevisionService.class), mock(AutomationUiCaseReviewChecker.class), messageService, mock(AutomationUiCaseReviewReviewerValidator.class), mock(AutomationUiCaseReviewProjectAccessValidator.class));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List
            .of(20L, 30L, 20L));

        var reviewRow = java.util.Arrays.stream(AutomationUiCaseReviewServiceImpl.class.getDeclaredClasses())
            .filter(type -> type.getSimpleName().equals("ReviewRow"))
            .findFirst()
            .orElseThrow();
        var method = AutomationUiCaseReviewServiceImpl.class
            .getDeclaredMethod("notifyCommentParticipants", reviewRow, Long.class, Long.class);
        method.setAccessible(true);
        var constructor = reviewRow.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object review = constructor
            .newInstance(1L, 2L, "CASE-1", 3L, 1L, "hash", "v1", 1, "IN_REVIEW", 10L, LocalDateTime
                .now(), 1, null, null, 1L, LocalDateTime.now());
        method.invoke(service, review, 99L, 20L);

        var recipients = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(messageService).add(any(MessageReq.class), recipients.capture());
        assertThat(recipients.getValue()).containsExactly(10L, 30L);
    }

    private AutomationUiCaseReviewDecisionReq decision(String decision, String comment) {
        AutomationUiCaseReviewDecisionReq request = new AutomationUiCaseReviewDecisionReq();
        request.setDecision(decision);
        request.setComment(comment);
        request.setExpectedReviewVersion(0L);
        return request;
    }

    private AutomationUiCaseReviewServiceImpl service(JdbcTemplate jdbcTemplate) {
        return new AutomationUiCaseReviewServiceImpl(jdbcTemplate, mock(IdentifierGenerator.class), new ObjectMapper(), mock(AutomationUiSceneMapper.class), mock(AutomationUiDefinitionRevisionService.class), mock(AutomationUiCaseReviewChecker.class), mock(MessageService.class), mock(AutomationUiCaseReviewReviewerValidator.class), mock(AutomationUiCaseReviewProjectAccessValidator.class));
    }
}
