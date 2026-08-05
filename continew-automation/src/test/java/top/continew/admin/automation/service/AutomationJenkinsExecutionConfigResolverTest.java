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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;

class AutomationJenkinsExecutionConfigResolverTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldFreezeCaseConfigAndAuditSourcesBeforeJenkinsStarts() {
        AutomationCaseExecutionClassifier classifier = mock(AutomationCaseExecutionClassifier.class);
        AutomationJenkinsExecutionConfigResolver resolver = new AutomationJenkinsExecutionConfigResolver(new EffectiveExecutionConfigResolver(), classifier);

        CaseDO recordedCase = new CaseDO();
        recordedCase.setId("RECORDED");
        CaseExecutionConfigDO recordedConfig = new CaseExecutionConfigDO();
        recordedConfig.setStartUrl("https://recorded.example/login");
        recordedCase.setExecutionConfig(recordedConfig);

        CaseDO environmentCase = new CaseDO();
        environmentCase.setId("ENVIRONMENT");
        when(classifier.hasBrowserSteps(recordedCase)).thenReturn(true);
        when(classifier.hasBrowserSteps(environmentCase)).thenReturn(true);

        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(7L);
        scene.setCaseList(List.of(recordedCase, environmentCase));
        ProjectEnvironmentConfigDO environment = new ProjectEnvironmentConfigDO();
        environment.setLastDomain("https://legacy.example");

        AutomationJenkinsExecutionConfigResolver.ResolvedScene resolved = resolver
            .resolve(scene, environment, "火狐", "https://environment.example");

        Map<String, Map<String, Object>> cases = (Map<String, Map<String, Object>>)resolved.auditConfig().get("cases");
        assertThat(resolved.browser()).isEqualTo("firefox");
        assertThat(cases.get("RECORDED")).containsEntry("browser", "firefox")
            .containsEntry("start_url", "https://environment.example");
        assertThat((Map<String, String>)cases.get("RECORDED").get("sources")).containsEntry("browser", "environment")
            .containsEntry("start_url", "environment");
        assertThat(cases.get("ENVIRONMENT")).containsEntry("start_url", "https://environment.example");
        assertThat((Map<String, String>)cases.get("ENVIRONMENT").get("sources"))
            .containsEntry("start_url", "environment");
        assertThat(resolved.caseStartUrls()).containsEntry("RECORDED", "https://environment.example")
            .containsEntry("ENVIRONMENT", "https://environment.example");
    }
}
