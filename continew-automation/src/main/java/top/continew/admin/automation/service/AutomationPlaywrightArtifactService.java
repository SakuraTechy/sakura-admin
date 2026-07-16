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

import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

/**
 * Playwright Runner 产物存储服务。
 *
 * @author Codex
 */
public interface AutomationPlaywrightArtifactService {

    Artifact store(String runId, String artifactType, MultipartFile file);

    ArtifactResource load(String runId, String fileName);

    record Artifact(String runId, String artifactType, String fileName, String url, String contentType, long size) {
    }

    record ArtifactResource(Path path, String contentType) {
    }
}
