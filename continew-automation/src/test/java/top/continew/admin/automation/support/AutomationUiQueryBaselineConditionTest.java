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

package top.continew.admin.automation.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationUiQueryBaselineConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ProbeConfiguration.class);

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AutomationUiQueryBaselineFilter.class);
            assertThat(context).doesNotHaveBean(AutomationUiQueryBaselineResponseAdvice.class);
        });
    }

    @Test
    void shouldRegisterOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("automation.ui-query.baseline.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(AutomationUiQueryBaselineFilter.class);
            assertThat(context).hasSingleBean(AutomationUiQueryBaselineResponseAdvice.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AutomationUiQueryBaselineFilter.class, AutomationUiQueryBaselineResponseAdvice.class})
    static class ProbeConfiguration {
    }
}
