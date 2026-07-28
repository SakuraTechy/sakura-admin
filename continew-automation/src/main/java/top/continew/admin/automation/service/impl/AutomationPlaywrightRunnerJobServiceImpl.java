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
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationPlaywrightJobDO;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerLogResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService.SessionFiles;
import top.continew.admin.automation.support.AutomationStoragePressureGuard;
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
    private static final int MAX_LOG_LINES = 500;
    private static final int DEFAULT_MAX_LIVE_FRAME_SIZE = 2 * 1024 * 1024;
    private static final Map<String, Integer> LIVE_FRAME_MAX_SIZES = Map
        .of("smooth", DEFAULT_MAX_LIVE_FRAME_SIZE, "high", 4 * 1024 * 1024, "ultra", 8 * 1024 * 1024, "8k", 16 * 1024 * 1024);
    private static final long LIVE_FRAME_RETENTION_SECONDS = 30;
    private static final String EXECUTION_LOG_PREFIX = "@@SAKURA_EXECUTION_LOG@@";
    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PLATFORM_DATE_TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter PLATFORM_DATE_TIME_MILLIS_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final List<String> TERMINAL_STATUSES = List.of("passed", "failed", "cancelled", "interrupted");
    private static final Set<String> BROWSERS = Set.of("chromium", "firefox", "webkit");
    private static final Set<String> SESSION_MODES = Set.of("isolated", "reuse-auth");
    private static final Set<String> LIVE_FRAME_QUALITIES = Set.of("smooth", "high", "ultra", "8k");
    private static final Set<String> ARTIFACT_POLICIES = Set.of("off", "on", "retain-on-failure");

    private final AutomationPlaywrightCaseService caseService;
    private final ObjectMapper objectMapper;
    private final AutomationPlaywrightSessionStateService sessionStateService;
    private final Map<String, JobRuntime> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService liveFrameCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService runtimeCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Object sessionAuditMonitor = new Object();

    @Resource
    private AutomationPlaywrightJobMapper jobMapper;

    @Resource
    private AutomationUiSceneMapper automationUiSceneMapper;

    @Resource
    private AutomationStoragePressureGuard storagePressureGuard;

    @Value("${automation.playwright-runner.enabled:true}")
    private boolean enabled;

    @Value("${automation.playwright-runner.root:sakura-playwright}")
    private String runnerRoot;

    @Value("${automation.playwright-runner.node-command:node}")
    private String nodeCommand;

    @Value("${automation.playwright-runner.max-concurrent:2}")
    private int maxConcurrent;

    @Value("${automation.playwright-runner.service-token:}")
    private String serviceToken;

    @Value("${automation.playwright-runner.runtime-retention-minutes:30}")
    private long runtimeRetentionMinutes;

    @Value("${automation.playwright-runner.max-terminal-runtime-count:100}")
    private int maxTerminalRuntimeCount;

    @Value("${automation.playwright-runner.lost-heartbeat-seconds:90}")
    private long lostHeartbeatSeconds;

    @Value("${automation.playwright-runner.executor-node:local}")
    private String executorNode;

    public AutomationPlaywrightRunnerJobServiceImpl(AutomationPlaywrightCaseService caseService,
                                                    ObjectMapper objectMapper,
                                                    AutomationPlaywrightSessionStateService sessionStateService) {
        this.caseService = caseService;
        this.objectMapper = objectMapper;
        this.sessionStateService = sessionStateService;
    }

    @PostConstruct
    public void recoverLostJobs() {
        if (jobMapper == null) {
            return;
        }
        LocalDateTime lostBefore = LocalDateTime.now(PLATFORM_ZONE_ID).minusSeconds(Math.max(1, lostHeartbeatSeconds));
        jobMapper.update(null, Wrappers.<AutomationPlaywrightJobDO>lambdaUpdate()
            .in(AutomationPlaywrightJobDO::getStatus, List.of("queued", "running"))
            .and(wrapper -> wrapper.lt(AutomationPlaywrightJobDO::getHeartbeatAt, lostBefore)
                .or()
                .isNull(AutomationPlaywrightJobDO::getHeartbeatAt))
            .set(AutomationPlaywrightJobDO::getStatus, "interrupted")
            .set(AutomationPlaywrightJobDO::getErrorCode, "ADMIN_RESTARTED")
            .set(AutomationPlaywrightJobDO::getErrorMessage, "admin 重启或执行节点失联，Runner 任务已中断")
            .set(AutomationPlaywrightJobDO::getFinishedAt, LocalDateTime.now(PLATFORM_ZONE_ID)));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        liveFrameCleanupExecutor.shutdownNow();
        runtimeCleanupExecutor.shutdownNow();
        jobs.values().forEach(job -> {
            if (job.process != null && job.process.isAlive()) {
                terminateProcessTree(job.process);
            }
        });
    }

    @Override
    public AutomationPlaywrightRunnerJobResp create(AutomationPlaywrightRunnerJobReq req) {
        return create(req, StpUtil.getTokenValue());
    }

    @Override
    public AutomationPlaywrightRunnerJobResp create(AutomationPlaywrightRunnerJobReq req, String token) {
        if (storagePressureGuard != null) {
            storagePressureGuard.assertExecutionAllowed();
        }
        CheckUtils.throwIf(!enabled, "Playwright Runner 未启用，请配置 automation.playwright-runner.enabled=true");
        CheckUtils.throwIfNull(req, "Playwright Runner 请求不能为空");
        String caseKey = StringUtils.trimToEmpty(req.getCaseKey());
        CheckUtils.throwIf(StringUtils.isBlank(caseKey), "Playwright Runner caseKey 不能为空");
        CheckUtils.throwIfNull(req.getProjectEnvironmentId(), "Playwright Runner 产品环境不能为空");
        AutomationPlaywrightRunnerJobReq normalizedRequest = normalizeRequest(req);
        validateOptions(normalizedRequest.getOptions());
        boolean reuseAuth = "reuse-auth".equals(normalizedRequest.getOptions().getSessionMode());
        CheckUtils.throwIf(reuseAuth && StringUtils.isBlank(normalizedRequest
            .getBatchId()), "Playwright Runner reuse-auth 模式必须提供 batchId");
        ensureBatchCaseNotCancelled(normalizedRequest, caseKey);
        if (reuseAuth) {
            caseService.validateReusableBatchCase(sceneKey(caseKey), normalizedRequest
                .getBatchId(), caseId(caseKey), normalizedRequest.getProjectEnvironmentId());
        }
        // 创建进程前先校验 case，避免任务进入队列后才发现 caseKey 或步骤不存在。
        caseService.getCase(caseKey, normalizedRequest.getProjectEnvironmentId());
        Path root = resolveRunnerRoot();
        CheckUtils.throwIf(!Files.isDirectory(root), runnerRootError(root));
        CheckUtils.throwIf(!Files.isRegularFile(root.resolve("src/index.js")), "Playwright Runner 入口不存在：" + root
            .resolve("src/index.js"));
        // 手工执行使用当前已鉴权请求令牌；无人值守调度只能使用服务端配置的专用令牌。
        String effectiveToken = StringUtils.firstNonBlank(token, serviceToken, "");
        CheckUtils.throwIf(StringUtils
            .isBlank(effectiveToken), "Playwright Runner 缺少服务端鉴权令牌，请配置 automation.playwright-runner.service-token");

        int limit = Math.max(1, maxConcurrent);
        int current = activeJobs.incrementAndGet();
        if (current > limit) {
            activeJobs.decrementAndGet();
            throw new BusinessException("Playwright Runner 并发任务已达上限：" + limit);
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        JobRuntime runtime;
        try {
            synchronized (jobs) {
                CheckUtils.throwIf(reuseAuth && hasActiveBatchJob(normalizedRequest
                    .getBatchId()), "Playwright Runner reuse-auth 批次仅允许串行执行");
                SessionFiles sessionFiles = reuseAuth
                    ? sessionStateService.prepare(normalizedRequest.getBatchId(), normalizedRequest
                        .getProjectEnvironmentId(), jobId)
                    : null;
                runtime = new JobRuntime(jobId, caseKey, normalizedRequest, sessionFiles, resolveDefinitionVersion(caseKey));
                jobs.put(jobId, runtime);
                // 任务注册后再次检查，封住“取消扫描完成、任务随后入队”的竞态窗口。
                ensureBatchCaseNotCancelled(normalizedRequest, caseKey);
            }
        } catch (RuntimeException e) {
            JobRuntime removed = jobs.remove(jobId);
            sessionStateService.discardCandidate(removed == null ? null : removed.sessionFiles);
            activeJobs.decrementAndGet();
            throw e;
        }
        try {
            insertJobRecord(runtime);
        } catch (RuntimeException e) {
            jobs.remove(jobId, runtime);
            activeJobs.decrementAndGet();
            throw e;
        }
        appendLog(runtime, nowWithMillis(), "info", "admin", "Runner 任务已加入执行队列", false);
        executor.submit(() -> run(runtime, effectiveToken));
        return toResponse(runtime);
    }

    private void ensureBatchCaseNotCancelled(AutomationPlaywrightRunnerJobReq request, String caseKey) {
        if (StringUtils.isBlank(request.getBatchId())) {
            return;
        }
        AutomationPlaywrightCaseCancellationResp cancellation = caseService
            .getCaseCancellation(sceneKey(caseKey), request.getBatchId(), caseId(caseKey));
        if (cancellation.isBatchCancelRequested()) {
            throw new BusinessException("Playwright Runner 批次已取消，不能创建新任务");
        }
        if (cancellation.isCaseCancelRequested()) {
            throw new BusinessException("Playwright Runner 用例已取消，不能创建新任务");
        }
    }

    private boolean hasActiveBatchJob(String batchId) {
        return jobs.values()
            .stream()
            .anyMatch(runtime -> batchId.equals(runtime.request.getBatchId()) && !TERMINAL_STATUSES
                .contains(runtime.status));
    }

    @Override
    public AutomationPlaywrightRunnerJobResp get(String jobId) {
        JobRuntime runtime = jobs.get(jobId);
        if (runtime != null) {
            return toResponse(runtime);
        }
        AutomationPlaywrightJobDO persisted = findJobRecord(jobId);
        if (persisted == null) {
            throw new BusinessException("RUNNER_JOB_NOT_FOUND：" + jobId);
        }
        return toResponse(persisted);
    }

    @Override
    public AutomationPlaywrightRunnerJobResp get(String jobId, Long afterSequence) {
        AutomationPlaywrightRunnerJobResp response = get(jobId);
        if (afterSequence == null || response.getLogs() == null || response.getLogs().isEmpty()) {
            return response;
        }
        response.setLogs(response.getLogs().stream().filter(log -> log.getSequence() > afterSequence).toList());
        // 增量日志已覆盖轮询展示，避免每次继续传输重复的控制台尾部。
        response.setOutputTail(List.of());
        return response;
    }

    @Override
    public AutomationPlaywrightRunnerJobResp cancel(String jobId) {
        JobRuntime runtime = requireJob(jobId);
        cancelRuntime(runtime);
        return toResponse(runtime);
    }

    @Override
    public void cancelBatch(String batchId) {
        if (StringUtils.isBlank(batchId)) {
            return;
        }
        jobs.values()
            .stream()
            .filter(runtime -> batchId.equals(runtime.request.getBatchId()))
            .forEach(this::cancelRuntime);
        sessionStateService.cleanupBatch(batchId);
    }

    @Override
    public void cancelCase(String sceneKey, String batchId, String caseId) {
        if (StringUtils.isAnyBlank(sceneKey, batchId, caseId)) {
            return;
        }
        String caseKey = sceneKey + ":" + caseId;
        jobs.values()
            .stream()
            .filter(runtime -> batchId.equals(runtime.request.getBatchId()) && caseKey.equals(runtime.caseKey))
            .forEach(this::cancelRuntime);
    }

    private void cancelRuntime(JobRuntime runtime) {
        synchronized (runtime) {
            if (TERMINAL_STATUSES.contains(runtime.status)) {
                // 状态终态不代表操作系统进程一定退出，重复取消仍需清理残留 Runner/浏览器进程树。
                if (runtime.process != null && runtime.process.isAlive()) {
                    terminateProcessTree(runtime.process);
                }
                return;
            }
            runtime.cancelRequested = true;
            runtime.status = "cancelled";
            runtime.finishedAt = now();
            runtime.terminalAt = System.currentTimeMillis();
            if (runtime.process != null) {
                terminateProcessTree(runtime.process);
            }
        }
        if (runtime.sessionFiles != null) {
            recordSessionEvent(runtime, "warning", "已收到取消请求，登录态候选不提交");
        }
        scheduleLiveFrameCleanup(runtime);
        updateJobRecord(runtime);
        scheduleRuntimeCleanup(runtime);
    }

    @Override
    public void acceptLiveFrame(String jobId, byte[] frame) {
        JobRuntime runtime = requireJob(jobId);
        CheckUtils.throwIf(!"running".equals(runtime.status), "Playwright Runner 任务未在运行，不能接收实时画面");
        CheckUtils.throwIf(frame == null || frame.length < 4, "Playwright Runner 实时画面不能为空");
        String quality = runtime.request.getOptions().getLiveFrameQuality();
        int maxFrameSize = LIVE_FRAME_MAX_SIZES.getOrDefault(quality, DEFAULT_MAX_LIVE_FRAME_SIZE);
        CheckUtils
            .throwIf(frame.length > maxFrameSize, "Playwright Runner 实时画面超过当前质量档位上限：" + maxFrameSize / 1024 / 1024 + "MB");
        boolean jpeg = (frame[0] & 0xFF) == 0xFF && (frame[1] & 0xFF) == 0xD8 && (frame[frame.length - 2] & 0xFF) == 0xFF && (frame[frame.length - 1] & 0xFF) == 0xD9;
        CheckUtils.throwIf(!jpeg, "Playwright Runner 实时画面必须是 JPEG");
        synchronized (runtime.liveFrameMonitor) {
            // 仅保留最新一帧，避免实时画面变成长期截图存储或撑大场景 JSON。
            runtime.liveFrame = frame.clone();
            runtime.liveFrameSequence++;
        }
    }

    @Override
    public LiveFrame getLiveFrame(String jobId) {
        JobRuntime runtime = requireJob(jobId);
        synchronized (runtime.liveFrameMonitor) {
            if (runtime.liveFrame == null)
                return null;
            return new LiveFrame(runtime.liveFrameSequence, runtime.liveFrame.clone());
        }
    }

    private void run(JobRuntime runtime, String token) {
        try {
            verifyDefinitionVersion(runtime);
            synchronized (runtime) {
                if (runtime.cancelRequested) {
                    return;
                }
                runtime.startedAt = now();
                runtime.status = "running";
                runtime.heartbeatAt = LocalDateTime.now(PLATFORM_ZONE_ID);
            }
            updateJobRecord(runtime);
            Path root = resolveRunnerRoot();
            CheckUtils.throwIf(!Files.isDirectory(root), runnerRootError(root));
            CheckUtils.throwIf(!Files.isRegularFile(root.resolve("src/index.js")), "Playwright Runner 入口不存在：" + root
                .resolve("src/index.js"));

            List<String> command = buildCommand(runtime.request, runtime.caseKey, runtime.jobId, runtime.sessionFiles);
            if (runtime.sessionFiles != null) {
                recordSessionEvent(runtime, "info", "Runner 启动，输入登录态=" + (sessionStateService
                    .hasCurrent(runtime.sessionFiles) ? "存在" : "不存在"));
            }
            appendOutput(runtime, "[admin] runnerRoot=" + root);
            appendOutput(runtime, "[admin] runnerConfig=" + root.resolve(".env"));
            appendOutput(runtime, "[admin] command=" + String.join(" ", sessionStateService.redactCommand(command)));
            ProcessBuilder processBuilder = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true);
            Map<String, String> environment = processBuilder.environment();
            // API 地址、浏览器和产物策略均由 Runner .env 统一管理；这里只注入当前用户的短期凭证。
            if (StringUtils.isNotBlank(token)) {
                environment.put("CUECAST_TOKEN", token);
            }

            Process process = processBuilder.start();
            boolean cancelledAfterStart;
            synchronized (runtime) {
                runtime.process = process;
                cancelledAfterStart = runtime.cancelRequested;
            }
            if (cancelledAfterStart) {
                terminateProcessTree(process);
                return;
            }
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
                    if (exitCode == 0 && !reportFailed && runtime.sessionFiles != null) {
                        // Node 只生成候选文件；必须在进程成功且未取消后由服务端原子提交。
                        sessionStateService.promote(runtime.sessionFiles);
                        recordSessionEvent(runtime, "success", "Runner 成功且未取消，登录态候选已原子提升");
                    }
                    runtime.status = exitCode == 0 && !reportFailed ? "passed" : "failed";
                    if (exitCode != 0 && StringUtils.isBlank(runtime.error)) {
                        runtime.error = "Playwright Runner 进程退出码：" + exitCode;
                    } else if (reportFailed && StringUtils.isBlank(runtime.error)) {
                        runtime.error = "Runner 执行完成，但结果回传 admin 失败";
                    }
                }
                runtime.heartbeatAt = LocalDateTime.now(PLATFORM_ZONE_ID);
            }
            if ("passed".equals(runtime.status) && StringUtils.isNotBlank(runtime.request.getBatchId()) && caseService
                .isBatchTerminal(sceneKey(runtime.caseKey), runtime.request.getBatchId())) {
                sessionStateService.cleanupBatch(runtime.request.getBatchId());
                recordSessionEvent(runtime, "info", "批次已进入终态，登录态目录已清理");
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
            if (runtime.sessionFiles != null && !"passed".equals(runtime.status)) {
                recordSessionEvent(runtime, "warning", "Runner 状态=" + runtime.status + "，登录态候选不提交");
            }
            sessionStateService.discardCandidate(runtime.sessionFiles);
            if (runtime.process != null && runtime.process.isAlive()) {
                terminateProcessTree(runtime.process);
            }
            if (StringUtils.isBlank(runtime.finishedAt)) {
                runtime.finishedAt = now();
            }
            runtime.terminalAt = System.currentTimeMillis();
            runtime.heartbeatAt = LocalDateTime.now(PLATFORM_ZONE_ID);
            updateJobRecord(runtime);
            scheduleLiveFrameCleanup(runtime);
            scheduleRuntimeCleanup(runtime);
            activeJobs.decrementAndGet();
        }
    }

    private void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        // Playwright 浏览器是 Node 的子孙进程；只杀主进程会留下仍在执行的浏览器实例。
        process.destroy();
        for (int index = descendants.size() - 1; index >= 0; index--) {
            ProcessHandle descendant = descendants.get(index);
            if (descendant.isAlive()) {
                descendant.destroyForcibly();
            }
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 父进程退出过程中可能短暂留下后代，再补一次强制终止，避免浏览器继续执行当前步骤。
        for (int index = descendants.size() - 1; index >= 0; index--) {
            ProcessHandle descendant = descendants.get(index);
            if (descendant.isAlive()) {
                descendant.destroyForcibly();
            }
        }
        if (process.isAlive()) {
            log.warn("Runner 进程树在取消等待后仍未退出，pid={}", process.pid());
        }
    }

    private List<String> buildCommand(AutomationPlaywrightRunnerJobReq request,
                                      String caseKey,
                                      String jobId,
                                      SessionFiles sessionFiles) {
        List<String> command = new ArrayList<>();
        command.add(nodeCommand);
        command.add("src/index.js");
        command.add("--case-id");
        command.add(caseKey);
        addCommandOption(command, "--job-id", jobId);
        addCommandOptionIfPresent(command, "--batch-id", request.getBatchId());
        addCommandOptionIfPresent(command, "--run-id", request.getExecutionId());
        if (request.getStartStep() != null && request.getStartStep() > 0) {
            command.add("--start-step");
            command.add(String.valueOf(request.getStartStep()));
        }
        command.add("--project-environment-id");
        command.add(String.valueOf(request.getProjectEnvironmentId()));
        AutomationPlaywrightRunnerOptionsReq options = request.getOptions();
        addCommandOption(command, "--browser", options.getBrowser());
        addCommandOption(command, "--live-frame-quality", options.getLiveFrameQuality());
        addCommandOption(command, "--session-mode", options.getSessionMode());
        if (sessionFiles != null) {
            if (sessionStateService.hasCurrent(sessionFiles)) {
                addCommandOption(command, "--storage-state", sessionFiles.currentPath());
            }
            addCommandOption(command, "--storage-state-out", sessionFiles.candidatePath());
        }
        // admin Runner 使用录制语义定位；独立 CLI/Jenkins 未传该参数时仍走 legacy，保留回滚入口。
        addCommandOption(command, "--locator-mode", "semantic-v1");
        addCommandOption(command, "--headed", options.getHeaded());
        addCommandOption(command, "--ignore-https-errors", options.getIgnoreHttpsErrors());
        if (options.getPageErrorCheckEnabled() != null) {
            addCommandOption(command, "--page-error-check-enabled", options.getPageErrorCheckEnabled());
        }
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
        target.setBatchId(StringUtils.trimToNull(source.getBatchId()));
        target.setExecutionId(StringUtils.trimToNull(source.getExecutionId()));
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
        target.setLiveFrameQuality(StringUtils.defaultIfBlank(source.getLiveFrameQuality(), defaults
            .getLiveFrameQuality()));
        target.setSessionMode(StringUtils.defaultIfBlank(source.getSessionMode(), defaults.getSessionMode()));
        target.setHeaded(source.getHeaded() == null ? defaults.getHeaded() : source.getHeaded());
        target.setIgnoreHttpsErrors(source.getIgnoreHttpsErrors() == null
            ? defaults.getIgnoreHttpsErrors()
            : source.getIgnoreHttpsErrors());
        target.setPageErrorCheckEnabled(source.getPageErrorCheckEnabled());
        target.setTrace(StringUtils.defaultIfBlank(source.getTrace(), defaults.getTrace()));
        target.setVideo(StringUtils.defaultIfBlank(source.getVideo(), defaults.getVideo()));
        target.setStepTimeoutMs(source.getStepTimeoutMs() == null
            ? defaults.getStepTimeoutMs()
            : source.getStepTimeoutMs());
        target.setCaseTimeoutMs(source.getCaseTimeoutMs() == null
            ? defaults.getCaseTimeoutMs()
            : source.getCaseTimeoutMs());
        target.setSlowMoMs(source.getSlowMoMs() == null ? defaults.getSlowMoMs() : source.getSlowMoMs());
        target.setFinishDelayMs(source.getFinishDelayMs() == null
            ? defaults.getFinishDelayMs()
            : source.getFinishDelayMs());
        return target;
    }

    private void validateOptions(AutomationPlaywrightRunnerOptionsReq options) {
        CheckUtils.throwIf(!BROWSERS.contains(options.getBrowser()), "Playwright Runner 浏览器配置无效：" + options
            .getBrowser());
        CheckUtils.throwIf(!LIVE_FRAME_QUALITIES.contains(options
            .getLiveFrameQuality()), "Playwright Runner 实时画面质量配置无效：" + options.getLiveFrameQuality());
        CheckUtils.throwIf(!SESSION_MODES.contains(options.getSessionMode()), "Playwright Runner 用例会话模式无效：" + options
            .getSessionMode());
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

    private void addCommandOptionIfPresent(List<String> command, String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            addCommandOption(command, name, value);
        }
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

    private void insertJobRecord(JobRuntime runtime) {
        if (jobMapper == null) {
            return;
        }
        AutomationPlaywrightJobDO job = new AutomationPlaywrightJobDO();
        job.setJobId(runtime.jobId);
        job.setSceneKey(sceneKey(runtime.caseKey));
        job.setCaseId(caseId(runtime.caseKey));
        job.setCaseKey(runtime.caseKey);
        job.setDefinitionVersion(runtime.definitionVersion);
        job.setBatchId(runtime.request.getBatchId());
        job.setExecutionId(runtime.request.getExecutionId());
        job.setExecutionType("playwright-runner");
        job.setProjectEnvironmentId(runtime.request.getProjectEnvironmentId());
        job.setExecutorNode(StringUtils.defaultIfBlank(executorNode, "local"));
        job.setStatus(runtime.status);
        job.setHeartbeatAt(LocalDateTime.now(PLATFORM_ZONE_ID));
        jobMapper.insert(job);
        runtime.recordId = job.getId();
        runtime.heartbeatAt = job.getHeartbeatAt();
    }

    private void updateJobRecord(JobRuntime runtime) {
        if (jobMapper == null || runtime.recordId == null) {
            return;
        }
        String errorCode = runtime.cancelRequested
            ? "CANCELLED"
            : runtime.errorCode != null ? runtime.errorCode : runtime.error == null ? null : "RUNNER_FAILED";
        jobMapper.update(null, Wrappers.<AutomationPlaywrightJobDO>lambdaUpdate()
            .eq(AutomationPlaywrightJobDO::getId, runtime.recordId)
            .set(AutomationPlaywrightJobDO::getStatus, runtime.status)
            .set(AutomationPlaywrightJobDO::getExitCode, runtime.exitCode)
            .set(AutomationPlaywrightJobDO::getErrorCode, errorCode)
            .set(AutomationPlaywrightJobDO::getErrorMessage, runtime.error)
            .set(AutomationPlaywrightJobDO::getStartedAt, toDateTime(runtime.startedAt))
            .set(AutomationPlaywrightJobDO::getFinishedAt, toDateTime(runtime.finishedAt))
            .set(AutomationPlaywrightJobDO::getHeartbeatAt, runtime.heartbeatAt == null
                ? LocalDateTime.now(PLATFORM_ZONE_ID)
                : runtime.heartbeatAt));
    }

    private AutomationPlaywrightJobDO findJobRecord(String jobId) {
        if (jobMapper == null || StringUtils.isBlank(jobId)) {
            return null;
        }
        return jobMapper.selectOne(Wrappers.<AutomationPlaywrightJobDO>lambdaQuery()
            .eq(AutomationPlaywrightJobDO::getJobId, jobId));
    }

    private AutomationPlaywrightRunnerJobResp toResponse(AutomationPlaywrightJobDO job) {
        AutomationPlaywrightRunnerJobResp response = new AutomationPlaywrightRunnerJobResp();
        response.setJobId(job.getJobId());
        response.setCaseKey(job.getCaseKey());
        response.setProjectEnvironmentId(job.getProjectEnvironmentId());
        response.setStatus(job.getStatus());
        response.setExitCode(job.getExitCode());
        response.setStartedAt(formatDateTime(job.getStartedAt()));
        response.setFinishedAt(formatDateTime(job.getFinishedAt()));
        response.setError(job.getErrorMessage());
        response.setOutputTail(List.of());
        response.setLogs(List.of());
        response.setLiveAvailable(false);
        return response;
    }

    private void scheduleRuntimeCleanup(JobRuntime runtime) {
        if (!TERMINAL_STATUSES.contains(runtime.status) || runtime.runtimeCleanupScheduled) {
            return;
        }
        runtime.runtimeCleanupScheduled = true;
        runtimeCleanupExecutor.schedule(() -> clearRuntime(runtime), Math
            .max(1, runtimeRetentionMinutes), TimeUnit.MINUTES);
        trimTerminalRuntimes();
    }

    private void trimTerminalRuntimes() {
        int maxCount = Math.max(1, maxTerminalRuntimeCount);
        List<JobRuntime> terminalRuntimes = jobs.values()
            .stream()
            .filter(job -> TERMINAL_STATUSES.contains(job.status))
            .sorted((left, right) -> Long.compare(left.terminalAt, right.terminalAt))
            .toList();
        int removeCount = terminalRuntimes.size() - maxCount;
        for (int i = 0; i < removeCount; i++) {
            clearRuntime(terminalRuntimes.get(i));
        }
    }

    private void clearRuntime(JobRuntime runtime) {
        if (!TERMINAL_STATUSES.contains(runtime.status)) {
            return;
        }
        synchronized (runtime.outputTail) {
            runtime.outputTail.clear();
        }
        synchronized (runtime.logs) {
            runtime.logs.clear();
        }
        synchronized (runtime.liveFrameMonitor) {
            runtime.liveFrame = null;
        }
        runtime.process = null;
        jobs.remove(runtime.jobId, runtime);
    }

    private String sceneKey(String caseKey) {
        int separator = StringUtils.indexOf(caseKey, ':');
        return separator < 0 ? caseKey : caseKey.substring(0, separator);
    }

    private String caseId(String caseKey) {
        int separator = StringUtils.indexOf(caseKey, ':');
        return separator < 0 || separator == caseKey.length() - 1 ? "" : caseKey.substring(separator + 1);
    }

    private LocalDateTime toDateTime(String value) {
        return StringUtils.isBlank(value) ? null : LocalDateTime.parse(value, PLATFORM_DATE_TIME_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(PLATFORM_DATE_TIME_FORMATTER);
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
        synchronized (runtime.logs) {
            response.setLogs(new ArrayList<>(runtime.logs));
        }
        synchronized (runtime.liveFrameMonitor) {
            response.setLiveAvailable(!TERMINAL_STATUSES.contains(runtime.status) || runtime.liveFrame != null);
        }
        return response;
    }

    private void appendOutput(JobRuntime runtime, String line) {
        runtime.heartbeatAt = LocalDateTime.now(PLATFORM_ZONE_ID);
        if (StringUtils.startsWith(line, EXECUTION_LOG_PREFIX)) {
            appendRunnerLog(runtime, line.substring(EXECUTION_LOG_PREFIX.length()));
            return;
        }
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
        appendLog(runtime, nowWithMillis(), inferLevel(line), inferPhase(line), line, line.startsWith("[admin]"));
    }

    private void appendRunnerLog(JobRuntime runtime, String payload) {
        try {
            AutomationPlaywrightRunnerLogResp event = objectMapper
                .readValue(payload, AutomationPlaywrightRunnerLogResp.class);
            appendLog(runtime, StringUtils.defaultIfBlank(event.getTimestamp(), nowWithMillis()), event
                .getLevel(), event.getPhase(), event.getMessage(), event.isDetail());
            synchronized (runtime.outputTail) {
                if (runtime.outputTail.size() >= MAX_OUTPUT_LINES)
                    runtime.outputTail.removeFirst();
                runtime.outputTail.addLast(event.getMessage());
            }
        } catch (Exception e) {
            appendLog(runtime, nowWithMillis(), "warning", "runner", "无法解析 Runner 结构化日志", true);
            log.debug("解析 Runner 结构化日志失败，jobId={}", runtime.jobId, e);
        }
    }

    private void appendLog(JobRuntime runtime,
                           String timestamp,
                           String level,
                           String phase,
                           String message,
                           boolean detail) {
        AutomationPlaywrightRunnerLogResp event = new AutomationPlaywrightRunnerLogResp();
        event.setSequence(++runtime.logSequence);
        event.setTimestamp(timestamp);
        event.setLevel(normalizeLevel(level));
        event.setPhase(StringUtils.defaultIfBlank(phase, "runner"));
        event.setMessage(StringUtils.abbreviate(StringUtils.defaultString(message), 4000));
        event.setDetail(detail);
        synchronized (runtime.logs) {
            if (runtime.logs.size() >= MAX_LOG_LINES)
                runtime.logs.removeFirst();
            runtime.logs.addLast(event);
        }
    }

    private void recordSessionEvent(JobRuntime runtime, String level, String message) {
        appendLog(runtime, nowWithMillis(), level, "session", message, true);
        try {
            Path auditFile = resolveRunnerRoot().resolve("logs")
                .resolve(LocalDate.now(PLATFORM_ZONE_ID).format(DateTimeFormatter.BASIC_ISO_DATE))
                .resolve("session-audit.log");
            String line = nowWithMillis() + " level=" + normalizeLevel(level) + " batchId=" + sanitizeAuditField(runtime.request
                .getBatchId()) + " jobId=" + sanitizeAuditField(runtime.jobId) + " case=" + sanitizeAuditField(runtime.caseKey) + " event=" + sanitizeAuditField(message) + System
                    .lineSeparator();
            synchronized (sessionAuditMonitor) {
                Files.createDirectories(auditFile.getParent());
                Files
                    .writeString(auditFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            // 会话审计日志仅用于定位问题，写入失败不能改变 Runner 任务结果。
            log.warn("写入 Playwright 会话审计日志失败，jobId={}", runtime.jobId, e);
        }
    }

    private String sanitizeAuditField(String value) {
        return StringUtils.defaultIfBlank(value, "-").replace('\r', '_').replace('\n', '_');
    }

    private String normalizeLevel(String level) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(level));
        return List.of("info", "success", "warning", "error").contains(normalized) ? normalized : "info";
    }

    private String inferLevel(String line) {
        String normalized = StringUtils.lowerCase(StringUtils.defaultString(line));
        // admin 命令行可能包含 page-error-check 等参数名，不能据此误判为执行错误。
        if (normalized.startsWith("[admin] command="))
            return "info";
        if (normalized.contains("error") || normalized.contains("failed"))
            return "error";
        if (normalized.contains("warn"))
            return "warning";
        return "info";
    }

    private String inferPhase(String line) {
        return StringUtils.startsWith(line, "[admin]") ? "admin" : "runner";
    }

    private void scheduleLiveFrameCleanup(JobRuntime runtime) {
        synchronized (runtime.liveFrameMonitor) {
            if (runtime.liveFrame == null || runtime.liveFrameCleanupScheduled)
                return;
            runtime.liveFrameCleanupScheduled = true;
        }
        // 短用例结束后保留最后一帧 30 秒，方便前端完成最后一次轮询；之后只清理内存，不生成截图产物。
        liveFrameCleanupExecutor
            .schedule(() -> clearLiveFrame(runtime), LIVE_FRAME_RETENTION_SECONDS, TimeUnit.SECONDS);
    }

    private void clearLiveFrame(JobRuntime runtime) {
        synchronized (runtime.liveFrameMonitor) {
            runtime.liveFrame = null;
            runtime.liveFrameCleanupScheduled = false;
        }
    }

    private String now() {
        return ZonedDateTime.now(PLATFORM_ZONE_ID).format(PLATFORM_DATE_TIME_FORMATTER);
    }

    private String nowWithMillis() {
        return ZonedDateTime.now(PLATFORM_ZONE_ID).format(PLATFORM_DATE_TIME_MILLIS_FORMATTER);
    }

    /** Runner 入队和真正启动之间若场景定义已改变，不能让相同 caseId 指向新的业务对象。 */
    private void verifyDefinitionVersion(JobRuntime runtime) {
        Long current = resolveDefinitionVersion(runtime.caseKey);
        if (!java.util.Objects.equals(current, runtime.definitionVersion)) {
            runtime.errorCode = "PLAYWRIGHT_DEFINITION_CHANGED";
            throw new BusinessException("场景定义已发生变化，请重新创建 Playwright 执行任务");
        }
    }

    private Long resolveDefinitionVersion(String caseKey) {
        String key = sceneKey(caseKey);
        AutomationUiSceneDO scene;
        try {
            scene = automationUiSceneMapper.selectById(Long.valueOf(key));
        } catch (NumberFormatException e) {
            scene = automationUiSceneMapper.selectOne(Wrappers.<AutomationUiSceneDO>lambdaQuery()
                .eq(AutomationUiSceneDO::getSceneId, key));
        }
        CheckUtils.throwIfNull(scene, "Playwright Runner 目标场景不存在");
        return scene.getDefinitionVersion() == null ? 0L : scene.getDefinitionVersion();
    }

    private static final class JobRuntime {

        private final String jobId;
        private final String caseKey;
        private final AutomationPlaywrightRunnerJobReq request;
        private final SessionFiles sessionFiles;
        private final Long definitionVersion;
        private final Deque<String> outputTail = new ArrayDeque<>();
        private final Deque<AutomationPlaywrightRunnerLogResp> logs = new ArrayDeque<>();
        private final Object liveFrameMonitor = new Object();
        private long logSequence;
        private long liveFrameSequence;
        private byte[] liveFrame;
        private boolean liveFrameCleanupScheduled;
        private volatile String status = "queued";
        private volatile String startedAt;
        private volatile String finishedAt;
        private volatile String error;
        private volatile String errorCode;
        private volatile Integer exitCode;
        private volatile String artifactDir;
        private volatile boolean cancelRequested;
        private volatile Process process;
        private volatile Long recordId;
        private volatile LocalDateTime heartbeatAt;
        private volatile long terminalAt;
        private volatile boolean runtimeCleanupScheduled;

        private JobRuntime(String jobId,
                           String caseKey,
                           AutomationPlaywrightRunnerJobReq request,
                           SessionFiles sessionFiles,
                           Long definitionVersion) {
            this.jobId = jobId;
            this.caseKey = caseKey;
            this.request = request;
            this.sessionFiles = sessionFiles;
            this.definitionVersion = definitionVersion;
        }
    }
}
