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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.validation.CheckUtils;

/**
 * 在 admin 服务所在节点启动 Playwright Runner CLI。
 *
 * <p>Runner 仍通过受保护的 case/result API 读写数据，admin 这里只负责任务生命周期和进程隔离，
 * 不把 Playwright 执行逻辑复制到 Java 服务中。</p>
 */
@Slf4j
@Service
public class AutomationPlaywrightRunnerJobServiceImpl implements AutomationPlaywrightRunnerJobService {

    private static final int MAX_OUTPUT_LINES = 200;
    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PLATFORM_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> TERMINAL_STATUSES = List.of("passed", "failed", "cancelled");
    private static final Set<String> BROWSERS = Set.of("chromium", "firefox", "webkit");
    private static final Set<String> ARTIFACT_POLICIES = Set.of("off", "on", "retain-on-failure");

    private final AutomationPlaywrightCaseService caseService;
    private final Map<String, JobRuntime> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${automation.playwright-runner.enabled:true}")
    private boolean enabled;

    @Value("${automation.playwright-runner.root:sakura-playwright}")
    private String runnerRoot;

    @Value("${automation.playwright-runner.node-command:node}")
    private String nodeCommand;

    @Value("${automation.playwright-runner.max-concurrent:2}")
    private int maxConcurrent;

    public AutomationPlaywrightRunnerJobServiceImpl(AutomationPlaywrightCaseService caseService) {
        this.caseService = caseService;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        jobs.values().forEach(job -> {
            if (job.process != null && job.process.isAlive()) {
                job.process.destroyForcibly();
            }
        });
    }

    @Override
    public AutomationPlaywrightRunnerJobResp create(AutomationPlaywrightRunnerJobReq req) {
        CheckUtils.throwIf(!enabled, "Playwright Runner 未启用，请配置 automation.playwright-runner.enabled=true");
        CheckUtils.throwIfNull(req, "Playwright Runner 请求不能为空");
        String caseKey = StringUtils.trimToEmpty(req.getCaseKey());
        CheckUtils.throwIf(StringUtils.isBlank(caseKey), "Playwright Runner caseKey 不能为空");
        CheckUtils.throwIfNull(req.getProjectEnvironmentId(), "Playwright Runner 产品环境不能为空");
        AutomationPlaywrightRunnerJobReq normalizedRequest = normalizeRequest(req);
        validateOptions(normalizedRequest.getOptions());
        // 创建进程前先校验 case，避免任务进入队列后才发现 caseKey 或步骤不存在。
        caseService.getCase(caseKey, normalizedRequest.getProjectEnvironmentId());
        Path root = resolveRunnerRoot();
        CheckUtils.throwIf(!Files.isDirectory(root), runnerRootError(root));
        CheckUtils.throwIf(!Files.isRegularFile(root.resolve("src/index.js")), "Playwright Runner 入口不存在：" + root
            .resolve("src/index.js"));

        int limit = Math.max(1, maxConcurrent);
        int current = activeJobs.incrementAndGet();
        if (current > limit) {
            activeJobs.decrementAndGet();
            throw new BusinessException("Playwright Runner 并发任务已达上限：" + limit);
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        JobRuntime runtime = new JobRuntime(jobId, caseKey, normalizedRequest);
        jobs.put(jobId, runtime);
        // 令牌只从当前已鉴权请求读取，Runner 不接受前端上传的 token，避免越权使用任意令牌。
        String token = StpUtil.getTokenValue();
        executor.submit(() -> run(runtime, token));
        return toResponse(runtime);
    }

    @Override
    public AutomationPlaywrightRunnerJobResp get(String jobId) {
        return toResponse(requireJob(jobId));
    }

    @Override
    public AutomationPlaywrightRunnerJobResp cancel(String jobId) {
        JobRuntime runtime = requireJob(jobId);
        synchronized (runtime) {
            if (TERMINAL_STATUSES.contains(runtime.status)) {
                return toResponse(runtime);
            }
            runtime.cancelRequested = true;
            runtime.status = "cancelled";
            runtime.finishedAt = now();
            if (runtime.process != null) {
                runtime.process.destroy();
                if (runtime.process.isAlive()) {
                    runtime.process.destroyForcibly();
                }
            }
        }
        return toResponse(runtime);
    }

    private void run(JobRuntime runtime, String token) {
        try {
            synchronized (runtime) {
                if (runtime.cancelRequested) {
                    return;
                }
                runtime.startedAt = now();
                runtime.status = "running";
            }
            Path root = resolveRunnerRoot();
            CheckUtils.throwIf(!Files.isDirectory(root), runnerRootError(root));
            CheckUtils.throwIf(!Files.isRegularFile(root.resolve("src/index.js")), "Playwright Runner 入口不存在：" + root
                .resolve("src/index.js"));

            List<String> command = buildCommand(runtime.request, runtime.caseKey);
            appendOutput(runtime, "[admin] runnerRoot=" + root);
            appendOutput(runtime, "[admin] runnerConfig=" + root.resolve(".env"));
            appendOutput(runtime, "[admin] command=" + String.join(" ", command));
            ProcessBuilder processBuilder = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true);
            Map<String, String> environment = processBuilder.environment();
            // API 地址、浏览器和产物策略均由 Runner .env 统一管理；这里只注入当前用户的短期凭证。
            if (StringUtils.isNotBlank(token)) {
                environment.put("CUECAST_TOKEN", token);
            }

            Process process = processBuilder.start();
            runtime.process = process;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process
                .getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendOutput(runtime, line);
                }
            }
            int exitCode = process.waitFor();
            runtime.exitCode = exitCode;
            synchronized (runtime) {
                if (!runtime.cancelRequested) {
                    boolean reportFailed = runtime.outputTail.stream()
                        .anyMatch(line -> line.contains("failed to report result"));
                    runtime.status = exitCode == 0 && !reportFailed ? "passed" : "failed";
                    if (exitCode != 0 && StringUtils.isBlank(runtime.error)) {
                        runtime.error = "Playwright Runner 进程退出码：" + exitCode;
                    } else if (reportFailed && StringUtils.isBlank(runtime.error)) {
                        runtime.error = "Runner 执行完成，但结果回传 admin 失败";
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runtime.error = "Playwright Runner 任务被中断";
            runtime.status = runtime.cancelRequested ? "cancelled" : "failed";
        } catch (Exception e) {
            runtime.error = StringUtils.defaultIfBlank(e.getMessage(), e.toString());
            runtime.status = runtime.cancelRequested ? "cancelled" : "failed";
            log.error("Playwright Runner 任务执行失败，jobId={}", runtime.jobId, e);
        } finally {
            if (runtime.process != null && runtime.process.isAlive()) {
                runtime.process.destroyForcibly();
            }
            if (StringUtils.isBlank(runtime.finishedAt)) {
                runtime.finishedAt = now();
            }
            activeJobs.decrementAndGet();
        }
    }

    private List<String> buildCommand(AutomationPlaywrightRunnerJobReq request, String caseKey) {
        List<String> command = new ArrayList<>();
        command.add(nodeCommand);
        command.add("src/index.js");
        command.add("--case-id");
        command.add(caseKey);
        if (request.getStartStep() != null && request.getStartStep() > 0) {
            command.add("--start-step");
            command.add(String.valueOf(request.getStartStep()));
        }
        command.add("--project-environment-id");
        command.add(String.valueOf(request.getProjectEnvironmentId()));
        AutomationPlaywrightRunnerOptionsReq options = request.getOptions();
        addCommandOption(command, "--browser", options.getBrowser());
        addCommandOption(command, "--headed", options.getHeaded());
        addCommandOption(command, "--ignore-https-errors", options.getIgnoreHttpsErrors());
        addCommandOption(command, "--trace", options.getTrace());
        addCommandOption(command, "--video", options.getVideo());
        addCommandOption(command, "--timeout", options.getStepTimeoutMs());
        addCommandOption(command, "--case-timeout", options.getCaseTimeoutMs());
        addCommandOption(command, "--slow-mo", options.getSlowMoMs());
        addCommandOption(command, "--finish-delay", options.getFinishDelayMs());
        return command;
    }

    private AutomationPlaywrightRunnerJobReq normalizeRequest(AutomationPlaywrightRunnerJobReq source) {
        AutomationPlaywrightRunnerJobReq target = new AutomationPlaywrightRunnerJobReq();
        target.setCaseKey(StringUtils.trimToEmpty(source.getCaseKey()));
        target.setProjectEnvironmentId(source.getProjectEnvironmentId());
        target.setStartStep(source.getStartStep());
        target.setOptions(normalizeOptions(source.getOptions()));
        return target;
    }

    private AutomationPlaywrightRunnerOptionsReq normalizeOptions(AutomationPlaywrightRunnerOptionsReq source) {
        AutomationPlaywrightRunnerOptionsReq defaults = new AutomationPlaywrightRunnerOptionsReq();
        if (source == null) {
            return defaults;
        }
        AutomationPlaywrightRunnerOptionsReq target = new AutomationPlaywrightRunnerOptionsReq();
        target.setBrowser(StringUtils.defaultIfBlank(source.getBrowser(), defaults.getBrowser()));
        target.setHeaded(source.getHeaded() == null ? defaults.getHeaded() : source.getHeaded());
        target.setIgnoreHttpsErrors(source.getIgnoreHttpsErrors() == null
            ? defaults.getIgnoreHttpsErrors()
            : source.getIgnoreHttpsErrors());
        target.setTrace(StringUtils.defaultIfBlank(source.getTrace(), defaults.getTrace()));
        target.setVideo(StringUtils.defaultIfBlank(source.getVideo(), defaults.getVideo()));
        target.setStepTimeoutMs(source.getStepTimeoutMs() == null ? defaults.getStepTimeoutMs() : source
            .getStepTimeoutMs());
        target.setCaseTimeoutMs(source.getCaseTimeoutMs() == null ? defaults.getCaseTimeoutMs() : source
            .getCaseTimeoutMs());
        target.setSlowMoMs(source.getSlowMoMs() == null ? defaults.getSlowMoMs() : source.getSlowMoMs());
        target.setFinishDelayMs(source.getFinishDelayMs() == null
            ? defaults.getFinishDelayMs()
            : source.getFinishDelayMs());
        return target;
    }

    private void validateOptions(AutomationPlaywrightRunnerOptionsReq options) {
        CheckUtils.throwIf(!BROWSERS.contains(options.getBrowser()), "Playwright Runner 浏览器配置无效：" + options
            .getBrowser());
        CheckUtils.throwIf(!ARTIFACT_POLICIES.contains(options.getTrace()), "Playwright Runner trace 策略无效：" + options
            .getTrace());
        CheckUtils.throwIf(!ARTIFACT_POLICIES.contains(options.getVideo()), "Playwright Runner video 策略无效：" + options
            .getVideo());
        CheckUtils.throwIf(options.getCaseTimeoutMs() < options.getStepTimeoutMs(), "Playwright Runner 用例超时不能小于步骤超时");
    }

    private void addCommandOption(List<String> command, String name, Object value) {
        command.add(name);
        command.add(String.valueOf(value));
    }

    private Path resolveRunnerRoot() {
        Path configured = Paths.get(runnerRoot);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        // 允许从工作区根目录、webapi 模块目录或 IDE 配置的其他工作目录启动 admin。
        // 只要候选目录包含 Runner 入口，就使用该目录，避免相对路径因 user.dir 不同而失效。
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Set<Path> candidates = new LinkedHashSet<>();
        candidates.add(userDir.resolve(configured).normalize());
        candidates.add(userDir.resolve("sakura-playwright").normalize());
        candidates.add(userDir.resolve("../sakura-playwright").normalize());
        candidates.add(userDir.resolve("../../sakura-playwright").normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("src/index.js"))) {
                return candidate;
            }
        }
        return userDir.resolve(configured).normalize();
    }

    private String runnerRootError(Path root) {
        return "Playwright Runner 目录不存在或未安装：" + root + "，请配置 automation.playwright-runner.root（当前工作目录：" + System
            .getProperty("user.dir") + "）";
    }

    private JobRuntime requireJob(String jobId) {
        JobRuntime runtime = jobs.get(jobId);
        if (runtime == null) {
            throw new BusinessException("Playwright Runner 任务不存在：" + jobId);
        }
        return runtime;
    }

    private AutomationPlaywrightRunnerJobResp toResponse(JobRuntime runtime) {
        AutomationPlaywrightRunnerJobResp response = new AutomationPlaywrightRunnerJobResp();
        response.setJobId(runtime.jobId);
        response.setCaseKey(runtime.caseKey);
        response.setProjectEnvironmentId(runtime.request.getProjectEnvironmentId());
        response.setStatus(runtime.status);
        response.setExitCode(runtime.exitCode);
        response.setStartedAt(runtime.startedAt);
        response.setFinishedAt(runtime.finishedAt);
        response.setError(runtime.error);
        response.setArtifactDir(runtime.artifactDir);
        synchronized (runtime.outputTail) {
            response.setOutputTail(new ArrayList<>(runtime.outputTail));
        }
        return response;
    }

    private void appendOutput(JobRuntime runtime, String line) {
        int artifactIndex = StringUtils.indexOf(line, "artifacts=");
        if (artifactIndex >= 0) {
            runtime.artifactDir = StringUtils.trimToNull(line.substring(artifactIndex + "artifacts=".length()));
        }
        synchronized (runtime.outputTail) {
            if (runtime.outputTail.size() >= MAX_OUTPUT_LINES) {
                runtime.outputTail.removeFirst();
            }
            runtime.outputTail.addLast(line);
        }
    }

    private String now() {
        return ZonedDateTime.now(PLATFORM_ZONE_ID).format(PLATFORM_DATE_TIME_FORMATTER);
    }

    private static final class JobRuntime {

        private final String jobId;
        private final String caseKey;
        private final AutomationPlaywrightRunnerJobReq request;
        private final Deque<String> outputTail = new ArrayDeque<>();
        private volatile String status = "queued";
        private volatile String startedAt;
        private volatile String finishedAt;
        private volatile String error;
        private volatile Integer exitCode;
        private volatile String artifactDir;
        private volatile boolean cancelRequested;
        private volatile Process process;

        private JobRuntime(String jobId, String caseKey, AutomationPlaywrightRunnerJobReq request) {
            this.jobId = jobId;
            this.caseKey = caseKey;
            this.request = request;
        }
    }
}
