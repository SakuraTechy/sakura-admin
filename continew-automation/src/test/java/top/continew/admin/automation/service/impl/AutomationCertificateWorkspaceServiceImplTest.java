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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.dromara.x.file.storage.core.FileInfo;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.mapper.AutomationFileAssetMapper;
import top.continew.admin.automation.model.entity.AutomationFileAssetDO;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService.CertificateAsset;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService.CertificateFile;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.system.service.FileService;
import top.continew.admin.system.service.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Playwright Runner 证书工作区测试。
 */
class AutomationCertificateWorkspaceServiceImplTest {

    private final AutomationUiSceneMapper sceneMapper = mock(AutomationUiSceneMapper.class);
    private final ProjectConfigMapper projectConfigMapper = mock(ProjectConfigMapper.class);
    private final AutomationFileAssetMapper fileAssetMapper = mock(AutomationFileAssetMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final StorageService storageService = mock(StorageService.class);
    private final AutomationCertificateWorkspaceServiceImpl service = new AutomationCertificateWorkspaceServiceImpl(sceneMapper, projectConfigMapper, fileAssetMapper, fileService, storageService);

    @TempDir
    Path runnerRoot;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(runnerRoot.resolve("src"));
        Files.writeString(runnerRoot.resolve("src/index.js"), "");
        ReflectionTestUtils.setField(service, "runnerRoot", runnerRoot.toString());

        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(7L);
        scene.setProjectId(11L);
        scene.setProjectName("AAS");
        scene.setVersionName("V6.5B05SP001");
        ProjectConfigDO project = new ProjectConfigDO();
        project.setAbbreviate("AAS");
        when(sceneMapper.selectById(7L)).thenReturn(scene);
        when(projectConfigMapper.selectById(11L)).thenReturn(project);
    }

    @Test
    void shouldUploadCertificateAndReturnRunnerRelativeReference() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "172_19_5_45_audit.lic", "application/octet-stream", "license"
            .getBytes());

        CertificateFile uploaded = service.upload(7L, file);

        assertThat(uploaded.reference()).isEqualTo("data/AAS/V6.5B05SP001/License/172_19_5_45_audit.lic");
        assertThat(Files.readString(runnerRoot.resolve(uploaded.reference()))).isEqualTo("license");
    }

    @Test
    void shouldRejectTraversalAndUnsupportedFiles() {
        MockMultipartFile traversal = new MockMultipartFile("file", "../client.lic", "application/octet-stream", new byte[] {
            1});
        MockMultipartFile executable = new MockMultipartFile("file", "client.exe", "application/octet-stream", new byte[] {
            1});

        assertThatThrownBy(() -> service.upload(7L, traversal)).hasMessageContaining("证书文件名");
        assertThatThrownBy(() -> service.upload(7L, executable)).hasMessageContaining("不支持的证书文件类型");
    }

    @Test
    void shouldStoreEnvironmentCertificateInSystemFileManagerProjectVersionLicenseDirectory() {
        MockMultipartFile file = new MockMultipartFile("file", "172_19_5_45_audit.lic", "application/octet-stream", "license"
            .getBytes());
        FileInfo stored = new FileInfo();
        stored.setPath("automation/AAS_P/V6.5B06D011/license/");
        stored.setFilename("172_19_5_45_audit.lic");
        stored.setSize(file.getSize());
        stored.setContentType(file.getContentType());
        ProjectConfigDO project = new ProjectConfigDO();
        project.setAbbreviate("AAS_P");
        when(projectConfigMapper.selectById(12L)).thenReturn(project);
        when(fileService.upload(file, "automation/AAS_P/V6.5B06D011/license/", null, "172_19_5_45_audit.lic"))
            .thenReturn(stored);

        CertificateAsset uploaded = service.uploadAsset(12L, "V6.5B06D011", file);

        assertThat(uploaded.fileName()).isEqualTo("172_19_5_45_audit.lic");
        ArgumentCaptor<AutomationFileAssetDO> assetCaptor = ArgumentCaptor.forClass(AutomationFileAssetDO.class);
        verify(fileAssetMapper).insert(assetCaptor.capture());
        assertThat(assetCaptor.getValue().getStorageKey())
            .isEqualTo("automation/AAS_P/V6.5B06D011/license/172_19_5_45_audit.lic");
        assertThat(assetCaptor.getValue().getSha256())
            .isEqualTo("cc1d3b0234846714b0aeda6cc34b057b4305bb83dd447fb88f816efeb59a4e96");
    }

    @Test
    void shouldReuseExistingAssetWhenCertificateIsUploadedAgainWithoutChanges() {
        MockMultipartFile file = new MockMultipartFile("file", "172_19_5_45_audit.lic", "application/octet-stream", "license"
            .getBytes());
        ProjectConfigDO project = new ProjectConfigDO();
        project.setAbbreviate("AAS_P");
        when(projectConfigMapper.selectById(12L)).thenReturn(project);
        AutomationFileAssetDO existing = new AutomationFileAssetDO();
        existing.setId(91L);
        existing.setOriginalName("172_19_5_45_audit.lic");
        existing.setSize(file.getSize());
        existing.setSha256("cc1d3b0234846714b0aeda6cc34b057b4305bb83dd447fb88f816efeb59a4e96");
        existing.setStatus("ACTIVE");
        when(fileAssetMapper.selectOne(any())).thenReturn(existing);

        CertificateAsset uploaded = service.uploadAsset(12L, "V6.5B06D011", file);

        assertThat(uploaded.assetId()).isEqualTo(91L);
        verify(fileService, never()).upload(any(), any(), any(), any());
        verify(fileAssetMapper, never()).insert(any(AutomationFileAssetDO.class));
    }

    @Test
    void shouldUpdateExistingAssetWhenCertificateContentChanges() {
        MockMultipartFile file = new MockMultipartFile("file", "172_19_5_45_audit.lic", "application/octet-stream", "license-v2"
            .getBytes());
        ProjectConfigDO project = new ProjectConfigDO();
        project.setAbbreviate("AAS_P");
        when(projectConfigMapper.selectById(12L)).thenReturn(project);
        AutomationFileAssetDO existing = new AutomationFileAssetDO();
        existing.setId(91L);
        existing.setSha256("old-sha256");
        existing.setStatus("ACTIVE");
        when(fileAssetMapper.selectOne(any())).thenReturn(existing);
        FileInfo stored = new FileInfo();
        stored.setPath("automation/AAS_P/V6.5B06D011/license/");
        stored.setFilename("172_19_5_45_audit.lic");
        stored.setSize(file.getSize());
        stored.setContentType(file.getContentType());
        when(fileService.upload(file, "automation/AAS_P/V6.5B06D011/license/", null, "172_19_5_45_audit.lic"))
            .thenReturn(stored);

        CertificateAsset uploaded = service.uploadAsset(12L, "V6.5B06D011", file);

        assertThat(uploaded.assetId()).isEqualTo(91L);
        assertThat(existing.getSha256()).isEqualTo(uploaded.sha256()).isNotEqualTo("old-sha256");
        verify(fileAssetMapper).updateById(existing);
        verify(fileAssetMapper, never()).insert(any(AutomationFileAssetDO.class));
    }
}
