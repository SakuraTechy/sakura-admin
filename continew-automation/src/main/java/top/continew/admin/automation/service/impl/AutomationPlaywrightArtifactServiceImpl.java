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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService;
import top.continew.starter.core.exception.BusinessException;

/**
 * Playwright Runner 本地产物存储实现。
 *
 * @author Codex
 */
@Service
public class AutomationPlaywrightArtifactServiceImpl implements AutomationPlaywrightArtifactService {

    private static final String ROOT_DIR = "uploads/automation-playwright-artifacts";
    private static final String URL_PREFIX = "/automation/playwright/artifacts";
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,160}");
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of("report", Set.of("html"), "console", Set
        .of("json"), "video", Set.of("webm"), "trace", Set.of("zip"), "screenshot", Set
            .of("png", "jpg", "jpeg", "webp"), "result", Set.of("json"));

    @Override
    public Artifact store(String runId, String artifactType, MultipartFile file) {
        String safeRunId = requireSafeSegment(runId, "执行 ID");
        String safeArtifactType = requireSafeSegment(artifactType, "产物类型").toLowerCase(Locale.ROOT);
        Set<String> allowedExtensions = ALLOWED_EXTENSIONS.get(safeArtifactType);
        if (allowedExtensions == null) {
            throw new BusinessException("不支持的 Playwright 产物类型：" + artifactType);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Playwright 产物文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("Playwright 产物不能超过 200MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException("Playwright 产物文件类型不匹配：" + safeArtifactType + "." + extension);
        }
        String fileName = safeArtifactType + "." + extension;
        Path runRoot = root().resolve(safeRunId).normalize();
        Path target = runRoot.resolve(fileName).normalize();
        if (!target.startsWith(runRoot)) {
            throw new BusinessException("Playwright 产物路径非法");
        }
        try {
            Files.createDirectories(runRoot);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String contentType = resolveContentType(target, file.getContentType());
            String url = URL_PREFIX + "/" + safeRunId + "/" + fileName;
            return new Artifact(safeRunId, safeArtifactType, fileName, url, contentType, Files.size(target));
        } catch (IOException e) {
            throw new BusinessException("保存 Playwright 产物失败：" + e.getMessage());
        }
    }

    @Override
    public ArtifactResource load(String runId, String fileName) {
        String safeRunId = requireSafeSegment(runId, "执行 ID");
        String safeFileName = requireSafeSegment(fileName, "文件名");
        Path runRoot = root().resolve(safeRunId).normalize();
        Path target = runRoot.resolve(safeFileName).normalize();
        if (!target.startsWith(runRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException("Playwright 产物不存在");
        }
        try {
            return new ArtifactResource(target, resolveContentType(target, null));
        } catch (IOException e) {
            throw new BusinessException("读取 Playwright 产物失败：" + e.getMessage());
        }
    }

    private Path root() {
        return Path.of(System.getProperty("user.dir"), ROOT_DIR).toAbsolutePath().normalize();
    }

    private String requireSafeSegment(String value, String name) {
        if (value == null || !SAFE_SEGMENT.matcher(value).matches() || value.contains("..")) {
            throw new BusinessException(name + "格式非法");
        }
        return value;
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(Path path, String fallback) throws IOException {
        String detected = Files.probeContentType(path);
        if (detected != null && !detected.isBlank()) {
            return detected;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "application/octet-stream";
    }
}
