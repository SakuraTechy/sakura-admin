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
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.mapper.AutomationFileAssetMapper;
import top.continew.admin.automation.model.entity.AutomationFileAssetDO;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.system.enums.StorageTypeEnum;
import top.continew.admin.system.model.entity.StorageDO;
import top.continew.admin.system.service.FileService;
import top.continew.admin.system.service.StorageService;
import top.continew.starter.core.exception.BusinessException;

/**
 * Playwright Runner 证书工作区实现。
 *
 * <p>证书正文只落入执行节点受控目录，步骤 JSON 仅保存相对引用，避免敏感文件进入场景主数据。</p>
 *
 * @author Codex
 */
@Service
@RequiredArgsConstructor
public class AutomationCertificateWorkspaceServiceImpl implements AutomationCertificateWorkspaceService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,160}");
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,159}");
    private static final Set<String> ALLOWED_EXTENSIONS = Set
        .of("lic", "p12", "pfx", "pem", "crt", "cer", "der", "key", "jks", "p7b", "p7c");

    private final AutomationUiSceneMapper sceneMapper;
    private final ProjectConfigMapper projectConfigMapper;
    private final AutomationFileAssetMapper fileAssetMapper;
    private final FileService fileService;
    private final StorageService storageService;

    @Value("${automation.playwright-runner.root:sakura-playwright}")
    private String runnerRoot;

    @Override
    public CertificateFile upload(Long sceneDbId, MultipartFile file) {
        AutomationUiSceneDO scene = requireScene(sceneDbId);
        String fileName = requireFile(file);
        Path root = resolveRunnerRoot();
        Path licenseDirectory = resolveLicenseDirectory(root, scene);
        Path target = licenseDirectory.resolve(fileName).normalize();
        if (!target.startsWith(licenseDirectory) || Files.isSymbolicLink(target)) {
            throw new BusinessException("证书工作区目标路径非法");
        }
        writeFile(file, target);
        String reference = root.relativize(target).toString().replace('\\', '/');
        return new CertificateFile(reference, fileName, file.getSize());
    }

    @Override
    public CertificateAsset uploadAsset(Long projectId, String versionName, MultipartFile file) {
        ProjectConfigDO project = projectId == null ? null : projectConfigMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("证书所属项目不存在");
        }
        String fileName = requireFile(file);
        String projectShortName = firstText(project.getAbbreviate(), project.getName());
        requireSafeSegment(projectShortName, "项目简称");
        requireSafeSegment(versionName, "版本名称");
        String storagePath = "automation/" + projectShortName + "/" + versionName + "/license/";
        String storageKey = storagePath + fileName;
        String sha256 = sha256(file);
        AutomationFileAssetDO existing = fileAssetMapper.selectOne(Wrappers.<AutomationFileAssetDO>lambdaQuery()
            .eq(AutomationFileAssetDO::getStorageKey, storageKey));
        // 相同内容重复提交时复用资产，避免文件管理记录与自动化资产元数据重复。
        if (existing != null && sha256.equals(existing.getSha256()) && "ACTIVE".equals(existing.getStatus())) {
            return new CertificateAsset(existing.getId(), existing.getOriginalName(), existing.getSize(), existing
                .getSha256());
        }
        FileInfo stored = fileService.upload(file, storagePath, null, fileName);
        storageKey = (stored.getPath() + stored.getFilename()).replace('\\', '/');

        if (existing != null) {
            existing.setProjectId(projectId);
            existing.setAssetKind("CERTIFICATE");
            existing.setOriginalName(fileName);
            existing.setStorageKey(storageKey);
            existing.setSha256(sha256);
            existing.setSize(stored.getSize());
            existing.setContentType(stored.getContentType());
            existing.setStatus("ACTIVE");
            fileAssetMapper.updateById(existing);
            return new CertificateAsset(existing.getId(), fileName, stored.getSize(), sha256);
        }

        AutomationFileAssetDO asset = new AutomationFileAssetDO();
        asset.setProjectId(projectId);
        asset.setAssetKind("CERTIFICATE");
        asset.setOriginalName(fileName);
        asset.setStorageKey(storageKey);
        asset.setSha256(sha256);
        asset.setSize(stored.getSize());
        asset.setContentType(stored.getContentType());
        asset.setStatus("ACTIVE");
        try {
            fileAssetMapper.insert(asset);
        } catch (DuplicateKeyException ignored) {
            // 并发上传同一路径时由唯一键收敛，再读取既有资产供环境绑定使用。
            AutomationFileAssetDO concurrentAsset = fileAssetMapper.selectOne(Wrappers
                .<AutomationFileAssetDO>lambdaQuery()
                .eq(AutomationFileAssetDO::getStorageKey, storageKey));
            if (concurrentAsset == null) {
                throw ignored;
            }
            return new CertificateAsset(concurrentAsset.getId(), concurrentAsset.getOriginalName(), concurrentAsset
                .getSize(), concurrentAsset.getSha256());
        }
        return new CertificateAsset(asset.getId(), fileName, stored.getSize(), sha256);
    }

    @Override
    public String runnerReference(Long assetId, Long projectId) {
        return assetPath(assetId, projectId).toString();
    }

    @Override
    public Path assetPath(Long assetId, Long projectId) {
        AutomationFileAssetDO asset = requireAsset(assetId, projectId);
        StorageDO storage = storageService.getDefaultStorage();
        if (storage == null || !StorageTypeEnum.LOCAL.equals(storage.getType())) {
            throw new BusinessException("证书执行要求默认存储为本地文件存储");
        }
        Path root = Path.of(storage.getBucketName()).toAbsolutePath().normalize();
        Path target = root.resolve(asset.getStorageKey()).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("证书资产文件不存在或已超出受控目录");
        }
        return target;
    }

    private AutomationFileAssetDO requireAsset(Long assetId, Long projectId) {
        AutomationFileAssetDO asset = assetId == null ? null : fileAssetMapper.selectById(assetId);
        if (asset == null || !projectId.equals(asset.getProjectId()) || !"CERTIFICATE".equals(asset
            .getAssetKind()) || !"ACTIVE".equals(asset.getStatus())) {
            throw new BusinessException("证书资产不存在、已停用或不属于当前项目");
        }
        return asset;
    }

    private String sha256(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("计算证书摘要失败：" + e.getMessage());
        }
    }

    private String sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("计算证书摘要失败：" + e.getMessage());
        }
    }

    private AutomationUiSceneDO requireScene(Long sceneDbId) {
        if (sceneDbId == null || sceneDbId <= 0) {
            throw new BusinessException("场景 ID 非法");
        }
        AutomationUiSceneDO scene = sceneMapper.selectById(sceneDbId);
        if (scene == null) {
            throw new BusinessException("场景不存在，无法确定证书工作区");
        }
        return scene;
    }

    private String requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("证书文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("证书文件不能超过 10MB");
        }
        String originalFileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (!SAFE_FILE_NAME.matcher(originalFileName).matches() || originalFileName.contains("..")) {
            throw new BusinessException("证书文件名只能包含字母、数字、点、横线和下划线");
        }
        String extension = extension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的证书文件类型：" + extension);
        }
        return originalFileName;
    }

    private Path resolveLicenseDirectory(Path root, AutomationUiSceneDO scene) {
        ProjectConfigDO project = scene.getProjectId() == null
            ? null
            : projectConfigMapper.selectById(scene.getProjectId());
        String projectShortName = firstText(project == null ? null : project.getAbbreviate(), project == null
            ? null
            : project.getName(), scene.getProjectName());
        String versionName = firstText(scene.getVersionName());
        requireSafeSegment(projectShortName, "项目简称");
        requireSafeSegment(versionName, "版本名称");
        Path licenseDirectory = root.resolve("data")
            .resolve(projectShortName)
            .resolve(versionName)
            .resolve("License")
            .normalize();
        if (!licenseDirectory.startsWith(root)) {
            throw new BusinessException("证书工作区目录非法");
        }
        try {
            Files.createDirectories(licenseDirectory);
            Path realRoot = root.toRealPath();
            Path realDirectory = licenseDirectory.toRealPath();
            if (!realDirectory.startsWith(realRoot)) {
                throw new BusinessException("证书工作区目录超出 Runner 根目录");
            }
            return realDirectory;
        } catch (IOException e) {
            throw new BusinessException("创建证书工作区失败：" + e.getMessage());
        }
    }

    private Path resolveRunnerRoot() {
        Path configured = Path.of(runnerRoot);
        if (configured.isAbsolute()) {
            return requireRunnerRoot(configured.normalize());
        }
        Path userDirectory = Path.of(System.getProperty("user.dir"));
        Set<Path> candidates = new LinkedHashSet<>();
        candidates.add(userDirectory.resolve(configured).normalize());
        candidates.add(userDirectory.resolve("sakura-playwright").normalize());
        candidates.add(userDirectory.resolve("../sakura-playwright").normalize());
        candidates.add(userDirectory.resolve("../../sakura-playwright").normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate
                .resolve("src/index.js"), LinkOption.NOFOLLOW_LINKS)) {
                return requireRunnerRoot(candidate);
            }
        }
        return requireRunnerRoot(userDirectory.resolve(configured).normalize());
    }

    private Path requireRunnerRoot(Path root) {
        if (!Files.isDirectory(root) || !Files.isRegularFile(root.resolve("src/index.js"), LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("Playwright Runner 目录不存在或未安装：" + root);
        }
        try {
            return root.toRealPath();
        } catch (IOException e) {
            throw new BusinessException("读取 Playwright Runner 目录失败：" + e.getMessage());
        }
    }

    private void writeFile(MultipartFile file, Path target) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), "certificate-", ".upload");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            replaceFile(temporary, target);
        } catch (IOException e) {
            throw new BusinessException("保存证书到执行节点失败：" + e.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 临时文件清理失败不覆盖原始上传异常。
                }
            }
        }
    }

    private void replaceFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void requireSafeSegment(String value, String name) {
        if (value.isBlank() || !SAFE_SEGMENT.matcher(value).matches() || value.contains("..")) {
            throw new BusinessException(name + "不能用于证书工作区路径：" + value);
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 || index == fileName.length() - 1
            ? ""
            : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
