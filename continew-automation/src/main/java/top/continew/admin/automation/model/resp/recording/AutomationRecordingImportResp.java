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

package top.continew.admin.automation.model.resp.recording;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Playwright 录制导入响应。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationRecordingImportResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long sceneDbId;

    private String sceneId;

    private String caseId;

    private String recordingId;

    private Integer stepCount;

    private String mode;
}
