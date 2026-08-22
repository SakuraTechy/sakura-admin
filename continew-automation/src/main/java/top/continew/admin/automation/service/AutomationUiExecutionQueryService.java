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

import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.query.AutomationUiExecutionCaseHistoryQuery;
import top.continew.admin.automation.model.req.AutomationUiPageReq;
import top.continew.admin.automation.model.resp.AutomationUiExecutionArtifactResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseHistoryResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionPageResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepResp;
import top.continew.starter.extension.crud.model.resp.PageResp;

/** UI 自动化执行事实分层查询服务。 */
public interface AutomationUiExecutionQueryService {

    AutomationUiExecutionPageResp page(AutomationUiExecutionQuery query, AutomationUiPageReq pageQuery);

    AutomationUiExecutionDetailResp detail(Long executionDbId);

    PageResp<AutomationUiExecutionCaseResp> cases(Long executionDbId, AutomationUiPageReq pageQuery);

    PageResp<AutomationUiExecutionCaseHistoryResp> caseHistory(AutomationUiExecutionCaseHistoryQuery query,
                                                               AutomationUiPageReq pageQuery);

    PageResp<AutomationUiExecutionStepResp> steps(Long caseExecutionDbId, AutomationUiPageReq pageQuery);

    AutomationUiExecutionStepDetailResp stepDetail(Long stepExecutionDbId);

    PageResp<AutomationUiExecutionArtifactResp> artifacts(Long executionDbId, AutomationUiPageReq pageQuery);
}
