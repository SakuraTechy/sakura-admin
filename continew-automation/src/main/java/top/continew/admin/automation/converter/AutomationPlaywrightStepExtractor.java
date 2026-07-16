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

package top.continew.admin.automation.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * 从 admin StepDO 反向提取 Playwright 原始 step。
 *
 * @author Codex
 */
@Component
@RequiredArgsConstructor
public class AutomationPlaywrightStepExtractor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public Map<String, Object> extract(StepDO stepDO, int index) {
        Map<String, String> configs = toConfigMap(stepDO.getConfigList());
        String rawStep = configs.get("playwright_step");
        if (rawStep != null && !rawStep.isBlank()) {
            Map<String, Object> step = parseMap(rawStep);
            // case 级配置仍挂在 StepDO.configList 中，补到响应顶层供两种执行器读取；原始 step 内容不改写。
            copyIfAbsent(step, configs, "start_url");
            copyIfAbsent(step, configs, "end_url");
            copyIfAbsent(step, configs, "screenshot_mode");
            copyIfAbsent(step, configs, "page_error_check_enabled");
            copyIfAbsent(step, configs, "window_size_mode");
            copyIfAbsent(step, configs, "viewport_width");
            copyIfAbsent(step, configs, "viewport_height");
            return step;
        }
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", stepDO.getId());
        step.put("step_index", index);
        step.put("action_type", configs.getOrDefault("action_type", fallbackActionType(stepDO.getOperationValue())));
        step.put("target_selector", configs.getOrDefault("target_selector", ""));
        step.put("target_xpath", configs.getOrDefault("target_xpath", ""));
        if (configs.containsKey("locator_meta")) {
            step.put("locator_meta", parseMap(configs.get("locator_meta")));
        }
        step.put("value", configs.getOrDefault("value", ""));
        step.put("value_masked", configs.getOrDefault("value_masked", "0"));
        step.put("url", configs.getOrDefault("url", ""));
        step.put("description", stepDO.getName());
        step.put("wait_before", configs.getOrDefault("wait_before", "0"));
        step.put("is_overlay", configs.getOrDefault("is_overlay", "0"));
        return step;
    }

    private void copyIfAbsent(Map<String, Object> target, Map<String, String> configs, String key) {
        if (!target.containsKey(key) && configs.containsKey(key)) {
            target.put(key, configs.get(key));
        }
    }

    private Map<String, String> toConfigMap(List<StepDO.Config> configList) {
        Map<String, String> configs = new LinkedHashMap<>();
        if (configList == null) {
            return configs;
        }
        for (StepDO.Config config : configList) {
            if (config != null && config.getParamsName() != null) {
                configs.put(config.getParamsName(), config.getParamsValue());
            }
        }
        return configs;
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException("Playwright step 解析失败：" + e.getMessage());
        }
    }

    private String fallbackActionType(String operationValue) {
        if (operationValue == null || operationValue.isBlank()) {
            return "unknown";
        }
        return operationValue.startsWith("pw-") ? operationValue.substring(3) : operationValue;
    }
}
