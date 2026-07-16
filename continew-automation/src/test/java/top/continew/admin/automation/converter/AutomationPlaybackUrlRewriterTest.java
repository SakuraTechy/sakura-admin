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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;

class AutomationPlaybackUrlRewriterTest {

    private final AutomationPlaybackUrlRewriter rewriter = new AutomationPlaybackUrlRewriter();

    @Test
    void shouldRewriteCaseAndPageUrlsWithoutChangingPathQueryOrFragment() {
        AutomationPlaywrightCaseResp testCase = new AutomationPlaywrightCaseResp();
        testCase.setStart_url("https://172.19.5.45/login?from=admin#top");
        Map<String, Object> navigate = new LinkedHashMap<>();
        navigate.put("action_type", "navigate");
        navigate.put("url", "https://172.19.5.45/home");
        navigate.put("start_url", "https://172.19.5.45/login");
        navigate.put("end_url", "https://172.19.5.45/home?tab=1");
        navigate.put("value", "https://172.19.5.45/profile#base");
        testCase.setSteps(new ArrayList<>(List.of(navigate)));

        rewriter.rewrite(testCase, "https://172.19.5.47/login", "");

        assertThat(testCase.getStart_url()).isEqualTo("https://172.19.5.47/login?from=admin#top");
        assertThat(testCase.getEnvironment_origin()).isEqualTo("https://172.19.5.47");
        assertThat(navigate).containsEntry("url", "https://172.19.5.47/home")
            .containsEntry("start_url", "https://172.19.5.47/login")
            .containsEntry("end_url", "https://172.19.5.47/home?tab=1")
            .containsEntry("value", "https://172.19.5.47/profile#base");
    }

    @Test
    void shouldUseConfiguredPortAndLeaveRelativeOrNonHttpValuesUntouched() {
        assertThat(rewriter.rewriteUrl("http://172.19.5.45:8080/a", "172.19.5.47", "9443"))
            .isEqualTo("http://172.19.5.47:9443/a");
        assertThat(rewriter.rewriteUrl("/relative/path", "172.19.5.47", "9443")).isEqualTo("/relative/path");
        assertThat(rewriter.rewriteUrl("about:blank", "172.19.5.47", "9443")).isEqualTo("about:blank");
    }

    @Test
    void shouldRejectInvalidEnvironmentAddressOrPort() {
        assertThatThrownBy(() -> rewriter.rewriteUrl("https://example.com/login", "https://", ""))
            .hasMessageContaining("产品环境前端地址无效");
        assertThatThrownBy(() -> rewriter.rewriteUrl("https://example.com/login", "172.19.5.47", "70000"))
            .hasMessageContaining("产品环境前端端口无效");
    }
}
