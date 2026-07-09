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

import top.continew.admin.automation.model.req.recording.AutomationRecordingImportReq;
import top.continew.admin.automation.model.resp.recording.AutomationRecordingImportResp;

/**
 * Playwright 录制导入业务接口。
 *
 * @author Codex
 */
public interface AutomationRecordingImportService {

    /**
     * 导入录制结果。
     *
     * @param req 导入请求
     * @return 导入结果
     */
    AutomationRecordingImportResp importRecording(AutomationRecordingImportReq req);
}
