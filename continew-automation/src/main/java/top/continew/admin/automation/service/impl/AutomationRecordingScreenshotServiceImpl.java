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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService;
import top.continew.admin.system.service.FileService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 本地文件化保存录制截图。
 *
 * @author Codex
 */
@Service
public class AutomationRecordingScreenshotServiceImpl implements AutomationRecordingScreenshotService {

    private static final String URL_PREFIX = "/automation/automationUiScene/recordings/screenshots";
    private static final String ROOT_DIR = "uploads/automation-recording-screenshots";
    private static final String FILE_MANAGER_ROOT = "automation/recording-screenshots/";

    private final FileService fileService;

    public AutomationRecordingScreenshotServiceImpl(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public ScreenshotArtifact store(String recordingId,
                                    String projectShortName,
                                    String versionName,
                                    String sceneId,
                                    String caseId,
                                    String stepId,
                                    String screenshot) {
        ParsedScreenshot parsed = parse(screenshot);
        String filePath = businessFilePath(projectShortName, versionName, sceneId, caseId);
        String saveFileName = safeSegment(stepId) + "." + parsed.extension();
        String displayFileName = businessDisplayFileName(projectShortName, versionName, sceneId, caseId, stepId, parsed
            .extension());
        try {
            // 新截图统一进入系统文件管理模块，复用默认存储、资源映射和 sys_file 记录。
            MultipartFile file = new InMemoryMultipartFile("file", displayFileName, parsed.contentType(), parsed
                .bytes());
            FileInfo fileInfo = fileService.upload(file, filePath, null, saveFileName);
            String relativePathText = valueOrEmpty(fileInfo.getPath()) + valueOrEmpty(fileInfo.getFilename());
            return new ScreenshotArtifact(fileInfo.getUrl(), relativePathText, String.valueOf(fileInfo
                .getId()), fileInfo.getThUrl(), parsed.contentType(), parsed.bytes().length);
        } catch (Exception e) {
            throw new BusinessException("录制导入失败：保存截图 artifact 失败：" + e.getMessage());
        }
    }

    @Override
    public ScreenshotResource load(String recordingId, String fileName) {
        Path target = root().resolve(safeSegment(recordingId)).resolve(safeSegment(fileName)).normalize();
        if (!target.startsWith(root()) || !Files.isRegularFile(target)) {
            throw new BusinessException("录制截图不存在");
        }
        try {
            String contentType = Files.probeContentType(target);
            return new ScreenshotResource(target, contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException e) {
            throw new BusinessException("读取录制截图失败：" + e.getMessage());
        }
    }

    private ParsedScreenshot parse(String screenshot) {
        if (screenshot == null || screenshot.isBlank()) {
            throw new BusinessException("录制导入失败：截图内容为空");
        }
        String contentType = "image/png";
        String base64 = screenshot.trim();
        int commaIndex = base64.indexOf(',');
        if (base64.startsWith("data:") && commaIndex > 0) {
            String header = base64.substring(5, commaIndex);
            if (!header.toLowerCase(Locale.ROOT).contains(";base64")) {
                throw new BusinessException("录制导入失败：截图 data URL 必须使用 base64 编码");
            }
            contentType = header.substring(0, header.indexOf(';'));
            base64 = base64.substring(commaIndex + 1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
            return new ParsedScreenshot(contentType, extension(contentType), bytes);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("录制导入失败：截图 base64 解码失败");
        }
    }

    private String extension(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private String safeSegment(String value) {
        String safe = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    private String businessFilePath(String projectShortName, String versionName, String sceneId, String caseId) {
        return FILE_MANAGER_ROOT + safeSegment(projectShortName) + "/" + safeSegment(versionName) + "/" + safeSegment(sceneId) + "/" + safeSegment(caseId) + "/";
    }

    private String businessDisplayFileName(String projectShortName,
                                           String versionName,
                                           String sceneId,
                                           String caseId,
                                           String stepId,
                                           String extension) {
        // 文件管理显示名带完整业务主键，便于人工搜索和反查录制步骤。
        return safeSegment(projectShortName) + "-" + safeSegment(versionName) + "-" + safeSegment(sceneId) + "-" + safeSegment(caseId) + "-" + safeSegment(stepId) + "." + extension;
    }

    private Path root() {
        return Path.of(System.getProperty("user.dir"), ROOT_DIR).toAbsolutePath().normalize();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ParsedScreenshot(String contentType, String extension, byte[] bytes) {
    }

    private record InMemoryMultipartFile(String name, String originalFilename, String contentType,
                                         byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0 : bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes == null ? new byte[0] : bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(getBytes());
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), getBytes());
        }
    }
}
