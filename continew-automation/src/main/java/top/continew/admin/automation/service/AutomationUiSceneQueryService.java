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

import java.util.Collection;
import java.util.List;

import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.resp.AutomationUiExecutionSummaryResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneDefinitionResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneGlobalRevisionResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneSummaryResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionCasePageResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionCaseResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionStepPageResp;
import com.fasterxml.jackson.databind.JsonNode;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

/** UI 自动化场景专用轻量查询服务。 */
public interface AutomationUiSceneQueryService {

    PageResp<AutomationUiSceneSummaryResp> page(AutomationUiSceneQuery query, PageQuery pageQuery);

    PageResp<AutomationUiSceneSummaryResp> page(AutomationUiSceneQuery query,
                                                PageQuery pageQuery,
                                                AutomationUiExecutionScopeReq executionScope);

    List<AutomationUiSceneSummaryResp> summaries(Collection<Long> sceneDbIds);

    List<AutomationUiSceneSummaryResp> summaries(Collection<Long> sceneDbIds,
                                                 AutomationUiExecutionScopeReq executionScope);

    AutomationUiExecutionSummaryResp latestExecution(Long sceneDbId, AutomationUiExecutionScopeReq executionScope);

    List<AutomationUiSceneGlobalRevisionResp> revisions(Collection<Long> sceneDbIds);

    DefinitionView definition(Long sceneDbId);

    DefinitionNodeView<AutomationUiDefinitionCasePageResp> definitionCases(Long sceneDbId,
                                                                           int page,
                                                                           int size,
                                                                           String keyword);

    DefinitionNodeView<AutomationUiDefinitionCaseResp> definitionCase(Long sceneDbId, String caseId);

    DefinitionNodeView<AutomationUiDefinitionStepPageResp> definitionSteps(Long sceneDbId,
                                                                           String caseId,
                                                                           int page,
                                                                           int size);

    DefinitionNodeView<JsonNode> definitionStep(Long sceneDbId, String caseId, String stepId);

    /** ETag 只能在对象授权和展示范围摘要计算完成后使用。 */
    record DefinitionView(AutomationUiSceneDefinitionResp body, String etag) {
    }

    /** 节点 ETag 绑定当前投影、节点身份、正文摘要、脱敏策略和访问范围。 */
    record DefinitionNodeView<T>(T body, String etag) {
    }
}
