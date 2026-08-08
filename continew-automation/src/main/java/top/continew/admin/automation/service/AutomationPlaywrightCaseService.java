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
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;

/**
 * Playwright Runner admin 数据服务。
 *
 * @author Codex
 */
public interface AutomationPlaywrightCaseService {

    AutomationPlaywrightCaseResp getCase(String caseKey);

    AutomationPlaywrightCaseResp getCase(String caseKey, Long projectEnvironmentId);

    /**
     * 批次 Runner 读取用例时绑定当前 execution capability，避免仅凭通用读取权限跨批次读取执行数据。
     */
    AutomationPlaywrightCaseResp getCase(String caseKey,
                                         Long projectEnvironmentId,
                                         String batchId,
                                         String executionCapability);

    AutomationPlaywrightBatchResp createBatch(AutomationPlaywrightBatchCreateReq req);

    void updateBatchCaseStatus(String sceneKey,
                               String batchId,
                               String caseId,
                               AutomationPlaywrightBatchCaseStatusReq req);

    void cancelBatch(String sceneKey, String batchId);

    /**
     * 取消批次中的单个用例，不影响同批次其余用例的调度。
     */
    void cancelCase(String sceneKey, String batchId, String caseId);

    /**
     * 查询执行端是否应停止当前用例。CDP 页面通过此标记协作式停止回放。
     */
    AutomationPlaywrightCaseCancellationResp getCaseCancellation(String sceneKey, String batchId, String caseId);

    boolean isBatchTerminal(String sceneKey, String batchId);

    void validateReusableBatchCase(String sceneKey, String batchId, String caseId, Long projectEnvironmentId);

    void saveResult(String caseKey, AutomationPlaywrightResultReq req);

    /**
     * 保存 Runner 回传结果，并使用当前执行批次的短期 capability 校验服务主体范围。
     */
    void saveResult(String caseKey, AutomationPlaywrightResultReq req, String executionCapability);
}
