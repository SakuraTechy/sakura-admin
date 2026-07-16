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
     * 导入模式：createScene、appendCase、replaceCase、appendStep、replaceStep。
     * replaceCaseSteps 为旧客户端兼容模式，仍表示仅替换目标用例的步骤。
     */
    @NotBlank(message = "导入模式不能为空")
    private String mode;

    /**
     * 新建场景信息。createScene 模式必填。
     */
    @Valid
    private RecordingSceneReq scene;

    /**
     * 目标场景数据库 ID。除 createScene 外的模式必填。
     */
    private Long targetSceneDbId;

    /**
     * 目标用例 ID。replaceCase、appendStep、replaceStep 及兼容模式必填。
     */
    private String targetCaseId;

    /**
     * 目标步骤 ID。replaceStep 模式必填。
     */
    private String targetStepId;

    /**
     * 追加位置。appendCase 模式可选：FIRST-最前面，LAST-末尾，AFTER-指定用例之后。
     */
    private String appendPosition;

    /**
     * 追加锚点用例 ID。appendPosition=AFTER 时必填。
     */
    private String appendAfterCaseId;

    /**
     * 步骤追加位置。appendStep 模式可选：FIRST-最前面，LAST-末尾，AFTER-指定步骤之后。
     */
    private String stepAppendPosition;

    /**
     * 步骤追加锚点 ID。stepAppendPosition=AFTER 时必填。
     */
    private String appendAfterStepId;

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
