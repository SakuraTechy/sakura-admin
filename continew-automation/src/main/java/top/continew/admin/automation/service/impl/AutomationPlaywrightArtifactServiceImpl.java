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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.model.entity.AutomationPlaywrightJobDO;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.system.model.entity.FileDO;
import top.continew.admin.system.model.entity.StorageDO;
import top.continew.admin.system.service.FileService;
import top.continew.admin.system.service.StorageService;
import top.continew.starter.core.exception.BusinessException;

/**
 * Playwright Runner 产物存储实现。
 *
 * <p>平台执行产物统一保存到系统文件管理，避免依赖当前 admin 节点的工作目录。历史 uploads
 * 目录仅保留只读兼容能力，不能作为新执行的长期存储。</p>
 *
 * @author Codex
 */
@Service
@RequiredArgsConstructor
public class AutomationPlaywrightArtifactServiceImpl implements AutomationPlaywrightArtifactService {

    private static final String LEGACY_ROOT_DIR = "uploads/automation-playwright-artifacts";
    private static final String URL_PREFIX = "/automation/playwright/artifacts/files/";
    private static final String BUSINESS_TYPE = "PLAYWRIGHT_ARTIFACT";
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,160}");
    private static final Pattern TIME_RUN_ID = Pattern.compile("\\d{14}");
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of("report", Set.of("html"), "console", Set
        .of("json"), "execution-log", Set.of("json"), "video", Set.of("webm"), "trace", Set.of("zip"), "screenshot", Set
            .of("png", "jpg", "jpeg", "webp"), "result", Set.of("json"));

    private final FileService fileService;
    private final StorageService storageService;
    private final FileStorageService fileStorageService;
    private final AutomationPlaywrightJobMapper jobMapper;
    private final AutomationPlaywrightCaseService caseService;

    @Value("${automation.playwright-artifact.unified-storage-enabled:true}")
    private boolean unifiedStorageEnabled;

    @Value("${automation.playwright-artifact.legacy-read-enabled:true}")
    private boolean legacyReadEnabled;

    @Value("${automation.playwright-artifact.storage-code:}")
    private String storageCode;

    @Value("${automation.playwright-artifact.path-prefix:automation/playwright}")
    private String pathPrefix;

    @Override
    public Artifact store(String runId, String artifactType, MultipartFile file) {
        String safeRunId = requireSafeSegment(runId, "执行 ID");
        String safeArtifactType = requireArtifactType(artifactType);
        String extension = requireValidFile(file, safeArtifactType);
        if (!unifiedStorageEnabled) {
            return storeLegacy(safeRunId, safeArtifactType, extension, file);
        }

        String fileName = fileName(safeArtifactType, extension);
        // 目录元数据从已持久化的 Runner Job 反查，避免上传端自行拼接或伪造业务路径。
        ArtifactPathMetadata pathMetadata = resolvePathMetadata(safeRunId);
        FileInfo fileInfo = fileService.upload(file, buildStoragePath(safeRunId, pathMetadata), storageCode, fileName);
        Long fileId = parseFileId(fileInfo.getId());
        Map<String, String> metadata = artifactMetadata(safeRunId, safeArtifactType, pathMetadata);
        // 上传时 FileRecorder 已创建 sys_file；随后补充业务元数据，读取接口据此拒绝普通系统文件。
        fileService.lambdaUpdate()
            .eq(FileDO::getId, fileId)
            .set(FileDO::getMetadata, JSONUtil.toJsonStr(metadata))
            .update();
        return new Artifact(fileId, safeRunId, safeArtifactType, fileName, URL_PREFIX + fileId, fileInfo
            .getContentType(), fileInfo.getSize(), fileInfo.getHashInfo().getMd5(), fileInfo.getPlatform());
    }

    @Override
    public ArtifactResource loadByFileId(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new BusinessException("Playwright artifact 文件 ID 非法");
        }
        FileDO file = fileService.getById(fileId);
        if (file == null || !isPlaywrightArtifact(file.getMetadata())) {
            throw new BusinessException("Playwright artifact 不存在");
        }
        StorageDO storage = storageService.getById(file.getStorageId());
        if (storage == null) {
            throw new BusinessException("Playwright artifact 存储不存在");
        }
        FileInfo fileInfo = file.toFileInfo(storage);
        // 本地存储必须使用上传时记录的相对目录。仅从公开 URL 反推 path 会把域名或文件前缀带入物理路径。
        if (file.getAbsPath() != null && !file.getAbsPath().isBlank()) {
            fileInfo.setPath(file.getAbsPath());
        }
        byte[] content = fileStorageService.download(fileInfo).bytes();
        return new ArtifactResource(content, defaultContentType(file.getContentType()), file.getName() + "." + file
            .getExtension());
    }

    @Override
    public ArtifactResource loadLegacy(String runId, String fileName) {
        String safeRunId = requireSafeSegment(runId, "执行 ID");
        String safeFileName = requireSafeSegment(fileName, "文件名");
        if (!legacyReadEnabled) {
            throw new BusinessException("历史 Playwright artifact 读取已关闭");
        }
        Path runRoot = legacyRoot().resolve(safeRunId).normalize();
        Path target = runRoot.resolve(safeFileName).normalize();
        if (!target.startsWith(runRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException("历史 Playwright artifact 不存在");
        }
        try {
            return new ArtifactResource(Files.readAllBytes(target), resolveContentType(target, null), safeFileName);
        } catch (IOException e) {
            throw new BusinessException("读取历史 Playwright artifact 失败：" + e.getMessage());
        }
    }

    private Artifact storeLegacy(String runId, String artifactType, String extension, MultipartFile file) {
        String fileName = fileName(artifactType, extension);
        Path runRoot = legacyRoot().resolve(runId).normalize();
        Path target = runRoot.resolve(fileName).normalize();
        if (!target.startsWith(runRoot)) {
            throw new BusinessException("Playwright artifact 路径非法");
        }
        try {
            Files.createDirectories(runRoot);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Artifact(null, runId, artifactType, fileName, "/automation/playwright/artifacts/" + runId + "/" + fileName, resolveContentType(target, file
                .getContentType()), Files.size(target), null, null);
        } catch (IOException e) {
            throw new BusinessException("保存历史 Playwright artifact 失败：" + e.getMessage());
        }
    }

    private String buildStoragePath(String runId) {
        return buildStoragePath(runId, new ArtifactPathMetadata("project", "version", "scene", "case"));
    }

    private String buildStoragePath(String runId, ArtifactPathMetadata pathMetadata) {
        String normalizedPrefix = pathPrefix == null ? "automation/playwright" : pathPrefix.replaceAll("^/+|/+$", "");
        if (normalizedPrefix.isBlank() || normalizedPrefix.contains("..")) {
            throw new BusinessException("Playwright artifact 存储目录配置非法");
        }
        LocalDate runDate = TIME_RUN_ID.matcher(runId).matches()
            ? LocalDate.parse(runId.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
            : LocalDate.now();
        return normalizedPrefix + "/" + pathMetadata.projectShortName() + "/" + pathMetadata
            .versionName() + "/" + pathMetadata.sceneId() + "/" + pathMetadata.caseId() + "/" + runDate
                .format(DateTimeFormatter.BASIC_ISO_DATE) + "/" + runId + "/";
    }

    private ArtifactPathMetadata resolvePathMetadata(String runId) {
        AutomationPlaywrightJobDO job = jobMapper.selectOne(Wrappers.<AutomationPlaywrightJobDO>lambdaQuery()
            .eq(AutomationPlaywrightJobDO::getExecutionId, runId)
            .last("LIMIT 1"));
        if (job == null || job.getCaseKey() == null || job.getCaseKey().isBlank()) {
            // 统一存储必须绑定已创建的 Runner Job，不能让上传端伪造 runId 或业务目录。
            throw new BusinessException("Playwright artifact 执行 ID 不存在或尚未初始化：" + runId);
        }
        AutomationPlaywrightCaseResp testCase = caseService.getCase(job.getCaseKey(), job.getProjectEnvironmentId());
        return new ArtifactPathMetadata(requireSafeSegment(firstText(testCase.getProjectShortName(), testCase
            .getProject_short_name(), "project"), "项目标识"), requireSafeSegment(firstText(testCase
                .getVersionName(), testCase
                    .getVersion_name(), "version"), "版本标识"), requireSafeSegment(firstText(testCase.getSceneId(), job
                        .getSceneKey(), "scene"), "场景标识"), requireSafeSegment(firstText(testCase.getCaseId(), job
                            .getCaseId(), "case"), "用例标识"));
    }

    private Map<String, String> artifactMetadata(String runId, String artifactType, ArtifactPathMetadata pathMetadata) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("businessType", BUSINESS_TYPE);
        metadata.put("runId", runId);
        metadata.put("artifactType", artifactType);
        metadata.put("projectShortName", pathMetadata.projectShortName());
        metadata.put("versionName", pathMetadata.versionName());
        metadata.put("sceneId", pathMetadata.sceneId());
        metadata.put("caseId", pathMetadata.caseId());
        metadata.put("source", "sakura-playwright");
        return metadata;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isPlaywrightArtifact(String metadata) {
        if (metadata == null || metadata.isBlank() || !JSONUtil.isTypeJSON(metadata)) {
            return false;
        }
        return BUSINESS_TYPE.equals(JSONUtil.parseObj(metadata).getStr("businessType"));
    }

    private String requireArtifactType(String artifactType) {
        String safeArtifactType = requireSafeSegment(artifactType, "产物类型").toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.containsKey(safeArtifactType)) {
            throw new BusinessException("不支持的 Playwright artifact 类型：" + artifactType);
        }
        return safeArtifactType;
    }

    private String requireValidFile(MultipartFile file, String artifactType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Playwright artifact 文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("Playwright artifact 不能超过 200MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.get(artifactType).contains(extension)) {
            throw new BusinessException("Playwright artifact 文件类型不匹配：" + artifactType + "." + extension);
        }
        return extension;
    }

    private Long parseFileId(String fileId) {
        try {
            return Long.valueOf(fileId);
        } catch (NumberFormatException e) {
            throw new BusinessException("系统文件管理未返回有效文件 ID");
        }
    }

    private Path legacyRoot() {
        return Path.of(System.getProperty("user.dir"), LEGACY_ROOT_DIR).toAbsolutePath().normalize();
    }

    private String requireSafeSegment(String value, String name) {
        if (value == null || !SAFE_SEGMENT.matcher(value).matches() || value.contains("..")) {
            throw new BusinessException(name + "格式非法");
        }
        return value;
    }

    private String fileName(String artifactType, String extension) {
        return artifactType + "." + extension;
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 || index == fileName.length() - 1
            ? ""
            : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(Path path, String fallback) throws IOException {
        String detected = Files.probeContentType(path);
        return defaultContentType(detected == null || detected.isBlank() ? fallback : detected);
    }

    private String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private record ArtifactPathMetadata(String projectShortName, String versionName, String sceneId, String caseId) {
    }
}
