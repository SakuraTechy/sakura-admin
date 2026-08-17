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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.json.jackson.autoconfigure.JacksonAutoConfiguration;

class AutomationUiDefinitionSnapshotMapperTest {

    @Test
    void shouldPreserveCanonicalFieldsAndRemoveScreenshotBody() {
        StepDO step = new StepDO();
        step.setId("STEP_001");
        step.setConfigList(new ArrayList<>(List
            .of(config("playwright_step", "{\"action_type\":\"click\"}"), config("locator_meta", "{\"candidates\":[]}"), config("screenshot", "data:image/png;base64,AAAA"), config("screenshot_ref", "file:10001"))));
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE_001");
        caseDO.setStepList(List.of(step));

        AutomationUiDefinitionSnapshotMapper.Snapshot snapshot = AutomationUiDefinitionSnapshotMapper.map(List
            .of(caseDO));

        assertThat(snapshot.definitionJson()).contains("playwright_step", "locator_meta", "file:10001")
            .doesNotContain("data:image", "AAAA");
        assertThat(snapshot.contentHash()).hasSize(64);
    }

    @Test
    void shouldProduceStableDigestForEquivalentDefinition() {
        CaseDO first = new CaseDO();
        first.setId("CASE_001");
        first.setName("登录");
        CaseDO second = new CaseDO();
        second.setName("登录");
        second.setId("CASE_001");

        assertThat(AutomationUiDefinitionSnapshotMapper.map(List.of(first)).contentHash())
            .isEqualTo(AutomationUiDefinitionSnapshotMapper.map(List.of(second)).contentHash());
    }

    @Test
    void shouldExcludeCommandAndDragFieldsFromDefinitionRevision() {
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE_001");
        caseDO.setExpectedDefinitionVersion(99L);
        caseDO.setCopyId("COPY_001");
        caseDO.setStepMsg("页面临时状态");
        caseDO.setDropPosition(1);
        caseDO.setExecutionConfig(new top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO());

        String json = AutomationUiDefinitionSnapshotMapper.map(List.of(caseDO)).definitionJson();

        assertThat(json)
            .doesNotContain("expectedDefinitionVersion", "expected_definition_version", "copyId", "copy_id", "stepMsg", "step_msg", "dropPosition", "drop_position")
            .contains("executionConfig");
    }

    @Test
    void shouldRestoreDisabledStatusWithApplicationBaseEnumDeserializer() throws Exception {
        StepDO disabledStep = new StepDO();
        disabledStep.setId("STEP_DISABLED");
        disabledStep.setStatus(StatusTypeEnum.DISABLE);
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE_001");
        caseDO.setStepList(List.of(disabledStep));
        AutomationUiDefinitionSnapshotMapper.Snapshot snapshot = AutomationUiDefinitionSnapshotMapper.map(List
            .of(caseDO));

        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonAutoConfiguration().jackson2ObjectMapperBuilderCustomizer().customize(builder);
        ObjectMapper applicationMapper = builder.build();
        List<CaseDO> restored = AutomationUiDefinitionSnapshotMapper.readCases(applicationMapper, snapshot
            .definitionJson());

        assertThat(snapshot.definitionJson()).contains("\"status\":\"DISABLE\"");
        assertThat(restored.get(0).getStepList().get(0).getStatus()).isEqualTo(StatusTypeEnum.DISABLE);
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
