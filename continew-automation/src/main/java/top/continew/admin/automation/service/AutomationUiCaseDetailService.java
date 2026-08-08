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

import top.continew.admin.automation.model.req.ui.AutomationUiCaseEditReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepEditReq;
import top.continew.admin.automation.model.resp.ui.AutomationUiCaseDetailResp;
import top.continew.admin.automation.model.resp.ui.AutomationUiStepDetailResp;

/** 用例和步骤详情 DTO 的读写边界。 */
public interface AutomationUiCaseDetailService {

    AutomationUiCaseDetailResp getCaseDetail(Long sceneDbId, String caseId);

    AutomationUiStepDetailResp getStepDetail(Long sceneDbId, String caseId, String stepId);

    AutomationUiCaseDetailResp updateCase(Long sceneDbId, AutomationUiCaseEditReq request);

    AutomationUiStepDetailResp updateStep(Long sceneDbId, AutomationUiStepEditReq request);
}
