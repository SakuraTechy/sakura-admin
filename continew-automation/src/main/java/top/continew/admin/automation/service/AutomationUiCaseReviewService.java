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

package top.continew.admin.automation.service;

import java.util.List;
import java.util.Map;

import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewChecklistReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewCommentReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewDecisionReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewResolveReq;
import top.continew.admin.automation.model.req.review.AutomationUiCaseReviewSubmitReq;
import top.continew.admin.automation.model.resp.review.AutomationUiCaseReviewResp;

public interface AutomationUiCaseReviewService {

    AutomationUiCaseReviewResp getCurrent(Long sceneId, String caseId);

    List<AutomationUiCaseReviewResp> listHistory(Long sceneId, String caseId);

    Map<String, Object> getDiff(Long sceneId, String caseId, Long reviewId);

    AutomationUiCaseReviewResp submit(Long sceneId, String caseId, AutomationUiCaseReviewSubmitReq request);

    AutomationUiCaseReviewResp decide(Long sceneId,
                                      String caseId,
                                      Long reviewId,
                                      AutomationUiCaseReviewDecisionReq request);

    AutomationUiCaseReviewResp recheck(Long sceneId, String caseId, Long reviewId, Long expectedReviewVersion);

    AutomationUiCaseReviewResp addComment(Long sceneId,
                                          String caseId,
                                          Long reviewId,
                                          AutomationUiCaseReviewCommentReq request);

    AutomationUiCaseReviewResp resolveComment(Long sceneId,
                                              String caseId,
                                              Long reviewId,
                                              Long commentId,
                                              AutomationUiCaseReviewResolveReq request);

    AutomationUiCaseReviewResp updateChecklist(Long sceneId,
                                               String caseId,
                                               Long reviewId,
                                               AutomationUiCaseReviewChecklistReq request);

    AutomationUiCaseReviewResp withdraw(Long sceneId, String caseId, Long reviewId, Long expectedReviewVersion);

    AutomationUiCaseReviewResp revokeApproval(Long sceneId, String caseId, Long reviewId, Long expectedReviewVersion);

    void remind(Long sceneId, String caseId, Long reviewId);

    List<Map<String, Object>> getSceneSummary(Long sceneId);
}
