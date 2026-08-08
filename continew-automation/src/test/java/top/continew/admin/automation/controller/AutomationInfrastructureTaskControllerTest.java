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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskCreateReq;
import top.continew.admin.automation.service.AutomationInfrastructureTaskService;
import top.continew.admin.automation.service.AutomationInfrastructureTaskService.ArtifactDownload;

class AutomationInfrastructureTaskControllerTest {

    @Test
    void allTaskEndpointsDeclareTheirDedicatedPermissions() throws Exception {
        assertPermission("create", "automation:automationUiScene:execute-infrastructure", AutomationInfrastructureTaskCreateReq.class, String.class);
        assertPermission("get", "automation:automationUiScene:get", String.class, Long.class, String.class);
        assertPermission("getStatement", "automation:automationUiScene:get", String.class);
        assertPermission("cancel", "automation:automationUiScene:execute-infrastructure", String.class, String.class);
        assertPermission("downloadArtifact", "automation:automationUiScene:download-infrastructure-artifact", String.class, String.class);
    }

    @Test
    void artifactDownloadUsesDedicatedPermissionAndServiceAuthorizationBoundary() throws Exception {
        AutomationInfrastructureTaskService service = mock(AutomationInfrastructureTaskService.class);
        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
        when(service.downloadArtifact("INFRA_1", "capability"))
            .thenReturn(new ArtifactDownload("INFRA_1.json", "application/json", bytes, "a".repeat(64)));
        AutomationInfrastructureTaskController controller = new AutomationInfrastructureTaskController(service);

        ResponseEntity<byte[]> response = controller.downloadArtifact("INFRA_1", "capability");

        assertThat(response.getBody()).isEqualTo(bytes);
        assertThat(response.getHeaders().getFirst("X-Content-Sha256")).isEqualTo("a".repeat(64));
        Method method = AutomationInfrastructureTaskController.class
            .getMethod("downloadArtifact", String.class, String.class);
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("automation:automationUiScene:download-infrastructure-artifact");
    }

    private void assertPermission(String methodName,
                                  String expectedPermission,
                                  Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = AutomationInfrastructureTaskController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(expectedPermission);
    }
}
