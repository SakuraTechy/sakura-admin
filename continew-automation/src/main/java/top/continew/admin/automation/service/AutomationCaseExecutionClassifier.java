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

import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

/**
 * 根据服务端规范化步骤判断用例是否需要浏览器会话。
 */
@Component
@RequiredArgsConstructor
public class AutomationCaseExecutionClassifier {

    private static final Set<String> INFRASTRUCTURE_ACTIONS = Set
        .of("server_command", "database_sql", "database_native", "host_command", "host_file_lookup", "host_file_delete", "server_file_upload", "global_variable_system_info", "global_variable_available_ip", "global_variable_property", "host_pointer_move", "captcha_ocr");

    private final AutomationPlaywrightStepExtractor stepExtractor;

    public boolean hasBrowserSteps(CaseDO caseDO) {
        if (caseDO == null || caseDO.getStepList() == null) {
            return false;
        }
        for (StepDO step : caseDO.getStepList()) {
            Map<String, Object> rawStep = stepExtractor.extract(step, 0);
            String actionType = StringUtils.trimToEmpty(String.valueOf(rawStep.getOrDefault("action_type", "")))
                .toLowerCase();
            if (StringUtils.isNotBlank(actionType) && !INFRASTRUCTURE_ACTIONS.contains(actionType)) {
                return true;
            }
        }
        return false;
    }
}
