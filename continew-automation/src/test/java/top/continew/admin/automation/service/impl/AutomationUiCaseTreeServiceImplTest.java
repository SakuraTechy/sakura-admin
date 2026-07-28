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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.enums.AutomationUiTreeMovePosition;
import top.continew.admin.automation.model.enums.AutomationUiTreeNodeType;
import top.continew.admin.automation.model.req.AutomationUiTreeMoveReq;
import top.continew.admin.automation.model.req.AutomationUiTreeNodeRefReq;
import top.continew.admin.automation.model.resp.AutomationUiTreeMutationResp;
import top.continew.starter.core.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AutomationUiCaseTreeServiceImplTest {

    @Mock
    private AutomationUiSceneMapper sceneMapper;
    @Mock
    private AutomationPlaywrightJobMapper playwrightJobMapper;

    private AutomationUiCaseTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationUiCaseTreeServiceImpl(sceneMapper, playwrightJobMapper);
    }

    @Test
    void shouldPersistAdjacentCaseMoveAfter() {
        AutomationUiSceneDO scene = scene(caseDO("CASE_001", 1), caseDO("CASE_002", 2));
        prepareMutation(scene);

        AutomationUiTreeMutationResp response = service.move(scene
            .getId(), move(caseRef("CASE_001"), caseRef("CASE_002"), AutomationUiTreeMovePosition.AFTER));

        assertThat(response.isChanged()).isTrue();
        ArgumentCaptor<List<CaseDO>> cases = caseListCaptor();
        verify(sceneMapper).updateDefinition(anyLong(), anyLong(), cases.capture(), anyInt(), anyInt());
        assertThat(cases.getValue()).extracting(CaseDO::getId).containsExactly("CASE_002", "CASE_001");
    }

    @Test
    void shouldNotAdvanceVersionForUnchangedStepPosition() {
        CaseDO parent = caseDO("CASE_001", 1);
        parent.setStepList(new ArrayList<>(List.of(step("STEP_001", parent.getId(), 1), step("STEP_002", parent
            .getId(), 2))));
        AutomationUiSceneDO scene = scene(parent);
        prepareMutation(scene);

        AutomationUiTreeMutationResp response = service.move(scene.getId(), move(stepRef(parent
            .getId(), "STEP_001"), stepRef(parent.getId(), "STEP_002"), AutomationUiTreeMovePosition.BEFORE));

        assertThat(response.isChanged()).isFalse();
        verify(sceneMapper, never()).updateDefinition(anyLong(), anyLong(), anyList(), anyInt(), anyInt());
    }

    @Test
    void shouldPreserveMaskedRecordingFactsWhenEditingStep() {
        CaseDO parent = caseDO("CASE_001", 1);
        StepDO existing = step("STEP_001", parent.getId(), 1);
        existing.setOperationValue("real-password");
        existing.setConfigList(new ArrayList<>(List
            .of(config("value_masked", "1"), config("value", "real-password"), config("playwright_step", "{\"value\":\"real-password\"}"), config("locator_meta", "{\"role\":\"textbox\"}"), config("screenshot_url", "/files/1"))));
        parent.setStepList(new ArrayList<>(List.of(existing)));
        AutomationUiSceneDO scene = scene(parent);
        prepareMutation(scene);

        StepDO request = step(existing.getId(), parent.getId(), 1);
        request.setExpectedDefinitionVersion(0L);
        request.setOperationValue("******");
        request.setConfigList(new ArrayList<>(List
            .of(config("value_masked", "1"), config("value", "******"), config("playwright_step", "******"), config("locator_meta", "******"), config("custom", "updated"))));
        service.updateStep(scene.getId(), request);

        ArgumentCaptor<List<CaseDO>> cases = caseListCaptor();
        verify(sceneMapper).updateDefinition(anyLong(), anyLong(), cases.capture(), anyInt(), anyInt());
        StepDO saved = cases.getValue().get(0).getStepList().get(0);
        assertThat(saved.getOperationValue()).isEqualTo("real-password");
        assertThat(configValue(saved, "value")).isEqualTo("real-password");
        assertThat(configValue(saved, "playwright_step")).isEqualTo("{\"value\":\"real-password\"}");
        assertThat(configValue(saved, "locator_meta")).isEqualTo("{\"role\":\"textbox\"}");
        assertThat(configValue(saved, "screenshot_url")).isEqualTo("/files/1");
        assertThat(configValue(saved, "custom")).isEqualTo("updated");
    }

    @Test
    void shouldRejectMutationWhileJenkinsSceneIsRunning() {
        AutomationUiSceneDO scene = scene(caseDO("CASE_001", 1), caseDO("CASE_002", 2));
        scene.setExecuteStatus("11");
        when(sceneMapper.selectByIdForUpdate(scene.getId())).thenReturn(scene);
        when(sceneMapper.selectById(scene.getId())).thenReturn(scene);

        assertThatThrownBy(() -> service.move(scene
            .getId(), move(caseRef("CASE_001"), caseRef("CASE_002"), AutomationUiTreeMovePosition.AFTER)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("场景正在执行，暂不能修改用例树");
        verify(sceneMapper, never()).updateDefinition(anyLong(), anyLong(), anyList(), anyInt(), anyInt());
    }

    @Test
    void shouldAllocateAfterStoredHighWaterMark() {
        AutomationUiSceneDO scene = scene(caseDO("CASE_001", 1), caseDO("CASE_002", 2));
        prepareMutation(scene);
        when(sceneMapper.selectNodeIdSequence(scene.getId(), "CASE", "CASE_")).thenReturn(3L);

        CaseDO request = caseDO("CASE_", 3);
        request.setExpectedDefinitionVersion(0L);
        AutomationUiTreeMutationResp response = service.addCase(scene.getId(), request);

        assertThat(response.getSelectedNode().getCaseId()).isEqualTo("CASE_004");
    }

    @Test
    void shouldInitializeNullDefinitionWhenAddingFirstCase() {
        AutomationUiSceneDO scene = scene();
        scene.setCaseList(null);
        prepareMutation(scene);

        CaseDO request = caseDO("CASE_", 1);
        request.setExpectedDefinitionVersion(0L);
        AutomationUiTreeMutationResp response = service.addCase(scene.getId(), request);

        assertThat(response.getSelectedNode().getCaseId()).isEqualTo("CASE_001");
        ArgumentCaptor<List<CaseDO>> cases = caseListCaptor();
        verify(sceneMapper).updateDefinition(anyLong(), anyLong(), cases.capture(), anyInt(), anyInt());
        assertThat(cases.getValue()).extracting(CaseDO::getId).containsExactly("CASE_001");
    }

    @Test
    void shouldReturnAllocatedStepIdForLegacyAddStepResponse() {
        CaseDO parent = caseDO("CASE_001", 1);
        AutomationUiSceneDO scene = scene(parent);
        prepareMutation(scene);

        StepDO request = step("CASE_STEP_", parent.getId(), 1);
        request.setExpectedDefinitionVersion(0L);
        AutomationUiTreeMutationResp response = service.addStep(scene.getId(), request);

        assertThat(response.getSelectedNode().getStepId()).isEqualTo("CASE_STEP_001");
    }

    @Test
    void shouldReturnChineseMessageForDuplicateNodeId() {
        AutomationUiSceneDO scene = scene(caseDO("CASE_001", 1));
        prepareMutation(scene);

        CaseDO request = caseDO("CASE_001", 2);
        request.setExpectedDefinitionVersion(0L);

        assertThatThrownBy(() -> service.addCase(scene.getId(), request)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("节点 ID 已存在，请更换后重试");
    }

    @Test
    void shouldInsertCaseAtRequestedOrder() {
        AutomationUiSceneDO scene = scene(caseDO("CASE_001", 1), caseDO("CASE_002", 2));
        prepareMutation(scene);

        CaseDO request = caseDO("CASE_MIDDLE", 2);
        request.setExpectedDefinitionVersion(0L);
        service.addCase(scene.getId(), request);

        ArgumentCaptor<List<CaseDO>> cases = caseListCaptor();
        verify(sceneMapper).updateDefinition(anyLong(), anyLong(), cases.capture(), anyInt(), anyInt());
        assertThat(cases.getValue()).extracting(CaseDO::getId).containsExactly("CASE_001", "CASE_MIDDLE", "CASE_002");
        assertThat(cases.getValue()).extracting(CaseDO::getOrder).containsExactly(1, 2, 3);
    }

    @Test
    void shouldInsertStepAtRequestedOrder() {
        CaseDO parent = caseDO("CASE_001", 1);
        parent.setStepList(new ArrayList<>(List.of(step("STEP_001", parent.getId(), 1), step("STEP_002", parent
            .getId(), 2))));
        AutomationUiSceneDO scene = scene(parent);
        prepareMutation(scene);

        StepDO request = step("STEP_MIDDLE", parent.getId(), 2);
        request.setExpectedDefinitionVersion(0L);
        service.addStep(scene.getId(), request);

        ArgumentCaptor<List<CaseDO>> cases = caseListCaptor();
        verify(sceneMapper).updateDefinition(anyLong(), anyLong(), cases.capture(), anyInt(), anyInt());
        assertThat(cases.getValue().get(0).getStepList()).extracting(StepDO::getId)
            .containsExactly("STEP_001", "STEP_MIDDLE", "STEP_002");
        assertThat(cases.getValue().get(0).getStepList()).extracting(StepDO::getOrder).containsExactly(1, 2, 3);
    }

    @Test
    void shouldRejectOutOfRangeInsertOrder() {
        AutomationUiSceneDO scene = scene(caseDO("CASE_001", 1));
        prepareMutation(scene);

        CaseDO request = caseDO("CASE_INVALID_ORDER", 3);
        request.setExpectedDefinitionVersion(0L);

        assertThatThrownBy(() -> service.addCase(scene.getId(), request)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("用例序号必须在 1 到 2 之间");
        verify(sceneMapper, never()).updateDefinition(anyLong(), anyLong(), anyList(), anyInt(), anyInt());
    }

    private void prepareMutation(AutomationUiSceneDO scene) {
        when(sceneMapper.selectByIdForUpdate(scene.getId())).thenReturn(scene);
        when(sceneMapper.selectById(scene.getId())).thenReturn(scene);
        when(playwrightJobMapper.countActiveBySceneKeys(String.valueOf(scene.getId()), scene.getSceneId()))
            .thenReturn(0L);
        lenient().when(sceneMapper.updateDefinition(anyLong(), anyLong(), anyList(), anyInt(), anyInt())).thenReturn(1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<CaseDO>> caseListCaptor() {
        return (ArgumentCaptor)ArgumentCaptor.forClass(List.class);
    }

    private AutomationUiSceneDO scene(CaseDO... cases) {
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(1L);
        scene.setSceneId("SCENE_001");
        scene.setDefinitionVersion(0L);
        scene.setExecuteStatus("10");
        scene.setCaseList(new ArrayList<>(List.of(cases)));
        return scene;
    }

    private CaseDO caseDO(String id, int order) {
        CaseDO caseDO = new CaseDO();
        caseDO.setId(id);
        caseDO.setName(id);
        caseDO.setOrder(order);
        caseDO.setStepList(new ArrayList<>());
        return caseDO;
    }

    private StepDO step(String id, String caseId, int order) {
        StepDO step = new StepDO();
        step.setId(id);
        step.setPid(caseId);
        step.setName(id);
        step.setOrder(order);
        step.setConfigList(new ArrayList<>());
        return step;
    }

    private StepDO.Config config(String name, String value) {
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

    private AutomationUiTreeMoveReq move(AutomationUiTreeNodeRefReq source,
                                         AutomationUiTreeNodeRefReq target,
                                         AutomationUiTreeMovePosition position) {
        AutomationUiTreeMoveReq request = new AutomationUiTreeMoveReq();
        request.setSource(source);
        request.setTarget(target);
        request.setPosition(position);
        request.setExpectedDefinitionVersion(0L);
        return request;
    }

    private AutomationUiTreeNodeRefReq caseRef(String caseId) {
        AutomationUiTreeNodeRefReq ref = new AutomationUiTreeNodeRefReq();
        ref.setType(AutomationUiTreeNodeType.CASE);
        ref.setCaseId(caseId);
        return ref;
    }

    private AutomationUiTreeNodeRefReq stepRef(String caseId, String stepId) {
        AutomationUiTreeNodeRefReq ref = new AutomationUiTreeNodeRefReq();
        ref.setType(AutomationUiTreeNodeType.STEP);
        ref.setCaseId(caseId);
        ref.setStepId(stepId);
        return ref;
    }
}
