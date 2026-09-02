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

package top.continew.admin.automation.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewBatchAssignReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewChecklistReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewCommentReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewDecisionReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewResolveReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewSubmitReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewVersionReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewPolicyReq;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewMetricsResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewPolicyResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewQueueResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewResp;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewReviewerOptionResp;
import top.continew.admin.automation.service.AutomationUiCaseReviewGovernanceService;
import top.continew.admin.automation.service.AutomationUiCaseReviewService;
import top.continew.starter.web.model.R;

@Tag(name = "自动化管理-UI 自动化用例评审 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/automationUiScene")
public class AutomationUiCaseReviewController {

    private final AutomationUiCaseReviewService reviewService;
    private final AutomationUiCaseReviewGovernanceService governanceService;

    @Operation(summary = "查询我的用例评审队列")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/case-reviews/my-queue")
    public R<AutomationUiCaseReviewQueueResp> queue(@RequestParam(required = false) Long projectId) {
        return R.ok(governanceService.getMyQueue(projectId));
    }

    @Operation(summary = "查询用例评审度量")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/case-reviews/metrics")
    public R<AutomationUiCaseReviewMetricsResp> metrics(@RequestParam Long projectId,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(governanceService.getMetrics(projectId, from, to));
    }

    @Operation(summary = "查询项目用例评审策略")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/case-reviews/projects/{projectId}/policy")
    public R<AutomationUiCaseReviewPolicyResp> policy(@PathVariable Long projectId) {
        return R.ok(governanceService.getPolicy(projectId));
    }

    @Operation(summary = "查询项目可选评审人")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/case-reviews/projects/{projectId}/eligible-reviewers")
    public R<List<AutomationUiCaseReviewReviewerOptionResp>> eligibleReviewers(@PathVariable Long projectId) {
        return R.ok(governanceService.listEligibleReviewers(projectId));
    }

    @Operation(summary = "更新项目用例评审策略")
    @SaCheckPermission("automation:automationUiScene:review:admin")
    @PutMapping("/case-reviews/projects/{projectId}/policy")
    public R<AutomationUiCaseReviewPolicyResp> updatePolicy(@PathVariable Long projectId,
                                                            @Valid @RequestBody AutomationUiCaseReviewPolicyReq request) {
        return R.ok(governanceService.updatePolicy(projectId, request));
    }

    @Operation(summary = "批量分配用例评审人")
    @SaCheckPermission("automation:automationUiScene:review:admin")
    @PostMapping("/case-reviews/reviewers/batch-assign")
    public R<AutomationUiCaseReviewQueueResp> batchAssign(@RequestParam(required = false) Long projectId,
                                                          @Valid @RequestBody AutomationUiCaseReviewBatchAssignReq request) {
        return R.ok(governanceService.assignReviewers(request, projectId));
    }

    @Operation(summary = "查询场景用例评审汇总")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/{sceneId}/case-reviews/summary")
    public R<List<Map<String, Object>>> summary(@PathVariable Long sceneId) {
        return R.ok(reviewService.getSceneSummary(sceneId));
    }

    @Operation(summary = "查询用例当前评审")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/{sceneId}/cases/{caseId}/review")
    public R<AutomationUiCaseReviewResp> current(@PathVariable Long sceneId, @PathVariable String caseId) {
        return R.ok(reviewService.getCurrent(sceneId, caseId));
    }

    @Operation(summary = "查询用例评审历史")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/{sceneId}/cases/{caseId}/reviews")
    public R<List<AutomationUiCaseReviewResp>> history(@PathVariable Long sceneId, @PathVariable String caseId) {
        return R.ok(reviewService.listHistory(sceneId, caseId));
    }

    @Operation(summary = "查询评审结构化变更")
    @SaCheckPermission("automation:automationUiScene:review:view")
    @GetMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/diff")
    public R<Map<String, Object>> diff(@PathVariable Long sceneId,
                                       @PathVariable String caseId,
                                       @PathVariable Long reviewId) {
        return R.ok(reviewService.getDiff(sceneId, caseId, reviewId));
    }

    @Operation(summary = "提交用例评审")
    @SaCheckPermission("automation:automationUiScene:review:submit")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/submit")
    public R<AutomationUiCaseReviewResp> submit(@PathVariable Long sceneId,
                                                @PathVariable String caseId,
                                                @Valid @RequestBody AutomationUiCaseReviewSubmitReq request) {
        return R.ok(reviewService.submit(sceneId, caseId, request));
    }

    @Operation(summary = "提交评审结论")
    @SaCheckPermission("automation:automationUiScene:review:approve")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/decision")
    public R<AutomationUiCaseReviewResp> decision(@PathVariable Long sceneId,
                                                  @PathVariable String caseId,
                                                  @PathVariable Long reviewId,
                                                  @Valid @RequestBody AutomationUiCaseReviewDecisionReq request) {
        return R.ok(reviewService.decide(sceneId, caseId, reviewId, request));
    }

    @Operation(summary = "重新运行自动检查")
    @SaCheckPermission("automation:automationUiScene:review:submit")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/recheck")
    public R<AutomationUiCaseReviewResp> recheck(@PathVariable Long sceneId,
                                                 @PathVariable String caseId,
                                                 @PathVariable Long reviewId,
                                                 @Valid @RequestBody AutomationUiCaseReviewVersionReq request) {
        return R.ok(reviewService.recheck(sceneId, caseId, reviewId, request.getExpectedReviewVersion()));
    }

    @Operation(summary = "新增评审意见或回复")
    @SaCheckPermission("automation:automationUiScene:review:comment")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/comments")
    public R<AutomationUiCaseReviewResp> comment(@PathVariable Long sceneId,
                                                 @PathVariable String caseId,
                                                 @PathVariable Long reviewId,
                                                 @Valid @RequestBody AutomationUiCaseReviewCommentReq request) {
        return R.ok(reviewService.addComment(sceneId, caseId, reviewId, request));
    }

    @Operation(summary = "解决或重开评审意见")
    @SaCheckPermission("automation:automationUiScene:review:comment")
    @PutMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/comments/{commentId}/resolve")
    public R<AutomationUiCaseReviewResp> resolve(@PathVariable Long sceneId,
                                                 @PathVariable String caseId,
                                                 @PathVariable Long reviewId,
                                                 @PathVariable Long commentId,
                                                 @Valid @RequestBody AutomationUiCaseReviewResolveReq request) {
        return R.ok(reviewService.resolveComment(sceneId, caseId, reviewId, commentId, request));
    }

    @Operation(summary = "更新人工评审清单")
    @SaCheckPermission("automation:automationUiScene:review:approve")
    @PutMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/checklist")
    public R<AutomationUiCaseReviewResp> checklist(@PathVariable Long sceneId,
                                                   @PathVariable String caseId,
                                                   @PathVariable Long reviewId,
                                                   @Valid @RequestBody AutomationUiCaseReviewChecklistReq request) {
        return R.ok(reviewService.updateChecklist(sceneId, caseId, reviewId, request));
    }

    @Operation(summary = "撤回用例评审")
    @SaCheckPermission("automation:automationUiScene:review:submit")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/withdraw")
    public R<AutomationUiCaseReviewResp> withdraw(@PathVariable Long sceneId,
                                                  @PathVariable String caseId,
                                                  @PathVariable Long reviewId,
                                                  @Valid @RequestBody AutomationUiCaseReviewVersionReq request) {
        return R.ok(reviewService.withdraw(sceneId, caseId, reviewId, request.getExpectedReviewVersion()));
    }

    @Operation(summary = "催办用例评审")
    @SaCheckPermission("automation:automationUiScene:review:submit")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/remind")
    public R<Void> remind(@PathVariable Long sceneId, @PathVariable String caseId, @PathVariable Long reviewId) {
        reviewService.remind(sceneId, caseId, reviewId);
        return R.ok();
    }

    @Operation(summary = "撤销本人批准")
    @SaCheckPermission("automation:automationUiScene:review:approve")
    @PostMapping("/{sceneId}/cases/{caseId}/reviews/{reviewId}/revoke-approval")
    public R<AutomationUiCaseReviewResp> revoke(@PathVariable Long sceneId,
                                                @PathVariable String caseId,
                                                @PathVariable Long reviewId,
                                                @Valid @RequestBody AutomationUiCaseReviewVersionReq request) {
        return R.ok(reviewService.revokeApproval(sceneId, caseId, reviewId, request.getExpectedReviewVersion()));
    }
}
