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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.util.AutomationUiSceneXmlUtils;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;

/**
 * 为 Jenkins/Selenium 兼容入口冻结服务端有效执行配置。
 */
@Component
@RequiredArgsConstructor
public class AutomationJenkinsExecutionConfigResolver {

    private static final List<String> RESOLUTION_ORDER = List
        .of("system-default", "case-default", "environment", "execution-override", "platform-policy");

    private final EffectiveExecutionConfigResolver effectiveExecutionConfigResolver;
    private final AutomationCaseExecutionClassifier executionClassifier;

    public ResolvedScene resolve(AutomationUiSceneDO scene,
                                 ProjectEnvironmentConfigDO projectEnvironment,
                                 String browserName,
                                 String environmentStartUrl) {
        Map<String, Object> environmentDefaults = new LinkedHashMap<>();
        environmentDefaults.put("browser", AutomationUiSceneXmlUtils.normalizeBrowserName(browserName));
        if (StringUtils.isNotBlank(environmentStartUrl)) {
            environmentDefaults.put("start_url", environmentStartUrl.trim());
        }

        Map<String, Map<String, Object>> caseConfigs = new LinkedHashMap<>();
        Map<String, String> caseStartUrls = new LinkedHashMap<>();
        if (scene != null && scene.getCaseList() != null) {
            for (CaseDO caseDO : scene.getCaseList()) {
                if (caseDO == null) {
                    continue;
                }
                EffectiveExecutionConfigResolver.Resolved resolved = effectiveExecutionConfigResolver
                    .resolve(caseDO, projectEnvironment, environmentDefaults, Map.of(), executionClassifier
                        .hasBrowserSteps(caseDO));
                String caseId = StringUtils.defaultString(caseDO.getId());
                caseConfigs.put(caseId, resolved.values());
                String startUrl = StringUtils.trimToEmpty(String.valueOf(resolved.values()
                    .getOrDefault("start_url", "")));
                if (StringUtils.isNotBlank(startUrl)) {
                    caseStartUrls.put(caseId, startUrl);
                }
            }
        }

        Map<String, Object> auditConfig = new LinkedHashMap<>();
        auditConfig.put("cases", caseConfigs);
        auditConfig.put("resolutionOrder", RESOLUTION_ORDER);
        return new ResolvedScene(auditConfig, caseStartUrls, AutomationUiSceneXmlUtils
            .normalizeBrowserName(browserName));
    }

    public record ResolvedScene(Map<String, Object> auditConfig, Map<String, String> caseStartUrls, String browser) {
    }
}
