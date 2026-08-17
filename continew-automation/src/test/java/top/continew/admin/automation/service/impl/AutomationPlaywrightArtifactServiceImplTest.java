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
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.model.entity.AutomationPlaywrightJobDO;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService.Artifact;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.system.service.FileService;
import top.continew.admin.system.service.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Playwright Runner 产物存储测试。
 */
class AutomationPlaywrightArtifactServiceImplTest {

    private final AutomationPlaywrightJobMapper jobMapper = mock(AutomationPlaywrightJobMapper.class);
    private final AutomationPlaywrightCaseService caseService = mock(AutomationPlaywrightCaseService.class);
    private final AutomationPlaywrightArtifactServiceImpl service = new AutomationPlaywrightArtifactServiceImpl(mock(FileService.class), mock(StorageService.class), mock(FileStorageService.class), jobMapper, caseService);
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

        useLegacyStorage();
        Artifact artifact = service.store(runId, "report", file);

        assertThat(artifact.fileName()).isEqualTo("report.html");
        assertThat(artifact.url()).isEqualTo("/automation/playwright/artifacts/" + runId + "/report.html");
        assertThat(service.loadLegacy(runId, artifact.fileName()).content()).isNotEmpty();
    }

    @Test
    void shouldStoreStructuredExecutionLogArtifact() {
        MockMultipartFile file = new MockMultipartFile("file", "execution-log.json", "application/json", "[{\"sequence\":1}]"
            .getBytes());

        useLegacyStorage();
        Artifact artifact = service.store(runId, "execution-log", file);

        assertThat(artifact.fileName()).isEqualTo("execution-log.json");
        assertThat(artifact.url()).isEqualTo("/automation/playwright/artifacts/" + runId + "/execution-log.json");
    }

    @Test
    void shouldPreserveDownloadRelativePathAndOriginalFileName() {
        MockMultipartFile file = new MockMultipartFile("file", "clientInfoFile47628A57FE84D04A.info", "multipart/form-data", new byte[] {
            1, 2, 3});

        useLegacyStorage();
        Artifact artifact = service.store(runId, "download", "downloads/clientInfoFile47628A57FE84D04A.info", file);

        assertThat(artifact.relativePath()).isEqualTo("downloads/clientInfoFile47628A57FE84D04A.info");
        assertThat(artifact.fileName()).isEqualTo("clientInfoFile47628A57FE84D04A.info");
        assertThat(artifact.url())
            .isEqualTo("/automation/playwright/artifacts/" + runId + "/downloads/clientInfoFile47628A57FE84D04A.info");
        assertThat(service.loadLegacy(runId, artifact.relativePath()).attachment()).isTrue();
        assertThat(Files.isRegularFile(Path.of(System
            .getProperty("user.dir"), "uploads", "automation-playwright-artifacts", runId, "downloads", artifact
                .fileName()))).isTrue();
    }

    @Test
    void shouldRejectPathTraversalAndMismatchedExtension() {
        MockMultipartFile invalidType = new MockMultipartFile("file", "report.exe", "application/octet-stream", new byte[] {
            1});

        assertThatThrownBy(() -> service.store(runId, "report", invalidType)).hasMessageContaining("文件类型不匹配");
        assertThatThrownBy(() -> service.store(runId, "download", "../report.exe", invalidType))
            .hasMessageContaining("相对路径非法");
        assertThatThrownBy(() -> service.loadLegacy("../outside", "report.html")).hasMessageContaining("格式非法");
        assertThatThrownBy(() -> service.loadLegacy(runId, "../report.html")).hasMessageContaining("相对路径非法");
    }

    @Test
    void shouldRejectArtifactForUnknownRunnerExecution() {
        ReflectionTestUtils.setField(service, "unifiedStorageEnabled", true);
        MockMultipartFile file = new MockMultipartFile("file", "report.html", "text/html", "<html></html>".getBytes());

        assertThatThrownBy(() -> service.store(runId, "report", file)).hasMessageContaining("执行 ID 不存在或尚未初始化");
    }

    @Test
    void shouldBuildDatePartitionForTimestampRunId() {
        ReflectionTestUtils.setField(service, "pathPrefix", "automation/playwright");
        String storagePath = (String)ReflectionTestUtils.invokeMethod(service, "buildStoragePath", "20260722120022");

        assertThat(storagePath).isEqualTo("automation/playwright/project/version/scene/case/20260722/20260722120022/");
    }

    @Test
    void shouldResolveStructuredPathFromRunnerJobMetadata() {
        String runId = "20260724165936";
        AutomationPlaywrightJobDO job = new AutomationPlaywrightJobDO();
        job.setExecutionId(runId);
        job.setCaseKey("AAS_P_SMOKE_008:SCENE_CASE_001");
        job.setProjectEnvironmentId(47L);
        job.setSceneKey("AAS_P_SMOKE_008");
        job.setCaseId("SCENE_CASE_001");
        AutomationPlaywrightCaseResp testCase = new AutomationPlaywrightCaseResp();
        testCase.setProjectShortName("AAS_P");
        testCase.setVersionName("V6.5B06D011");
        testCase.setSceneId("AAS_P_SMOKE_008");
        testCase.setCaseId("SCENE_CASE_001");
        when(jobMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(job);
        when(caseService.getCase(job.getCaseKey(), job.getProjectEnvironmentId())).thenReturn(testCase);

        Object pathMetadata = ReflectionTestUtils.invokeMethod(service, "resolvePathMetadata", runId);
        String storagePath = (String)ReflectionTestUtils.invokeMethod(service, "buildStoragePath", runId, pathMetadata);

        assertThat(storagePath)
            .isEqualTo("automation/playwright/AAS_P/V6.5B06D011/AAS_P_SMOKE_008/SCENE_CASE_001/20260724/20260724165936/");
    }

    private void useLegacyStorage() {
        ReflectionTestUtils.setField(service, "unifiedStorageEnabled", false);
        ReflectionTestUtils.setField(service, "legacyReadEnabled", true);
    }
}
