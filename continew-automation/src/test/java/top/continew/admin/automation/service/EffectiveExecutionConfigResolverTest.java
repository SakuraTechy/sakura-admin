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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;

class EffectiveExecutionConfigResolverTest {

    private final EffectiveExecutionConfigResolver resolver = new EffectiveExecutionConfigResolver();

    @Test
    void shouldApplyCaseEnvironmentAndAllowlistedOverrideInOrder() {
        CaseExecutionConfigDO caseConfig = new CaseExecutionConfigDO();
        caseConfig.setStartUrl("https://case.example/login");
        caseConfig.setPageErrorCheckEnabled(1);
        CaseDO caseDO = new CaseDO();
        caseDO.setExecutionConfig(caseConfig);
        ProjectEnvironmentConfigDO environment = new ProjectEnvironmentConfigDO();
        environment.setLastDomain("https://environment.example");

        EffectiveExecutionConfigResolver.Resolved resolved = resolver.resolve(caseDO, environment, Map
            .of("startUrl", "https://environment.example/login"), Map.of("stepTimeoutMs", 12000, "headed", true), true);

        assertThat(resolved.values()).containsEntry("start_url", "https://environment.example/login")
            .containsEntry("step_timeout_ms", 12000)
            .containsEntry("headed", true)
            .containsEntry("window_size_mode", "maximized")
            .containsEntry("page_error_check_enabled", true);
        assertThat(resolved.sources()).containsEntry("step_timeout_ms", "execution-override")
            .containsEntry("window_size_mode", "system-default")
            .containsEntry("start_url", "environment");
    }

    @Test
    void shouldRejectUnknownExecutionOverride() {
        assertThatThrownBy(() -> resolver.resolve(new CaseDO(), null, Map.of("command", "rm -rf /"), false))
            .hasMessageContaining("EXECUTION_CONFIG_FIELD_NOT_ALLOWED");
    }

    @Test
    void shouldDisableBrowserBootstrapForPureInfrastructureCase() {
        EffectiveExecutionConfigResolver.Resolved resolved = resolver.resolve(new CaseDO(), null, Map
            .of("browserBootstrapMode", "launch"), false);

        assertThat(resolved.values()).containsEntry("browser_bootstrap_mode", "none");
        assertThat(resolved.sources()).containsEntry("browser_bootstrap_mode", "platform-policy");
    }

    @Test
    void shouldRejectNoneBootstrapWhenCaseContainsBrowserSteps() {
        assertThatThrownBy(() -> resolver.resolve(browserCase("https://case.example"), null, Map
            .of("browserBootstrapMode", "none"), true)).hasMessageContaining("浏览器步骤仅支持 launch 或 attach 模式");
    }

    @Test
    void shouldReadStartUrlFromRecordedStepConfigWhenCaseConfigIsAbsent() {
        StepDO step = new StepDO();
        step.setConfigList(List
            .of(config("playwright_step", "{\"action_type\":\"click\",\"url\":\"https://recorded.example/login\"}")));
        CaseDO caseDO = new CaseDO();
        caseDO.setStepList(List.of(step));

        EffectiveExecutionConfigResolver.Resolved resolved = resolver.resolve(caseDO, null, Map.of(), true);

        assertThat(resolved.values()).containsEntry("start_url", "https://recorded.example/login");
        assertThat(resolved.sources()).containsEntry("start_url", "case-default");
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

    private CaseDO browserCase(String startUrl) {
        CaseExecutionConfigDO executionConfig = new CaseExecutionConfigDO();
        executionConfig.setStartUrl(startUrl);
        CaseDO caseDO = new CaseDO();
        caseDO.setExecutionConfig(executionConfig);
        return caseDO;
    }
}
