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

package top.continew.admin.automation.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService.Artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Playwright Runner 产物存储测试。
 */
class AutomationPlaywrightArtifactServiceImplTest {

    private final AutomationPlaywrightArtifactServiceImpl service = new AutomationPlaywrightArtifactServiceImpl();
    private final String runId = "artifact-test-" + UUID.randomUUID();

    @AfterEach
    void cleanRunDirectory() throws IOException {
        Path runRoot = Path.of(System.getProperty("user.dir"), "uploads", "automation-playwright-artifacts", runId);
        if (!Files.exists(runRoot)) {
            return;
        }
        try (var paths = Files.walk(runRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 测试清理失败不应掩盖断言结果。
                }
            });
        }
    }

    @Test
    void shouldStoreAndLoadAllowedArtifact() {
        MockMultipartFile file = new MockMultipartFile("file", "report.html", "text/html", "<html></html>".getBytes());

        Artifact artifact = service.store(runId, "report", file);

        assertThat(artifact.fileName()).isEqualTo("report.html");
        assertThat(artifact.url()).isEqualTo("/automation/playwright/artifacts/" + runId + "/report.html");
        assertThat(service.load(runId, artifact.fileName()).path()).isRegularFile();
    }

    @Test
    void shouldRejectPathTraversalAndMismatchedExtension() {
        MockMultipartFile invalidType = new MockMultipartFile("file", "report.exe", "application/octet-stream", new byte[] {
            1});

        assertThatThrownBy(() -> service.store(runId, "report", invalidType)).hasMessageContaining("文件类型不匹配");
        assertThatThrownBy(() -> service.load("../outside", "report.html")).hasMessageContaining("格式非法");
        assertThatThrownBy(() -> service.load(runId, "../report.html")).hasMessageContaining("格式非法");
    }
}
