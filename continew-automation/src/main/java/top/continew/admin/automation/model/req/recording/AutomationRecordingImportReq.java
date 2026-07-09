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

package top.continew.admin.automation.model.req.recording;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Playwright 录制导入请求。
 *
 * @author Codex
 */
@Data
public class AutomationRecordingImportReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导入模式。MVP 仅支持 createScene。
     */
    @NotBlank(message = "导入模式不能为空")
    private String mode;

    /**
     * 新建场景信息。
     */
    @Valid
    @NotNull(message = "场景信息不能为空")
    private RecordingSceneReq scene;

    /**
     * 录制生成的原始用例。
     */
    @Valid
    @NotNull(message = "录制用例不能为空")
    private PlaywrightRecordedCaseReq recordedCase;

    /**
     * MVP 默认不持久化截图，避免 caseList JSON 被 base64 撑大。
     */
    private Boolean persistScreenshots = Boolean.FALSE;

    /**
     * 默认不在 playwright_step 中保留原始 base64 截图。
     */
    private Boolean keepRawScreenshotInStep = Boolean.FALSE;
}
