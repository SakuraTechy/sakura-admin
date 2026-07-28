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

import org.springframework.web.multipart.MultipartFile;

/**
 * Playwright Runner 产物存储服务。
 *
 * @author Codex
 */
public interface AutomationPlaywrightArtifactService {

    Artifact store(String runId, String artifactType, MultipartFile file);

    /**
     * 读取新链路中由系统文件管理持久化的 Playwright artifact。
     */
    ArtifactResource loadByFileId(Long fileId);

    /**
     * 读取历史 uploads 目录中的 artifact。新文件不再写入该目录。
     */
    ArtifactResource loadLegacy(String runId, String fileName);

    record Artifact(Long fileId, String runId, String artifactType, String fileName, String url, String contentType,
                    long size, String md5, String storageCode) {
    }

    record ArtifactResource(byte[] content, String contentType, String fileName) {
    }
}
