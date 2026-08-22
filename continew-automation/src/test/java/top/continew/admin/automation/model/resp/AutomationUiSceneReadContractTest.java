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

package top.continew.admin.automation.model.resp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;

class AutomationUiSceneReadContractTest {

    @Test
    void summaryShouldContainOnlyWhitelistedFields() {
        Set<String> fields = Arrays.stream(AutomationUiSceneSummaryResp.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertThat(fields)
            .containsExactlyInAnyOrder("sceneDbId", "sceneKey", "name", "description", "projectDbId", "projectName", "versionDbId", "versionName", "moduleDbId", "modulePath", "level", "status", "tags", "definitionVersion", "globalExecutionRevision", "latestExecution", "createUserString", "updateUserString", "createTime", "updateTime");
        assertThat(fields).doesNotContain("caseList", "debugRecord", "testRecord", "steps", "diagnostics");
    }

    @Test
    void globalRevisionShouldContainOnlyRefreshFields() {
        Set<String> fields = Arrays.stream(AutomationUiSceneGlobalRevisionResp.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("sceneDbId", "globalExecutionRevision", "updateTime");
    }

    @Test
    void definitionBranchesShouldBeStrictlyDiscriminated() throws Exception {
        AutomationUiSceneDefinitionResp.Inline inline = new AutomationUiSceneDefinitionResp.Inline();
        inline.setCaseList(List.of(new CaseDO()));
        AutomationUiSceneDefinitionResp.Projected projected = new AutomationUiSceneDefinitionResp.Projected();
        projected.setProjectionId(7L);
        projected.setCaseCount(2);
        projected.setStepCount(5);

        ObjectMapper objectMapper = new ObjectMapper();
        String inlineJson = objectMapper.writeValueAsString(inline);
        String projectedJson = objectMapper.writeValueAsString(projected);

        assertThat(inlineJson).contains("\"mode\":\"inline\"")
            .contains("\"caseList\"")
            .doesNotContain("projectionId", "debugRecord", "testRecord");
        assertThat(projectedJson).contains("\"mode\":\"projected\"")
            .contains("\"projectionId\":7")
            .doesNotContain("caseList", "debugRecord", "testRecord");
        assertThat(Arrays.stream(AutomationUiSceneDefinitionResp.Projected.class.getDeclaredFields())
            .map(Field::getName)).doesNotContain("caseList");
    }
}
