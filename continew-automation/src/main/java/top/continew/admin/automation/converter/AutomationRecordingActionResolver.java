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

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.ui.StepDO;

/**
 * 统一判定步骤来源和可编辑投影。
 *
 * <p>存在 playwright_step 只说明有 canonical 快照；只有明确 source 或 recording_id 才算录制事实，
 * 防止手工目录步骤被页面误显示为录制数据。</p>
 */
@Component
@RequiredArgsConstructor
public class AutomationRecordingActionResolver {

    private final AutomationOperationStepReverseAdapter reverseAdapter;

    public Resolution resolve(StepDO step) {
        Map<String, String> config = configMap(step);
        String source = text(config.get("source"));
        String recordingId = text(config.get("recording_id"));
        boolean recording = "sakura-playwright".equalsIgnoreCase(source) || !recordingId.isBlank();
        AutomationOperationStepReverseAdapter.ReverseResult reverse = reverseAdapter.adapt(step);
        String normalizedSource = recording ? "sakura-playwright" : hasMethodConfig(config) ? "admin-manual" : "legacy";
        return new Resolution(normalizedSource, recording, recordingId, reverse);
    }

    private boolean hasMethodConfig(Map<String, String> config) {
        return !text(config.get("method_code")).isBlank() || !text(config.get("method_config")).isBlank();
    }

    private Map<String, String> configMap(StepDO step) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (step != null && step.getConfigList() != null) {
            step.getConfigList().forEach(item -> {
                if (item != null && item.getParamsName() != null) {
                    result.put(item.getParamsName(), item.getParamsValue());
                }
            });
        }
        return result;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record Resolution(String source, boolean recording, String recordingId,
                             AutomationOperationStepReverseAdapter.ReverseResult reverse) {

        public List<String> warnings() {
            return reverse == null ? List.of("步骤无法反向解析") : reverse.warnings();
        }
    }
}
