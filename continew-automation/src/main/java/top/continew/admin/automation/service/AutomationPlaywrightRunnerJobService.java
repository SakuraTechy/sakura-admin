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

import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;

/**
 * Playwright Runner 任务服务。
 */
public interface AutomationPlaywrightRunnerJobService {

    AutomationPlaywrightRunnerJobResp create(AutomationPlaywrightRunnerJobReq req);

    /**
     * 计划调度使用已捕获的用户令牌；无人值守任务为空时回退到服务令牌配置。
     */
    AutomationPlaywrightRunnerJobResp create(AutomationPlaywrightRunnerJobReq req, String token);

    AutomationPlaywrightRunnerJobResp get(String jobId);

    /**
     * 查询任务状态，并且只返回指定序号之后的新日志。
     */
    AutomationPlaywrightRunnerJobResp get(String jobId, Long afterSequence);

    AutomationPlaywrightRunnerJobResp cancel(String jobId);

    void cancelBatch(String batchId);

    /**
     * 仅终止指定批次中的一个 Runner 进程。
     */
    void cancelCase(String sceneKey, String batchId, String caseId);

    void acceptLiveFrame(String jobId, byte[] frame);

    LiveFrame getLiveFrame(String jobId);

    /**
     * 实时画面帧只存在任务内存中，不进入场景主数据。
     */
    record LiveFrame(long sequence, byte[] content) {
    }
}
