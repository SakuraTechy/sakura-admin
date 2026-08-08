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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

/**
 * 管理 Playwright 批次认证状态的受控临时文件。
 *
 * <p>认证状态可能包含可冒用账号的 Cookie 和 sessionStorage，不能进入 artifact、场景 JSON 或前端请求。</p>
 */
@Slf4j
@Service
public class AutomationPlaywrightSessionStateService {

    private static final List<String> SENSITIVE_OPTIONS = List
        .of("--token", "--execution-capability", "--storage-state", "--storage-state-out");

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    @Value("${automation.playwright-runner.session-state-dir:}")
    private String configuredRoot;

    @Value("${automation.playwright-runner.session-state-retention-hours:24}")
    private long retentionHours;

    public AutomationPlaywrightSessionStateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        ensureRoot();
        cleanupExpired();
        cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpired, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    public SessionFiles prepare(String batchId, Long projectEnvironmentId, String jobId) {
        Path batchDirectory = resolveBatchDirectory(batchId);
        Path environmentDirectory = resolveInsideRoot(batchDirectory.resolve(safeSegment(String
            .valueOf(projectEnvironmentId))));
        Path candidatesDirectory = resolveInsideRoot(environmentDirectory.resolve("candidates"));
        try {
            Files.createDirectories(candidatesDirectory);
        } catch (IOException e) {
            throw new BusinessException("创建 Playwright 批次登录态目录失败：" + e.getMessage());
        }
        return new SessionFiles(environmentDirectory.resolve("current.json"), candidatesDirectory
            .resolve(safeSegment(jobId) + ".json"));
    }

    public boolean hasCurrent(SessionFiles files) {
        return files != null && Files.isRegularFile(files.currentPath());
    }

    public void promote(SessionFiles files) {
        if (files == null) {
            return;
        }
        validateStateFile(files.candidatePath());
        try {
            Files.createDirectories(files.currentPath().getParent());
            try {
                Files.move(files.candidatePath(), files
                    .currentPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(files.candidatePath(), files.currentPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("提交 Playwright 批次登录态失败：" + e.getMessage());
        }
    }

    public void discardCandidate(SessionFiles files) {
        if (files == null) {
            return;
        }
        try {
            Files.deleteIfExists(files.candidatePath());
        } catch (IOException e) {
            log.warn("清理 Playwright 登录态候选文件失败，jobCandidate={}", files.candidatePath().getFileName(), e);
        }
    }

    public void cleanupBatch(String batchId) {
        if (StringUtils.isBlank(batchId)) {
            return;
        }
        deleteDirectory(resolveBatchDirectory(batchId));
    }

    public List<String> redactCommand(List<String> command) {
        java.util.ArrayList<String> redacted = new java.util.ArrayList<>();
        boolean maskNext = false;
        for (String value : command) {
            if (maskNext) {
                redacted.add("***");
                maskNext = false;
                continue;
            }
            int equalsIndex = StringUtils.indexOf(value, '=');
            String option = equalsIndex >= 0 ? value.substring(0, equalsIndex) : value;
            if (!SENSITIVE_OPTIONS.contains(option)) {
                redacted.add(value);
                continue;
            }
            if (equalsIndex >= 0) {
                redacted.add(option + "=***");
            } else {
                redacted.add(option);
                maskNext = true;
            }
        }
        return redacted;
    }

    public void cleanupExpired() {
        Path root = ensureRoot();
        Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1, retentionHours)));
        try (var entries = Files.list(root)) {
            entries.filter(Files::isDirectory).forEach(directory -> {
                try {
                    FileTime modified = Files.getLastModifiedTime(directory);
                    if (modified.toInstant().isBefore(cutoff)) {
                        deleteDirectory(directory);
                    }
                } catch (IOException e) {
                    log.warn("检查 Playwright 过期登录态目录失败，batch={}", directory.getFileName(), e);
                }
            });
        } catch (IOException e) {
            log.warn("扫描 Playwright 登录态目录失败", e);
        }
    }

    private void validateStateFile(Path file) {
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            if (root == null || !root.isObject()) {
                throw new BusinessException("Playwright 登录态候选必须是 JSON 对象");
            }
            if (root.has("cookies") && !root.get("cookies").isArray()) {
                throw new BusinessException("Playwright 登录态 cookies 必须是数组");
            }
            if (root.has("origins") && !root.get("origins").isArray()) {
                throw new BusinessException("Playwright 登录态 origins 必须是数组");
            }
            validateSessionStorageMetadata(root.get("_sakura"));
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("读取 Playwright 登录态候选失败：" + e.getMessage());
        }
    }

    private void validateSessionStorageMetadata(JsonNode metadata) {
        if (metadata == null) {
            return;
        }
        if (!metadata.isObject()) {
            throw new BusinessException("Playwright 登录态私有元数据必须是对象");
        }
        JsonNode sessionStorage = metadata.get("session_storage");
        if (sessionStorage == null) {
            return;
        }
        if (!sessionStorage.isObject() || !sessionStorage.path("origin").isTextual() || !sessionStorage.path("entries")
            .isArray()) {
            throw new BusinessException("Playwright 登录态 sessionStorage 结构无效");
        }
        for (JsonNode entry : sessionStorage.path("entries")) {
            if (!entry.isObject() || !entry.path("name").isTextual() || !entry.path("value").isTextual()) {
                throw new BusinessException("Playwright 登录态 sessionStorage 条目必须包含字符串 name 和 value");
            }
        }
    }

    private Path resolveBatchDirectory(String batchId) {
        return resolveInsideRoot(ensureRoot().resolve(safeSegment(batchId)));
    }

    private Path resolveInsideRoot(Path path) {
        Path root = ensureRoot();
        Path resolved = path.toAbsolutePath().normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new BusinessException("Playwright 登录态路径超出服务端受控目录");
        }
        return resolved;
    }

    private Path ensureRoot() {
        Path root = StringUtils.isBlank(configuredRoot)
            ? Paths.get(System.getProperty("java.io.tmpdir"), "sakura-playwright-sessions")
            : Paths.get(configuredRoot);
        root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BusinessException("创建 Playwright 登录态根目录失败：" + e.getMessage());
        }
        return root;
    }

    private String safeSegment(String value) {
        String safe = StringUtils.defaultString(value).replaceAll("[^A-Za-z0-9._-]", "_");
        if (StringUtils.isBlank(safe) || ".".equals(safe) || "..".equals(safe)) {
            throw new BusinessException("Playwright 登录态路径标识无效");
        }
        return safe;
    }

    private void deleteDirectory(Path directory) {
        Path root = ensureRoot();
        Path resolved = directory.toAbsolutePath().normalize();
        if (resolved.equals(root) || !resolved.startsWith(root) || !Files.exists(resolved)) {
            return;
        }
        try (var entries = Files.walk(resolved)) {
            entries.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new DeleteStateException(e);
                }
            });
        } catch (DeleteStateException | IOException e) {
            log.warn("清理 Playwright 批次登录态目录失败，batch={}", resolved.getFileName(), e);
        }
    }

    public record SessionFiles(Path currentPath, Path candidatePath) {
    }

    private static final class DeleteStateException extends RuntimeException {

        private DeleteStateException(Throwable cause) {
            super(cause);
        }
    }
}
