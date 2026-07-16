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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.AutomationPlaybackUrlRewriter;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * Playwright Runner admin 数据服务实现。
 *
 * @author Codex
 */
@Service
@RequiredArgsConstructor
public class AutomationPlaywrightCaseServiceImpl implements AutomationPlaywrightCaseService {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PLATFORM_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final AutomationPlaywrightStepExtractor stepExtractor;
    private final ProjectEnvironmentConfigMapper projectEnvironmentConfigMapper;
    private final ProjectConfigMapper projectConfigMapper;
    private final AutomationPlaybackUrlRewriter playbackUrlRewriter;

    @Override
    public AutomationPlaywrightCaseResp getCase(String caseKey) {
        return getCase(caseKey, null);
    }

    @Override
    public AutomationPlaywrightCaseResp getCase(String caseKey, Long projectEnvironmentId) {
        ResolvedCase resolved = resolveCase(caseKey);
        CaseDO caseDO = resolved.caseDO();
        AutomationPlaywrightCaseResp resp = new AutomationPlaywrightCaseResp();
        resp.setId(caseKey);
        resp.setSceneDbId(resolved.scene().getId());
        resp.setSceneId(resolved.scene().getSceneId());
        resp.setSceneName(resolved.scene().getName());
        resp.setScene_name(resolved.scene().getName());
        resp.setCaseId(caseDO.getId());
        resp.setName(caseDO.getName());
        fillArtifactPathMetadata(resp, resolved.scene());

        List<Map<String, Object>> steps = new ArrayList<>();
        List<StepDOAdapter> adapters = stepAdapters(caseDO);
        for (int i = 0; i < adapters.size(); i++) {
            steps.add(stepExtractor.extract(adapters.get(i).step(), i));
        }
        resp.setSteps(steps);
        fillCaseRuntimeFields(resp, steps);
        if (projectEnvironmentId != null) {
            applyProjectEnvironment(resp, resolved.scene(), projectEnvironmentId);
        }
        return resp;
    }

    /**
     * Runner 仅使用这些业务标识生成本地目录，不把节点路径写回场景主数据。
     */
    private void fillArtifactPathMetadata(AutomationPlaywrightCaseResp resp, AutomationUiSceneDO scene) {
        ProjectConfigDO project = scene.getProjectId() == null ? null : projectConfigMapper.selectById(scene.getProjectId());
        String projectShortName = project == null
            ? StringUtils.firstNonBlank(scene.getProjectName(), "project")
            : StringUtils.firstNonBlank(project.getAbbreviate(), project.getName(), scene.getProjectName(), "project");
        String versionName = StringUtils.firstNonBlank(scene.getVersionName(), "version");
        resp.setProjectShortName(projectShortName);
        resp.setProject_short_name(projectShortName);
        resp.setVersionName(versionName);
        resp.setVersion_name(versionName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(String caseKey, AutomationPlaywrightResultReq req) {
        ResolvedCase resolved = resolveCase(caseKey);
        AutomationUiSceneDO scene = resolved.scene();
        List<Object> debugRecord = scene.getDebugRecord() == null
            ? new ArrayList<>()
            : new ArrayList<>(scene.getDebugRecord());
        Map<String, Object> record = new LinkedHashMap<>();
        // 每次 CDP/Runner 回传都生成独立快照，避免覆盖其他执行方式的最新记录。
        debugRecord.add(0, record);
        Map<String, Object> rawResult = normalizeExecutionTimes(asObjectMap(req.getRaw()));
        Map<String, Object> caseResult = asObjectMap(rawResult.get("case_result"));
        List<Object> stepResults = readStepResults(rawResult, caseResult);
        boolean passed = Boolean.TRUE.equals(req.getSuccess());
        int stepTotal = stepResults.size();
        int stepPass = countStepStatus(stepResults, "passed");
        int stepFail = countStepStatus(stepResults, "failed");
        int stepSkip = countStepStatus(stepResults, "skipped");
        if (stepTotal == 0) {
            // 兼容旧 Runner 结果：旧协议只有步骤总数，没有逐步明细。
            Map<String, Object> detail = asObjectMap(rawResult.get("detail"));
            stepTotal = toInt(detail.get("steps"));
            stepPass = passed ? stepTotal : 0;
            stepFail = passed ? 0 : (stepTotal > 0 ? 1 : 0);
            stepSkip = 0;
        }
        String executor = String.valueOf(rawResult.getOrDefault("executor", "playwright-runner"));
        String executionType = "extension-cdp".equalsIgnoreCase(executor) ? "extension-cdp" : "playwright-runner";
        String executionId = stringValue(rawResult.get("run_id"));
        if (executionId.isBlank()) {
            executionId = executionType + "-" + UUID.randomUUID();
        }
        String startedAt = normalizeExecutionDateTime(stringValue(rawResult.get("started_at")));
        String finishedAt = normalizeExecutionDateTime(stringValue(rawResult.get("finished_at")));
        if (StringUtils.isNotBlank(startedAt)) {
            rawResult.put("started_at", startedAt);
        }
        if (StringUtils.isNotBlank(finishedAt)) {
            rawResult.put("finished_at", finishedAt);
        }
        String resultCode = passed
            ? AutomationUiSceneStatusCodes.RESULT_PASSED
            : AutomationUiSceneStatusCodes.RESULT_FAILED;
        String caseId = resolved.caseDO().getId();
        String caseName = resolved.caseDO().getName();
        caseResult.putIfAbsent("case_key", caseKey);
        caseResult.putIfAbsent("case_id", caseId);
        caseResult.putIfAbsent("case_name", caseName);
        caseResult.putIfAbsent("status", passed ? "passed" : "failed");
        caseResult.putIfAbsent("steps", stepResults);
        caseResult.putIfAbsent("step_total", stepTotal);
        caseResult.putIfAbsent("step_pass", stepPass);
        caseResult.putIfAbsent("step_fail", stepFail);
        caseResult.putIfAbsent("step_skip", stepSkip);

        record.put("executeName", executor);
        record.put("executionType", executionType);
        record.put("executionId", executionId);
        record.put("startedAt", startedAt);
        record.put("finishedAt", finishedAt);
        record.put("executeStatus", "completed");
        record.put("executeResult", resultCode);
        record.put("duration", req.getDurationMs() == null ? "-" : String.valueOf(req.getDurationMs()));
        record.put("playwrightCaseKey", caseKey);
        record.put("playwrightStatus", req.getStatus());
        record.put("playwrightError", req.getError());
        // 入库前统一执行时间，避免不同 Runner/CDP 版本把 UTC ISO 与平台时间混存。
        record.put("playwrightResult", rawResult);
        record.put("caseId", caseId);
        record.put("caseName", caseName);
        record.put("caseTotal", 1);
        record.put("casePass", passed ? 1 : 0);
        record.put("caseFail", passed ? 0 : 1);
        record.put("caseSkip", 0);
        record.put("casePassRate", passed ? "100%" : "0%");
        // 场景列表读取 scenePassRate；单用例回放的场景通过率就是本次用例通过率。
        record.put("scenePassRate", passed ? "100%" : "0%");
        record.put("stepTotal", stepTotal);
        record.put("stepPass", stepPass);
        record.put("stepFail", stepFail);
        record.put("stepSkip", stepSkip);
        record.put("stepPassRate", formatRate(stepPass, stepTotal));
        record.put("caseResults", List.of(caseResult));
        record.put("stepResults", stepResults);
        if (!rawResult.isEmpty()) {
            Object artifacts = rawResult.get("artifacts");
            record.put("playwrightArtifacts", artifacts);
            record.put("artifactUrls", artifacts);
            record.put("artifactUploadErrors", rawResult.get("artifact_upload_errors"));
        }
        scene.setExecuteStatus(AutomationUiSceneStatusCodes.STATUS_COMPLETED);
        scene.setExecuteResult(resultCode);
        scene.setLastResult(resultCode);
        scene.setCaseTotal(1);
        scene.setCasePass(passed ? 1 : 0);
        scene.setCaseFail(passed ? 0 : 1);
        scene.setCaseSkip(0);
        scene.setPassRate(passed ? "100%" : "0%");
        scene.setStepTotal(stepTotal);
        scene.setStepPass(stepPass);
        scene.setStepFail(stepFail);
        scene.setStepSkip(stepSkip);
        scene.setDebugRecord(debugRecord);
        automationUiSceneMapper.updateById(scene);
    }

    private Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeExecutionDateTime(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String normalized = value.trim();
        try {
            return Instant.parse(normalized).atZone(PLATFORM_ZONE_ID).format(PLATFORM_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续兼容带偏移量和本地时间格式。
        }
        try {
            return OffsetDateTime.parse(normalized).atZoneSameInstant(PLATFORM_ZONE_ID)
                .format(PLATFORM_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续兼容无时区的 ISO 本地时间。
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME, PLATFORM_DATE_TIME_FORMATTER)) {
            try {
                return LocalDateTime.parse(normalized, formatter).format(PLATFORM_DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                // 未识别的旧数据保持原值，避免结果回传失败。
            }
        }
        return normalized;
    }

    private Map<String, Object> normalizeExecutionTimes(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(key, normalizeExecutionTimeValue(key, value)));
        return normalized;
    }

    private Object normalizeExecutionTimeValue(String key, Object value) {
        if (value instanceof CharSequence && isExecutionDateTimeField(key)) {
            return normalizeExecutionDateTime(String.valueOf(value));
        }
        if (value instanceof Map<?, ?>) {
            return normalizeExecutionTimes(asObjectMap(value));
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeExecutionTimeValue("", item));
            }
            return normalized;
        }
        return value;
    }

    private boolean isExecutionDateTimeField(String key) {
        return "timestamp".equals(key) || key.endsWith("_at") || key.endsWith("At");
    }

    private List<Object> readStepResults(Map<String, Object> rawResult, Map<String, Object> caseResult) {
        Object steps = caseResult.get("steps");
        if (!(steps instanceof List<?>)) {
            // Playwright Runner 旧协议直接把逐步骤结果放在 raw.steps，不能只按扩展 case_result 读取。
            steps = rawResult.get("steps");
        }
        if (!(steps instanceof List<?>)) {
            steps = asObjectMap(rawResult.get("detail")).get("step_results");
        }
        if (!(steps instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    private int countStepStatus(List<Object> stepResults, String status) {
        int count = 0;
        for (Object item : stepResults) {
            if (item instanceof Map<?, ?> map && status.equals(String.valueOf(map.get("status")))) {
                count++;
            }
        }
        return count;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatRate(int pass, int total) {
        if (total <= 0) {
            return "0%";
        }
        return String.valueOf(Math.round(pass * 10000.0 / total) / 100.0) + "%";
    }

    private ResolvedCase resolveCase(String caseKey) {
        String[] parts = caseKey == null ? new String[0] : caseKey.split(":", 2);
        if (parts.length != 2) {
            throw new BusinessException("Playwright caseKey 格式必须为 sceneId:caseId");
        }
        AutomationUiSceneDO scene = resolveScene(parts[0]);
        CaseDO caseDO = findCase(scene, parts[1]);
        if (caseDO == null) {
            throw new BusinessException("Playwright 目标用例不存在，caseId=" + parts[1]);
        }
        return new ResolvedCase(scene, caseDO);
    }

    private AutomationUiSceneDO resolveScene(String sceneKey) {
        AutomationUiSceneDO scene;
        try {
            scene = automationUiSceneMapper.selectById(Long.valueOf(sceneKey));
        } catch (NumberFormatException e) {
            scene = automationUiSceneMapper.selectOne(new LambdaQueryWrapper<AutomationUiSceneDO>()
                .eq(AutomationUiSceneDO::getSceneId, sceneKey));
        }
        if (scene == null) {
            throw new BusinessException("Playwright 目标场景不存在，sceneKey=" + sceneKey);
        }
        return scene;
    }

    private CaseDO findCase(AutomationUiSceneDO scene, String caseId) {
        if (scene.getCaseList() == null) {
            return null;
        }
        for (CaseDO caseDO : scene.getCaseList()) {
            if (caseDO != null && caseId.equals(caseDO.getId())) {
                return caseDO;
            }
        }
        return null;
    }

    private List<StepDOAdapter> stepAdapters(CaseDO caseDO) {
        List<StepDOAdapter> adapters = new ArrayList<>();
        if (caseDO.getStepList() == null) {
            return adapters;
        }
        caseDO.getStepList()
            .stream()
            .sorted((a, b) -> Integer.compare(a.getOrder() == null ? 0 : a.getOrder(), b.getOrder() == null
                ? 0
                : b.getOrder()))
            .forEach(step -> adapters.add(new StepDOAdapter(step)));
        return adapters;
    }

    private void fillCaseRuntimeFields(AutomationPlaywrightCaseResp resp, List<Map<String, Object>> steps) {
        String startUrl = firstPageUrl(steps);
        Object windowSizeMode = firstConfigValue(steps, "window_size_mode", "maximized");
        Object screenshotMode = firstConfigValue(steps, "screenshot_mode", "standard");
        Object pageErrorCheckEnabled = firstConfigValue(steps, "page_error_check_enabled", 0);
        Object viewportWidth = firstConfigValue(steps, "viewport_width", null);
        Object viewportHeight = firstConfigValue(steps, "viewport_height", null);
        resp.setStartUrl(startUrl);
        resp.setStart_url(startUrl);
        resp.setWindowSizeMode(String.valueOf(windowSizeMode));
        resp.setWindow_size_mode(String.valueOf(windowSizeMode));
        resp.setScreenshotMode(String.valueOf(screenshotMode));
        resp.setScreenshot_mode(String.valueOf(screenshotMode));
        resp.setPageErrorCheckEnabled(toInteger(pageErrorCheckEnabled));
        resp.setPage_error_check_enabled(resp.getPageErrorCheckEnabled());
        resp.setViewportWidth(toInteger(viewportWidth));
        resp.setViewport_width(resp.getViewportWidth());
        resp.setViewportHeight(toInteger(viewportHeight));
        resp.setViewport_height(resp.getViewportHeight());
    }

    private String firstPageUrl(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps) {
            for (String key : List.of("start_url", "startUrl", "url")) {
                String value = StringUtils.trimToEmpty(Objects.toString(step.get(key), ""));
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private Object firstConfigValue(List<Map<String, Object>> steps, String key, Object fallback) {
        for (Map<String, Object> step : steps) {
            Object value = step.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyProjectEnvironment(AutomationPlaywrightCaseResp resp,
                                         AutomationUiSceneDO scene,
                                         Long projectEnvironmentId) {
        ProjectEnvironmentConfigDO environment = projectEnvironmentConfigMapper.selectById(projectEnvironmentId);
        String context = "sceneId=" + scene.getSceneId() + "，caseId=" + resp.getCaseId() + "，projectEnvironmentId="
            + projectEnvironmentId;
        if (environment == null) {
            throw new BusinessException("回放产品环境不存在，" + context);
        }
        if (!Objects.equals(scene.getProjectId(), environment.getProjectId())) {
            throw new BusinessException("回放产品环境与场景所属项目不一致，" + context);
        }
        if (!DisEnableStatusEnum.ENABLE.equals(environment.getStatus())) {
            throw new BusinessException("回放产品环境未启用，" + context);
        }

        PlaybackEnvironmentTarget target = resolvePlaybackTarget(environment, context);
        resp.setProjectEnvironmentId(projectEnvironmentId);
        resp.setProject_environment_id(projectEnvironmentId);
        resp.setProjectEnvironmentName(environment.getName());
        resp.setProject_environment_name(environment.getName());
        try {
            playbackUrlRewriter.rewrite(resp, target.address(), target.frontendPort());
        } catch (BusinessException e) {
            throw new BusinessException(e.getMessage() + "，" + context);
        }
        if (StringUtils.isBlank(resp.getStart_url())) {
            throw new BusinessException("回放用例缺少可改写的起始地址，" + context);
        }
    }

    private PlaybackEnvironmentTarget resolvePlaybackTarget(ProjectEnvironmentConfigDO environment, String context) {
        String lastDomain = StringUtils.trimToEmpty(environment.getLastDomain());
        if (StringUtils.isNotBlank(lastDomain)) {
            return new PlaybackEnvironmentTarget(lastDomain, "");
        }

        ProjectServerConfigDO server = resolvePrimaryServer(environment.getServerConfig());
        if (server == null) {
            throw new BusinessException("回放产品环境未配置服务器信息，" + context);
        }
        String frontendDomain = resolveServerConfigParam(server, "前端域名");
        String frontendPort = resolveServerConfigParam(server, "前端端口");
        String address = StringUtils.isNotBlank(frontendDomain) ? frontendDomain : StringUtils.trimToEmpty(server.getIp());
        if (StringUtils.isBlank(address)) {
            throw new BusinessException("回放产品环境未配置可用的前端域名或服务器 IP，" + context);
        }
        return new PlaybackEnvironmentTarget(address, frontendPort);
    }

    private ProjectServerConfigDO resolvePrimaryServer(List<?> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        ProjectServerConfigDO fallback = null;
        for (Object item : source) {
            ProjectServerConfigDO server = BeanUtil.toBean(item, ProjectServerConfigDO.class);
            if (fallback == null) {
                fallback = server;
            }
            if (DisEnableStatusEnum.ENABLE.equals(server.getStatus())) {
                return server;
            }
        }
        return fallback;
    }

    private String resolveServerConfigParam(ProjectServerConfigDO server, String name) {
        if (server.getConfigList() == null) {
            return "";
        }
        for (Object item : server.getConfigList()) {
            if (!(item instanceof Map<?, ?> map) || !Objects.equals(name, String.valueOf(map.get("paramsName")))) {
                continue;
            }
            Object value = map.get("paramsValue");
            return value == null ? "" : String.valueOf(value).trim();
        }
        return "";
    }

    private record ResolvedCase(AutomationUiSceneDO scene, CaseDO caseDO) {
    }

    private record PlaybackEnvironmentTarget(String address, String frontendPort) {
    }

    private record StepDOAdapter(top.continew.admin.automation.model.entity.ui.StepDO step) {
    }
}
