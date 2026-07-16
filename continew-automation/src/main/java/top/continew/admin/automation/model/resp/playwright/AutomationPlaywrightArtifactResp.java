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

package top.continew.admin.automation.model.resp.playwright;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Playwright Runner 产物上传结果。
 *
 * @author Codex
 */
@Data
@Builder
@Schema(description = "Playwright Runner 产物上传结果")
public class AutomationPlaywrightArtifactResp {

    @Schema(description = "执行 ID")
    private String runId;

    @Schema(description = "产物类型")
    private String artifactType;

    @Schema(description = "服务端文件名")
    private String fileName;

    @Schema(description = "受鉴权保护的读取地址")
    private String url;

    @Schema(description = "文件类型")
    private String contentType;

    @Schema(description = "文件大小")
    private Long size;
}
