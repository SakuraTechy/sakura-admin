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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import top.continew.admin.common.jenkins.JenkinsService;

class JenkinsServiceSecurityTest {

    @Test
    void shouldRedactCredentialsWithoutChangingJenkinsRequestParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ServerUserName", "tester");
        params.put("ServerPassWord", "server-secret");
        params.put("DataBasePassWord", "database-secret");
        params.put("api_token", "token-secret");

        Map<String, String> redacted = JenkinsService.redactSensitiveParams(params);

        assertThat(redacted).containsEntry("ServerUserName", "tester")
            .containsEntry("ServerPassWord", "******")
            .containsEntry("DataBasePassWord", "******")
            .containsEntry("api_token", "******");
        assertThat(params).containsEntry("ServerPassWord", "server-secret")
            .containsEntry("DataBasePassWord", "database-secret");
    }

    @Test
    void shouldFilterOnlyParametersDeclaredByJenkinsJob() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Domain", "https://example.test");
        params.put("sceneWorkspace", "D:/workspace");
        params.put("ServerPassWord", "server-secret");

        Map<String, String> filtered = JenkinsService.filterDeclaredParameters(params, Set
            .of("Domain", "ServerPassWord"));

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Domain", "https://example.test");
        expected.put("ServerPassWord", "server-secret");
        assertThat(filtered).containsExactlyEntriesOf(expected);
        assertThat(params).containsEntry("sceneWorkspace", "D:/workspace");
    }

    @Test
    void shouldExtractDeclaredParameterNamesWithoutReadingParameterValues() {
        String response = "{\"property\":[{\"parameterDefinitions\":[" + "{\"name\":\"Domain\",\"defaultParameterValue\":{\"value\":\"secret\"}}," + "{\"name\":\"testReportId\"}]}]}";

        assertThat(JenkinsService.extractDeclaredParameterNames(response)).containsExactly("Domain", "testReportId");
    }

    @Test
    void shouldRejectParameterizedBuildWhenJobHasNoParameterDeclaration() {
        assertThatThrownBy(() -> JenkinsService.filterDeclaredParameters(Map.of("Domain", "https://example.test"), Set
            .of())).isInstanceOf(IllegalStateException.class).hasMessageContaining("未声明可用参数");
    }

    @Test
    void shouldRejectParameterizedBuildWhenDeclarationsHaveNoIntersection() {
        assertThatThrownBy(() -> JenkinsService.filterDeclaredParameters(Map.of("Domain", "https://example.test"), Set
            .of("Other"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("无交集");
    }
}
