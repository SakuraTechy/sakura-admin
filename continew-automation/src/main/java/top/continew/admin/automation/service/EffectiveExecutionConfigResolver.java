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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * 计算一次执行唯一的有效配置。
 *
 * <p>顺序固定为安全默认值、Case 默认、环境解析、单次 allowlist 覆盖和平台上限；
 * 结果及字段来源由调用方写入执行事实，Runner/CueCast 不再自行合并。</p>
 */
@Component
public class EffectiveExecutionConfigResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> OVERRIDE_KEYS = List
        .of("browser", "session_mode", "headed", "ignore_https_errors", "trace", "video", "step_timeout_ms", "case_timeout_ms", "slow_mo_ms", "finish_delay_ms", "page_error_check_enabled", "screenshot_mode", "browser_bootstrap_mode", "start_url", "window_size_mode", "viewport_width", "viewport_height", "live_frame_quality");

    public Resolved resolve(CaseDO caseDO,
                            ProjectEnvironmentConfigDO environment,
                            Map<String, Object> overrides,
                            boolean hasBrowserSteps) {
        return resolve(caseDO, environment, Map.of(), overrides, hasBrowserSteps);
    }

    public Resolved resolve(CaseDO caseDO,
                            ProjectEnvironmentConfigDO environment,
                            Map<String, Object> environmentDefaults,
                            Map<String, Object> overrides,
                            boolean hasBrowserSteps) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        put(values, sources, "browser", "chromium", "system-default");
        put(values, sources, "session_mode", "isolated", "system-default");
        put(values, sources, "headed", false, "system-default");
        put(values, sources, "ignore_https_errors", true, "system-default");
        put(values, sources, "trace", "retain-on-failure", "system-default");
        put(values, sources, "video", "retain-on-failure", "system-default");
        put(values, sources, "step_timeout_ms", 6000, "system-default");
        put(values, sources, "case_timeout_ms", 600000, "system-default");
        put(values, sources, "slow_mo_ms", 0, "system-default");
        put(values, sources, "finish_delay_ms", 0, "system-default");
        put(values, sources, "live_frame_quality", "smooth", "system-default");
        put(values, sources, "screenshot_mode", "standard", "system-default");
        put(values, sources, "page_error_check_enabled", false, "system-default");
        put(values, sources, "window_size_mode", "maximized", "system-default");
        put(values, sources, "browser_bootstrap_mode", hasBrowserSteps ? "launch" : "none", "system-default");

        CaseExecutionConfigDO config = caseDO == null ? null : caseDO.getExecutionConfig();
        if (config != null) {
            putIfPresent(values, sources, "start_url", config.getStartUrl(), "case-default");
            putIfPresent(values, sources, "window_size_mode", config.getWindowSizeMode(), "case-default");
            putIfPresent(values, sources, "viewport_width", config.getViewportWidth(), "case-default");
            putIfPresent(values, sources, "viewport_height", config.getViewportHeight(), "case-default");
            putIfPresent(values, sources, "screenshot_mode", config.getScreenshotMode(), "case-default");
            if (config.getPageErrorCheckEnabled() != null) {
                put(values, sources, "page_error_check_enabled", config
                    .getPageErrorCheckEnabled() != 0, "case-default");
            }
        }
        if (hasBrowserSteps && StringUtils.isBlank(string(values.get("start_url")))) {
            // 录制数据的起始地址可能只保存在 StepDO.configList；计划批次不能因未展开的 case 配置而丢失它。
            putIfPresent(values, sources, "start_url", firstStepPageUrl(caseDO), "case-default");
        }
        applyAllowedValues(values, sources, environmentDefaults, "environment");
        if (environment != null && StringUtils.isBlank(string(values.get("start_url")))) {
            putIfPresent(values, sources, "start_url", environment.getLastDomain(), "environment");
        }
        applyAllowedValues(values, sources, overrides, "execution-override");
        enforceBrowserBootstrapPolicy(values, sources, hasBrowserSteps);
        clamp(values, "step_timeout_ms", 1000, 300000);
        clamp(values, "case_timeout_ms", 10000, 3600000);
        clamp(values, "slow_mo_ms", 0, 10000);
        clamp(values, "finish_delay_ms", 0, 600000);
        if (hasBrowserSteps && "launch".equals(values.get("browser_bootstrap_mode")) && StringUtils
            .isBlank(string(values.get("start_url")))) {
            throw new BusinessException("EXECUTION_CONFIG_INVALID：浏览器 launch 模式缺少 start_url");
        }
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.put("sources", sources);
        result.put("resolution_order", List
            .of("system-default", "case-default", "environment", "execution-override", "platform-policy"));
        return new Resolved(result, sources);
    }

    private void enforceBrowserBootstrapPolicy(Map<String, Object> values,
                                               Map<String, String> sources,
                                               boolean hasBrowserSteps) {
        String mode = string(values.get("browser_bootstrap_mode")).trim().toLowerCase();
        if (!hasBrowserSteps) {
            // 纯基础设施用例不得因客户端覆盖而创建浏览器，最终平台策略必须固定为 none。
            put(values, sources, "browser_bootstrap_mode", "none", "platform-policy");
            return;
        }
        if (!List.of("launch", "attach").contains(mode)) {
            throw new BusinessException("EXECUTION_CONFIG_INVALID：浏览器步骤仅支持 launch 或 attach 模式");
        }
        values.put("browser_bootstrap_mode", mode);
    }

    private void applyAllowedValues(Map<String, Object> values,
                                    Map<String, String> sources,
                                    Map<String, Object> candidates,
                                    String source) {
        Map<String, Object> safeCandidates = candidates == null ? Map.of() : candidates;
        for (Map.Entry<String, Object> entry : safeCandidates.entrySet()) {
            String key = canonicalKey(entry.getKey());
            if (!OVERRIDE_KEYS.contains(key)) {
                throw new BusinessException("EXECUTION_CONFIG_FIELD_NOT_ALLOWED：不允许覆盖 " + entry.getKey());
            }
            if (entry.getValue() == null) {
                continue;
            }
            put(values, sources, key, entry.getValue(), source);
        }
    }

    private String firstStepPageUrl(CaseDO caseDO) {
        if (caseDO == null || caseDO.getStepList() == null) {
            return "";
        }
        for (StepDO step : caseDO.getStepList()) {
            if (step == null || step.getConfigList() == null) {
                continue;
            }
            String fallbackUrl = "";
            for (StepDO.Config config : step.getConfigList()) {
                if (config == null || StringUtils.isBlank(config.getParamsName())) {
                    continue;
                }
                String name = config.getParamsName().trim();
                String value = StringUtils.trimToEmpty(config.getParamsValue());
                if ("start_url".equalsIgnoreCase(name) || "startUrl".equals(name)) {
                    if (StringUtils.isNotBlank(value)) {
                        return value;
                    }
                }
                if ("url".equalsIgnoreCase(name) && StringUtils.isBlank(fallbackUrl)) {
                    fallbackUrl = value;
                }
                if ("playwright_step".equals(name) && StringUtils.isNotBlank(value)) {
                    String rawStartUrl = jsonText(value, "start_url");
                    if (StringUtils.isBlank(rawStartUrl)) {
                        rawStartUrl = jsonText(value, "startUrl");
                    }
                    if (StringUtils.isBlank(rawStartUrl)) {
                        rawStartUrl = jsonText(value, "url");
                    }
                    if (StringUtils.isNotBlank(rawStartUrl)) {
                        return rawStartUrl;
                    }
                }
            }
            if (StringUtils.isNotBlank(fallbackUrl)) {
                return fallbackUrl;
            }
        }
        return "";
    }

    private String jsonText(String rawJson, String field) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(rawJson).get(field);
            return node == null || node.isNull() ? "" : node.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void clamp(Map<String, Object> values, String key, int min, int max) {
        if (values.get(key) == null) {
            return;
        }
        try {
            int value = Integer.parseInt(String.valueOf(values.get(key)));
            values.put(key, Math.max(min, Math.min(max, value)));
        } catch (NumberFormatException e) {
            throw new BusinessException("EXECUTION_CONFIG_INVALID：" + key + " 必须是整数");
        }
    }

    private String canonicalKey(String key) {
        String value = StringUtils.defaultString(key).trim();
        String snake = value.replaceAll("([a-z])([A-Z])", "$1_$2").replace('-', '_').toLowerCase();
        return snake;
    }

    private void putIfPresent(Map<String, Object> values,
                              Map<String, String> sources,
                              String key,
                              Object value,
                              String source) {
        if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
            put(values, sources, key, value, source);
        }
    }

    private void put(Map<String, Object> values, Map<String, String> sources, String key, Object value, String source) {
        values.put(key, value);
        sources.put(key, source);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record Resolved(Map<String, Object> values, Map<String, String> sources) {
    }
}
