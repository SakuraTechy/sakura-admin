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

package top.continew.admin.automation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.automation.converter.AutomationOperationStepReverseAdapter;
import top.continew.admin.automation.converter.AutomationRecordingActionResolver;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.ui.AutomationUiStepConfigEditReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepEditReq;
import top.continew.admin.automation.model.resp.ui.AutomationUiStepDetailResp;
import top.continew.admin.automation.service.AutomationUiCaseTreeService;

@ExtendWith(MockitoExtension.class)
class AutomationUiCaseDetailServiceImplTest {

    @Mock
    private AutomationUiSceneMapper sceneMapper;
    @Mock
    private AutomationUiCaseTreeService caseTreeService;

    private AutomationUiCaseDetailServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationOperationCatalogServiceImpl catalogService = new AutomationOperationCatalogServiceImpl(objectMapper);
        catalogService.initialize();
        AutomationOperationStepReverseAdapter reverseAdapter = new AutomationOperationStepReverseAdapter(objectMapper, catalogService);
        service = new AutomationUiCaseDetailServiceImpl(sceneMapper, caseTreeService, new AutomationRecordingActionResolver(reverseAdapter), catalogService, objectMapper);
    }

    @Test
    void shouldUseCatalogLabelsForRecordedInputStep() {
        StepDO step = recordedInputStep();
        when(sceneMapper.selectById(1L)).thenReturn(scene(step));

        AutomationUiStepDetailResp detail = service.getStepDetail(1L, "CASE_001", "STEP_001");

        assertThat(detail.getOperationType()).isEqualTo("输入操作");
        assertThat(detail.getOperationName()).isEqualTo("输入文本");
        assertThat(detail.getMethodCode()).isEqualTo("input.text");
        assertThat(detail.getMethodConfig()).containsKey("target_ref");
        assertThat(detail.getConfigList()).allSatisfy(config -> assertThat(config.isReadOnly()).isFalse());
    }

    @Test
    void shouldForwardFullKeyValueConfigurationWhenEditingStep() {
        StepDO step = recordedInputStep();
        when(sceneMapper.selectById(1L)).thenReturn(scene(step));
        AutomationUiStepEditReq request = new AutomationUiStepEditReq();
        request.setPid("CASE_001");
        request.setId("STEP_001");
        request.setName("修改后的输入步骤");
        request.setOperationType("输入操作");
        request.setOperationName("输入文本");
        request.setOperationValue("web-input");
        request.setMethodCode("input.text");
        request.setMethodVersion(1);
        request.setMethodConfig(Map.of("value", "admin"));
        request.setConfigList(List.of(config("custom_key", "custom_value")));
        request.setExpectedDefinitionVersion(3L);

        service.updateStep(1L, request);

        ArgumentCaptor<StepDO> command = ArgumentCaptor.forClass(StepDO.class);
        verify(caseTreeService).updateStep(org.mockito.ArgumentMatchers.eq(1L), command.capture());
        assertThat(command.getValue().getOperationType()).isEqualTo("输入操作");
        assertThat(command.getValue().getConfigList()).extracting(StepDO.Config::getParamsName)
            .contains("custom_key", "method_code", "method_version", "method_config");
        assertThat(configValue(command.getValue(), "custom_key")).isEqualTo("custom_value");
    }

    private AutomationUiSceneDO scene(StepDO step) {
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE_001");
        caseDO.setStepList(new ArrayList<>(List.of(step)));
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setCaseList(new ArrayList<>(List.of(caseDO)));
        return scene;
    }

    private StepDO recordedInputStep() {
        StepDO step = new StepDO();
        step.setPid("CASE_001");
        step.setId("STEP_001");
        step.setOperationType("浏览器操作");
        step.setOperationName("输入");
        step.setOperationValue("pw-input");
        step.setConfigList(new ArrayList<>(List
            .of(stepConfig("source", "sakura-playwright"), stepConfig("playwright_step", "{\"action_type\":\"input\",\"target_selector\":\"#username\",\"value\":\"admin\"}"))));
        return step;
    }

    private AutomationUiStepConfigEditReq config(String name, String value) {
        AutomationUiStepConfigEditReq config = new AutomationUiStepConfigEditReq();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

    private StepDO.Config stepConfig(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

    private String configValue(StepDO step, String name) {
        return step.getConfigList()
            .stream()
            .filter(config -> name.equals(config.getParamsName()))
            .map(StepDO.Config::getParamsValue)
            .findFirst()
            .orElse(null);
    }
}
