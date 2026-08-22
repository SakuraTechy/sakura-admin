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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.starter.core.exception.BadRequestException;

class AutomationUiExecutionScopeSupportTest {

    @Test
    void debugScopeShouldAllowJenkinsBuildNumberWithoutClassifyingItAsTest() {
        AutomationUiExecutionScopeReq request = scope(" DEBUG ");
        request.setBuildNumber(18);

        AutomationUiExecutionScopeReq normalized = AutomationUiExecutionScopeSupport.normalize(request);

        assertThat(normalized.getRecordSource()).isEqualTo("debug");
        assertThat(normalized.getBuildNumber()).isEqualTo(18);
    }

    @Test
    void debugScopeShouldRejectTestBindings() {
        AutomationUiExecutionScopeReq request = scope("debug");
        request.setTestPlanId(7L);

        assertThatThrownBy(() -> AutomationUiExecutionScopeSupport.normalize(request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_EXECUTION_SCOPE");
    }

    @Test
    void shouldRejectUnknownSourceAndFilterValues() {
        assertThatThrownBy(() -> AutomationUiExecutionScopeSupport.normalize(scope("global")))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_RECORD_SOURCE");
        assertThatThrownBy(() -> AutomationUiExecutionScopeSupport.validateStatus("anything"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_STATUS");
        assertThatThrownBy(() -> AutomationUiExecutionScopeSupport.validateResult("anything"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_RESULT");
    }

    private AutomationUiExecutionScopeReq scope(String source) {
        AutomationUiExecutionScopeReq request = new AutomationUiExecutionScopeReq();
        request.setRecordSource(source);
        return request;
    }
}
