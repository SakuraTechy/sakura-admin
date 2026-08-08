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

package top.continew.admin.automation.controller;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationPlaywrightCaseControllerTest {

    @Test
    void caseReadEndpointsMustRequireProjectEnvironment() throws NoSuchMethodException {
        Method byKey = AutomationPlaywrightCaseController.class
            .getDeclaredMethod("getCase", String.class, Long.class, String.class, String.class);
        Method byParts = AutomationPlaywrightCaseController.class
            .getDeclaredMethod("getCaseByParts", String.class, String.class, Long.class, String.class, String.class);

        assertThat(findRequestParam(byKey, 1)).satisfies(this::assertRequired);
        assertThat(findRequestParam(byParts, 2)).satisfies(this::assertRequired);
        assertThat(findRequestHeader(byKey, 3)).satisfies(this::assertOptionalCapability);
        assertThat(findRequestHeader(byParts, 4)).satisfies(this::assertOptionalCapability);
    }

    @Test
    void resultEndpointsMustAcceptExecutionCapabilityHeader() throws NoSuchMethodException {
        Method byKey = AutomationPlaywrightCaseController.class
            .getDeclaredMethod("saveResult", String.class, AutomationPlaywrightResultReq.class, String.class);
        Method byParts = AutomationPlaywrightCaseController.class
            .getDeclaredMethod("saveResultByParts", String.class, String.class, AutomationPlaywrightResultReq.class, String.class);

        assertThat(findRequestHeader(byKey, 2)).satisfies(this::assertOptionalCapability);
        assertThat(findRequestHeader(byParts, 3)).satisfies(this::assertOptionalCapability);
    }

    private RequestParam findRequestParam(Method method, int parameterIndex) {
        for (var annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (annotation instanceof RequestParam requestParam) {
                return requestParam;
            }
        }
        return null;
    }

    private RequestHeader findRequestHeader(Method method, int parameterIndex) {
        for (var annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (annotation instanceof RequestHeader requestHeader) {
                return requestHeader;
            }
        }
        return null;
    }

    private void assertRequired(RequestParam requestParam) {
        assertThat(requestParam.required()).isTrue();
        assertThat(requestParam.name()).isBlank();
        assertThat(requestParam.value()).isBlank();
    }

    private void assertOptionalCapability(RequestHeader requestHeader) {
        assertThat(requestHeader.required()).isFalse();
        assertThat(requestHeader.value()).isEqualTo("X-Execution-Capability");
    }
}
