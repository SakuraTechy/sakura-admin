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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.starter.core.exception.BusinessException;

/**
 * 管理同一批次跨 Runner Job 复用的 Playwright 浏览器宿主。
 *
 * <p>WebSocket 端点等同于本机浏览器控制凭据，只通过子进程环境变量传递，不能进入日志、场景数据或前端响应。</p>
 */
@Slf4j
@Service
public class AutomationPlaywrightBrowserSessionService {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();

    @Value("${automation.playwright-runner.browser-session-dir:}")
    private String configuredRoot;

    public AutomationPlaywrightBrowserSessionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String acquire(String batchId,
                          Long projectEnvironmentId,
                          AutomationPlaywrightRunnerOptionsReq options,
                          Path runnerRoot,
                          String nodeCommand) {
        String normalizedBatchId = requireSegment(batchId, "批次 ID");
        String environmentId = requireSegment(String.valueOf(projectEnvironmentId), "产品环境 ID");
        BrowserSessionConfig requestedConfig = BrowserSessionConfig.from(options);
        synchronized (sessions) {
            BrowserSession existing = sessions.get(normalizedBatchId);
            if (existing != null && existing.process().isAlive()) {
                if (!environmentId.equals(existing.projectEnvironmentId()) || !requestedConfig.equals(existing
                    .config())) {
                    throw new BusinessException("同一 reuse-browser 批次不能切换产品环境或浏览器启动配置");
                }
                return existing.endpoint();
            }
            if (existing != null) {
                sessions.remove(normalizedBatchId);
                cleanup(existing);
            }
            BrowserSession created = start(normalizedBatchId, environmentId, requestedConfig, runnerRoot, nodeCommand);
            sessions.put(normalizedBatchId, created);
            return created.endpoint();
        }
    }

    public void release(String batchId) {
        if (StringUtils.isBlank(batchId)) {
            return;
        }
        String normalizedBatchId = requireSegment(batchId, "批次 ID");
        BrowserSession session;
        synchronized (sessions) {
            session = sessions.remove(normalizedBatchId);
        }
        cleanup(session);
    }

    public Optional<Path> sessionDirectory(String batchId) {
        if (StringUtils.isBlank(batchId)) {
            return Optional.empty();
        }
        BrowserSession session = sessions.get(requireSegment(batchId, "批次 ID"));
        return session == null ? Optional.empty() : Optional.of(session.directory());
    }

    /**
     * 终态批次先关闭共享 Context 生成完整原生 WebM，再由 Node finalizer 按用例时间切片并回传结果。
     */
    public void finalizeBatchVideos(String batchId,
                                    Path runnerRoot,
                                    String nodeCommand,
                                    String token,
                                    String executionCapability) {
        if (StringUtils.isBlank(batchId) || runnerRoot == null || StringUtils.isBlank(nodeCommand)) {
            return;
        }
        BrowserSession session = sessions.get(requireSegment(batchId, "批次 ID"));
        if (session == null) {
            return;
        }
        terminateProcessTree(session.process());
        Process finalizer = null;
        try {
            List<String> command = List.of(nodeCommand, "src/batch-video-finalizer.js", "--session-dir", session
                .directory()
                .toString());
            Path outputFile = session.directory().resolve("batch-video-finalizer.log");
            ProcessBuilder builder = new ProcessBuilder(command).directory(runnerRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile());
            Map<String, String> environment = builder.environment();
            environment.put("SAKURA_ADMIN_API", "true");
            // 保留旧变量，兼容未升级的 Runner 子进程和外部脚本。
            environment.put("CUECAST_ADMIN_API", "true");
            if (StringUtils.isNotBlank(token)) {
                environment.put("CUECAST_TOKEN", token);
            }
            if (StringUtils.isNotBlank(executionCapability)) {
                environment.put("CUECAST_EXECUTION_CAPABILITY", executionCapability);
            }
            finalizer = builder.start();
            boolean finished = finalizer.waitFor(120, TimeUnit.SECONDS);
            String output = readOutput(outputFile);
            if (!finished || finalizer.exitValue() != 0) {
                log.warn("Playwright 批次视频切片失败，batchId={} output={}", batchId, output);
            } else {
                log.info("Playwright 批次视频切片完成，batchId={} output={}", batchId, output);
            }
        } catch (IOException e) {
            log.warn("启动 Playwright 批次视频切片失败，batchId={}", batchId, e);
            if (finalizer != null) {
                finalizer.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (finalizer != null) {
                finalizer.destroyForcibly();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        List<BrowserSession> active;
        synchronized (sessions) {
            active = new ArrayList<>(sessions.values());
            sessions.clear();
        }
        active.forEach(this::cleanup);
    }

    private BrowserSession start(String batchId,
                                 String projectEnvironmentId,
                                 BrowserSessionConfig config,
                                 Path runnerRoot,
                                 String nodeCommand) {
        Path hostEntry = runnerRoot.resolve("src/browser-session-host.js");
        if (!Files.isRegularFile(hostEntry)) {
            throw new BusinessException("Playwright 共享浏览器宿主入口不存在：" + hostEntry);
        }
        Path directory = resolveSessionDirectory(batchId, projectEnvironmentId);
        Path endpointFile = directory.resolve("endpoint.json");
        Path outputFile = directory.resolve("host.log");
        Process process = null;
        try {
            Files.createDirectories(directory);
            List<String> command = List.of(nodeCommand, "src/browser-session-host.js", "--endpoint-file", endpointFile
                .toString(), "--browser", config.browser(), "--headed", String.valueOf(config
                    .headed()), "--ignore-https-errors", String.valueOf(config.ignoreHttpsErrors()), "--slow-mo", String
                        .valueOf(config.slowMoMs()), "--video", config.video(), "--recording-manifest-file", directory
                            .resolve("recording.json")
                            .toString());
            process = new ProcessBuilder(command).directory(runnerRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
            String endpoint = waitForEndpoint(process, endpointFile, outputFile);
            return new BrowserSession(projectEnvironmentId, config, directory, endpoint, process);
        } catch (BusinessException e) {
            terminateProcessTree(process);
            deleteDirectory(directory);
            throw e;
        } catch (IOException e) {
            terminateProcessTree(process);
            deleteDirectory(directory);
            throw new BusinessException("启动 Playwright 共享浏览器失败：" + e.getMessage());
        }
    }

    private String waitForEndpoint(Process process, Path endpointFile, Path outputFile) {
        Instant deadline = Instant.now().plus(START_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new BusinessException("Playwright 共享浏览器启动失败：" + readOutput(outputFile));
            }
            if (Files.isRegularFile(endpointFile)) {
                try {
                    JsonNode root = objectMapper.readTree(endpointFile.toFile());
                    String endpoint = root == null ? "" : root.path("endpoint").asText("");
                    validateEndpoint(endpoint);
                    return endpoint;
                } catch (IOException e) {
                    // 宿主使用原子重命名写入；短暂读取失败继续等待，超时后给出完整诊断。
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminateProcessTree(process);
                throw new BusinessException("等待 Playwright 共享浏览器启动时被中断");
            }
        }
        terminateProcessTree(process);
        throw new BusinessException("Playwright 共享浏览器启动超时：" + readOutput(outputFile));
    }

    private void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            boolean loopback = "ws".equalsIgnoreCase(uri.getScheme()) && List.of("127.0.0.1", "localhost", "::1")
                .contains(StringUtils.lowerCase(uri.getHost()));
            if (!loopback || uri.getPort() <= 0) {
                throw new IllegalArgumentException("not loopback");
            }
        } catch (RuntimeException e) {
            throw new BusinessException("Playwright 共享浏览器返回了无效的本机端点");
        }
    }

    private void cleanup(BrowserSession session) {
        if (session == null) {
            return;
        }
        terminateProcessTree(session.process());
        deleteDirectory(session.directory());
    }

    private void terminateProcessTree(Process process) {
        if (process == null) {
            return;
        }
        if (process.isAlive()) {
            try {
                process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                process.waitFor(3, TimeUnit.SECONDS);
            } catch (IOException e) {
                log.debug("Playwright 浏览器宿主未能优雅停止", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<ProcessHandle> descendants = process.descendants().toList();
        process.destroy();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (int index = descendants.size() - 1; index >= 0; index--) {
            ProcessHandle descendant = descendants.get(index);
            if (descendant.isAlive()) {
                descendant.destroyForcibly();
            }
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private Path resolveSessionDirectory(String batchId, String projectEnvironmentId) {
        Path root = ensureRoot();
        Path resolved = root.resolve(batchId).resolve(projectEnvironmentId).toAbsolutePath().normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new BusinessException("Playwright 共享浏览器目录超出服务端受控范围");
        }
        return resolved;
    }

    private Path ensureRoot() {
        Path root = StringUtils.isBlank(configuredRoot)
            ? Paths.get(System.getProperty("java.io.tmpdir"), "sakura-playwright-browser-sessions")
            : Paths.get(configuredRoot);
        root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BusinessException("创建 Playwright 共享浏览器根目录失败：" + e.getMessage());
        }
        return root;
    }

    private String requireSegment(String value, String label) {
        String safe = StringUtils.trimToEmpty(value).replaceAll("[^A-Za-z0-9._-]", "_");
        if (StringUtils.isBlank(safe) || ".".equals(safe) || "..".equals(safe)) {
            throw new BusinessException("Playwright 共享浏览器" + label + "无效");
        }
        return safe;
    }

    private String readOutput(Path outputFile) {
        try {
            String output = Files.readString(outputFile).trim();
            return output.length() <= 2000 ? output : output.substring(output.length() - 2000);
        } catch (IOException e) {
            return "未生成宿主日志";
        }
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
                    log.warn("清理 Playwright 共享浏览器文件失败，path={}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("清理 Playwright 共享浏览器目录失败，directory={}", resolved, e);
        }
    }

    private record BrowserSession(String projectEnvironmentId, BrowserSessionConfig config, Path directory,
                                  String endpoint, Process process) {
    }

    private record BrowserSessionConfig(String browser, boolean headed, boolean ignoreHttpsErrors, int slowMoMs,
                                        String video) {

        private static BrowserSessionConfig from(AutomationPlaywrightRunnerOptionsReq options) {
            return new BrowserSessionConfig(options.getBrowser(), Boolean.TRUE.equals(options.getHeaded()), Boolean.TRUE
                .equals(options.getIgnoreHttpsErrors()), options.getSlowMoMs(), options.getVideo());
        }
    }
}
