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

import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCaseStatusReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;

/**
 * Playwright Runner admin 数据服务。
 *
 * @author Codex
 */
public interface AutomationPlaywrightCaseService {

    AutomationPlaywrightCaseResp getCase(String caseKey);

    AutomationPlaywrightCaseResp getCase(String caseKey, Long projectEnvironmentId);

    AutomationPlaywrightBatchResp createBatch(AutomationPlaywrightBatchCreateReq req);

    void updateBatchCaseStatus(String sceneKey,
                               String batchId,
                               String caseId,
                               AutomationPlaywrightBatchCaseStatusReq req);

    void cancelBatch(String sceneKey, String batchId);

    void saveResult(String caseKey, AutomationPlaywrightResultReq req);
}
