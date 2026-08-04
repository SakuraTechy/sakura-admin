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

package top.continew.admin.automation.model.req.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AutomationInfrastructureTaskCreateReqTest {

    @Test
    void shouldAcceptSnakeCaseRuntimeBindingsWithoutPersistingContractField() throws Exception {
        AutomationInfrastructureTaskCreateReq request = new ObjectMapper().readValue("""
            {"caseKey":"scene:case","stepId":"step","projectEnvironmentId":1,
             "runtime_bindings":{"token":"runtime-only"}}
            """, AutomationInfrastructureTaskCreateReq.class);

        assertThat(request.getRuntimeBindings()).containsEntry("token", "runtime-only");
    }
}
