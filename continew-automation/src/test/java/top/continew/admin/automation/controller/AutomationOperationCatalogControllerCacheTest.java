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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper;
import top.continew.admin.automation.service.AutomationExecutorRegistrationService;
import top.continew.admin.automation.service.AutomationOperationCatalogService;
import top.continew.admin.automation.support.AutomationExecutionAgentClient;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;

class AutomationOperationCatalogControllerCacheTest {

    @Test
    void shouldReuseAgentHealthWithinConfiguredShortTtl() {
        AutomationExecutionAgentClient agentClient = mock(AutomationExecutionAgentClient.class);
        when(agentClient.health()).thenReturn(Map.of("status", "ok", "agent_types", List.of("browser"), "features", List
            .of("shell")));
        AutomationOperationCatalogController controller = new AutomationOperationCatalogController(mock(AutomationOperationCatalogService.class), mock(AutomationExecutorRegistrationService.class), mock(AutomationUiSceneQueryMapper.class), mock(AutomationUiSceneAccessScopeResolver.class), mock(ProjectEnvironmentConfigMapper.class), mock(ProjectConfigMapper.class), agentClient, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "healthCacheEnabled", true);
        ReflectionTestUtils.setField(controller, "healthCacheTtl", Duration.ofSeconds(5));

        Object first = ReflectionTestUtils.invokeMethod(controller, "readAgentHealth");
        Object second = ReflectionTestUtils.invokeMethod(controller, "readAgentHealth");
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        verify(agentClient, times(1)).health();
    }
}
