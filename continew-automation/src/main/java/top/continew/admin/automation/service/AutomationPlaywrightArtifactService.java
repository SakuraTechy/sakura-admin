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

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * Playwright Runner 产物存储服务。
 *
 * @author Codex
 */
public interface AutomationPlaywrightArtifactService {

    default Artifact store(String runId, String artifactType, MultipartFile file) {
        return store(runId, artifactType, "", file);
    }

    /**
     * 按 Runner 产物目录中的安全相对路径保存文件。
     */
    Artifact store(String runId, String artifactType, String relativePath, MultipartFile file);

    /** 仅由已完成场景、批次和用例鉴权的结果服务调用，不向上传 Controller 开放自定义目录。 */
    Artifact storeExecutionLog(ExecutionLogContext context, List<?> logs);

    record ExecutionLogContext(String runId, String projectShortName, String versionName, String sceneId,
                               String caseId) {
    }

    /**
     * 读取新链路中由系统文件管理持久化的 Playwright artifact。
     */
    ArtifactResource loadByFileId(Long fileId);

    /**
     * 读取历史 uploads 目录中的 artifact。新文件不再写入该目录。
     */
    ArtifactResource loadLegacy(String runId, String fileName);

    record Artifact(Long fileId, String runId, String artifactType, String relativePath, String fileName, String url,
                    String contentType, long size, String md5, String storageCode) {
    }

    record ArtifactResource(byte[] content, String contentType, String fileName, boolean attachment) {
    }
}
