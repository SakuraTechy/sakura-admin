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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

class AutomationRecordingImportServiceImplTest {

    @Test
    void replaceCasePreservesTheExistingBusinessCaseName() throws Exception {
        AutomationRecordingImportServiceImpl service = new AutomationRecordingImportServiceImpl(null, null, null, null, null);
        CaseDO target = new CaseDO();
        target.setId("CASE_001");
        target.setName("登录主流程");
        target.setOrder(2);
        CaseDO replacement = new CaseDO();
        replacement.setName("Playwright 录制用例");

        Method method = AutomationRecordingImportServiceImpl.class
            .getDeclaredMethod("preserveCaseIdentity", CaseDO.class, CaseDO.class);
        method.setAccessible(true);
        method.invoke(service, target, replacement);

        assertThat(replacement.getId()).isEqualTo("CASE_001");
        assertThat(replacement.getName()).isEqualTo("登录主流程");
        assertThat(replacement.getOrder()).isEqualTo(2);
    }

    @Test
    void replaceCaseUsesTheFirstRecordedPageAsTheNewStartUrl() throws Exception {
        AutomationRecordingImportServiceImpl service = new AutomationRecordingImportServiceImpl(null, null, null, null, null);
        CaseDO replacement = new CaseDO();
        CaseExecutionConfigDO executionConfig = new CaseExecutionConfigDO();
        executionConfig.setStartUrl("https://example.test/login");
        replacement.setExecutionConfig(executionConfig);
        StepDO step = new StepDO();
        step.setConfigList(List
            .of(config("start_url", "https://example.test/login"), config("url", "https://example.test/sys-monitor"), config("playwright_step", "{\"url\":\"https://example.test/sys-monitor\"}")));
        replacement.setStepList(List.of(step));

        Method method = AutomationRecordingImportServiceImpl.class
            .getDeclaredMethod("synchronizeReplacementStartUrl", CaseDO.class);
        method.setAccessible(true);
        method.invoke(service, replacement);

        assertThat(replacement.getExecutionConfig().getStartUrl()).isEqualTo("https://example.test/sys-monitor");
        assertThat(step.getConfigList()).filteredOn(item -> "start_url".equals(item.getParamsName()))
            .extracting(StepDO.Config::getParamsValue)
            .containsExactly("https://example.test/sys-monitor");
        assertThat(step.getConfigList()).filteredOn(item -> "playwright_step".equals(item.getParamsName()))
            .extracting(StepDO.Config::getParamsValue)
            .containsExactly("{\"url\":\"https://example.test/sys-monitor\"}");
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

}
