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

package top.continew.admin.test.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestPlanDO;

@ExtendWith(MockitoExtension.class)
class TestPlanServiceImplTest {

    @Mock
    private AutomationUiSceneMapper sceneMapper;

    @Mock
    private AutomationUiSceneService sceneService;

    @Mock
    private AutomationUiExecutionRecordService executionRecordService;

    @Mock
    private TestReportMapper reportMapper;

    @Mock
    private TestTimedTaskMapper timedTaskMapper;

    @Mock
    private TestPlanExecutionDispatchService dispatchService;

    private TestPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestPlanServiceImpl(sceneMapper, executionRecordService, sceneService, reportMapper, timedTaskMapper, dispatchService);
    }

    @Test
    void shouldResolveSelectedScenesInPlanOrder() {
        TestPlanDO plan = plan();
        when(sceneMapper.selectBatchIds(List.of(11L, 12L))).thenReturn(List.of(scene(12L), scene(11L)));

        List<Long> result = resolveExecutionSceneIds(plan, List.of(12L, 11L));

        assertThat(result).containsExactly(11L, 12L);
    }

    @Test
    void shouldRejectEmptyDuplicateAndUnrelatedSceneSelection() {
        TestPlanDO plan = plan();

        assertThatThrownBy(() -> resolveExecutionSceneIds(plan, List.of())).hasMessageContaining("执行场景不能为空");
        assertThatThrownBy(() -> resolveExecutionSceneIds(plan, List.of(11L, 11L))).hasMessageContaining("执行场景不能重复");
        assertThatThrownBy(() -> resolveExecutionSceneIds(plan, List.of(13L))).hasMessageContaining("执行场景不属于当前测试计划");
    }

    @Test
    void shouldRejectMissingAssociatedScene() {
        TestPlanDO plan = plan();
        when(sceneMapper.selectBatchIds(List.of(11L))).thenReturn(List.of());

        assertThatThrownBy(() -> resolveExecutionSceneIds(plan, List.of(11L))).hasMessageContaining("关联场景不存在");
    }

    @Test
    void shouldResetExecutionSummaryWhenStartingPlan() {
        TestPlanDO plan = plan();
        plan.setExecutedCount(2);
        plan.setPassedCount(2);
        plan.setTestProgress(new java.math.BigDecimal("100"));
        plan.setRunTime(1000L);

        ReflectionTestUtils.invokeMethod(service, "initializePlanExecution", plan);

        assertThat(plan.getStatus()).isEqualTo("RUNNING");
        assertThat(plan.getExecutedCount()).isZero();
        assertThat(plan.getPassedCount()).isZero();
        assertThat(plan.getTestProgress()).isZero();
        assertThat(plan.getRunTime()).isZero();
        assertThat(plan.getActualStartTime()).isNotNull();
        assertThat(plan.getActualEndTime()).isNull();
    }

    @SuppressWarnings("unchecked")
    private List<Long> resolveExecutionSceneIds(TestPlanDO plan, List<Long> requestedSceneIds) {
        return ReflectionTestUtils.invokeMethod(service, "resolveExecutionSceneIds", plan, requestedSceneIds);
    }

    private TestPlanDO plan() {
        TestPlanDO plan = new TestPlanDO();
        plan.setId(1L);
        plan.setUiTestScene(List.of(11L, 12L));
        return plan;
    }

    private AutomationUiSceneDO scene(Long id) {
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(id);
        return scene;
    }
}
