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

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import top.continew.admin.automation.model.resp.AutomationUiSceneDefinitionResp;
import top.continew.admin.automation.service.AutomationUiSceneQueryService;
import top.continew.admin.automation.support.AutomationUiDefinitionProjectionUnavailableException;

class AutomationUiSceneDefinitionProjectionControllerTest {

    @Test
    void pendingShouldReturnBounded202WithoutEtag() {
        AutomationUiSceneQueryService queryService = mock(AutomationUiSceneQueryService.class);
        when(queryService.definition(8L))
            .thenThrow(new AutomationUiDefinitionProjectionUnavailableException(true, "pending-id", 8L, 4L, "building"));
        AutomationUiSceneController controller = controller(queryService);

        ResponseEntity<?> response = controller.definition(8L, mock(HttpServletRequest.class));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(response.getHeaders().getETag()).isNull();
        assertThat(response.getHeaders().getCacheControl()).contains("no-store", "private");
        Map<?, ?> body = (Map<?, ?>)response.getBody();
        assertThat(body.get("sceneDbId")).isEqualTo(8L);
        assertThat(body.get("definitionVersion")).isEqualTo(4L);
        assertThat(body.get("projectionStatus")).isEqualTo("building");
        assertThat(body.containsKey("caseList")).isFalse();
    }

    @Test
    void terminalFailureShouldReturnSafe503WithoutEtag() {
        AutomationUiSceneQueryService queryService = mock(AutomationUiSceneQueryService.class);
        when(queryService.definition(8L))
            .thenThrow(new AutomationUiDefinitionProjectionUnavailableException(false, "projection-error-1", 8L, 4L, "failed"));
        AutomationUiSceneController controller = controller(queryService);

        ResponseEntity<?> response = controller.definition(8L, mock(HttpServletRequest.class));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getETag()).isNull();
        Map<?, ?> body = (Map<?, ?>)response.getBody();
        assertThat(body.get("code")).isEqualTo("DEFINITION_PROJECTION_FAILED");
        assertThat(body.get("errorId")).isEqualTo("projection-error-1");
        assertThat(body.containsKey("caseList")).isFalse();
    }

    @Test
    void readyDefinitionShouldCompareEtagOnlyAfterServiceAuthorization() {
        AutomationUiSceneQueryService queryService = mock(AutomationUiSceneQueryService.class);
        AutomationUiSceneDefinitionResp.Projected body = new AutomationUiSceneDefinitionResp.Projected();
        when(queryService.definition(8L))
            .thenReturn(new AutomationUiSceneQueryService.DefinitionView(body, "W/\"etag\""));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn("W/\"etag\"");

        ResponseEntity<?> response = controller(queryService).definition(8L, request);

        assertThat(response.getStatusCode().value()).isEqualTo(304);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"etag\"");
        assertThat(response.getBody()).isNull();
    }

    private AutomationUiSceneController controller(AutomationUiSceneQueryService queryService) {
        return new AutomationUiSceneController(null, null, null, null, queryService);
    }
}
