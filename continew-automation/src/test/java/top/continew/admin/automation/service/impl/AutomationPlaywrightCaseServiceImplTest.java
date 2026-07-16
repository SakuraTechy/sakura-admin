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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.automation.converter.AutomationPlaybackUrlRewriter;
import top.continew.admin.automation.converter.AutomationPlaywrightStepExtractor;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;

@ExtendWith(MockitoExtension.class)
class AutomationPlaywrightCaseServiceImplTest {

    @Mock
    private AutomationUiSceneMapper sceneMapper;

    @Mock
    private AutomationPlaywrightStepExtractor stepExtractor;

    @Mock
    private ProjectEnvironmentConfigMapper environmentMapper;

    @Mock
    private ProjectConfigMapper projectConfigMapper;

    private AutomationPlaywrightCaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationPlaywrightCaseServiceImpl(sceneMapper,
            stepExtractor,
            environmentMapper,
            projectConfigMapper,
            new AutomationPlaybackUrlRewriter());
        lenient().when(stepExtractor.extract(any(StepDO.class), anyInt()))
            .thenReturn(Map.of("start_url", "https://172.19.5.45/login"));
    }

    @Test
    void shouldRejectEnvironmentFromAnotherProject() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(2L);
        when(environmentMapper.selectById(47L)).thenReturn(environment);

        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L))
            .hasMessageContaining("产品环境与场景所属项目不一致")
            .hasMessageContaining("projectEnvironmentId=47");
    }

    @Test
    void shouldRejectEnvironmentWithoutFrontendAddress() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setServerConfig(List.of());
        when(environmentMapper.selectById(47L)).thenReturn(environment);

        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L))
            .hasMessageContaining("未配置服务器信息")
            .hasMessageContaining("caseId=CASE_001");
    }

    @Test
    void shouldIncludeExecutionContextWhenEnvironmentAddressIsInvalid() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setLastDomain("https://");
        when(environmentMapper.selectById(47L)).thenReturn(environment);

        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L))
            .hasMessageContaining("产品环境前端地址无效")
            .hasMessageContaining("sceneId=SCENE_001")
            .hasMessageContaining("caseId=CASE_001")
            .hasMessageContaining("projectEnvironmentId=47");
    }

    @Test
    void shouldRewriteResponseCopyWithoutChangingStoredPlaywrightStepOrLocatorMeta() {
        AutomationUiSceneDO storedScene = scene(1L);
        storedScene.setVersionName("V6.5B06D011");
        StepDO storedStep = storedScene.getCaseList().get(0).getStepList().get(0);
        String rawPlaywrightStep = "{\"action_type\":\"navigate\",\"value\":\"https://172.19.5.45/login\"}";
        String rawLocatorMeta = "{\"version\":1,\"candidates\":[{\"type\":\"css_id\",\"value\":\"#login\"}]}";
        storedStep.setConfigList(List.of(config("playwright_step", rawPlaywrightStep), config("locator_meta", rawLocatorMeta)));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);

        Map<String, Object> extractedStep = new LinkedHashMap<>();
        extractedStep.put("action_type", "navigate");
        extractedStep.put("start_url", "https://172.19.5.45/login?x=1#top");
        extractedStep.put("value", "https://172.19.5.45/home");
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenReturn(extractedStep);

        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setName("集成环境 .47");
        environment.setLastDomain("https://172.19.5.47");
        when(environmentMapper.selectById(47L)).thenReturn(environment);
        ProjectConfigDO project = new ProjectConfigDO();
        project.setAbbreviate("AAS_P");
        when(projectConfigMapper.selectById(1L)).thenReturn(project);

        AutomationPlaywrightCaseResp response = service.getCase("100:CASE_001", 47L);

        assertThat(response.getProject_short_name()).isEqualTo("AAS_P");
        assertThat(response.getVersion_name()).isEqualTo("V6.5B06D011");
        assertThat(response.getSceneId()).isEqualTo("SCENE_001");
        assertThat(response.getCaseId()).isEqualTo("CASE_001");
        assertThat(response.getStart_url()).isEqualTo("https://172.19.5.47/login?x=1#top");
        assertThat(response.getSteps().get(0).get("value")).isEqualTo("https://172.19.5.47/home");
        assertThat(storedStep.getConfigList().get(0).getParamsValue()).isEqualTo(rawPlaywrightStep);
        assertThat(storedStep.getConfigList().get(1).getParamsValue()).isEqualTo(rawLocatorMeta);
        verify(sceneMapper, never()).updateById(any(AutomationUiSceneDO.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveBusinessSceneIdCaseKey() {
        AutomationUiSceneDO storedScene = scene(1L);
        storedScene.setSceneId("AAS_P_SMOKE_006");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(storedScene);

        AutomationPlaywrightCaseResp response = service.getCase("AAS_P_SMOKE_006:CASE_001");

        assertThat(response.getId()).isEqualTo("AAS_P_SMOKE_006:CASE_001");
        assertThat(response.getSceneId()).isEqualTo("AAS_P_SMOKE_006");
        assertThat(response.getCaseId()).isEqualTo("CASE_001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeExecutionTimesBeforePersistingDebugRecord() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("executor", "playwright-runner");
        raw.put("run_id", "AAS_P_SMOKE_006_SCENE_CASE_001-20260715-171534");
        raw.put("started_at", "2026-07-15T09:15:34.971Z");
        raw.put("finished_at", "2026-07-15T09:15:36.123Z");
        raw.put("detail", Map.of("console_events", List.of(Map.of("timestamp", "2026-07-15T09:15:35.456Z"))));
        raw.put("response_snapshot", Map.of("captured_at", "2026-07-15T09:15:35.789Z"));
        AutomationPlaywrightResultReq request = new AutomationPlaywrightResultReq();
        request.setStatus("passed");
        request.setSuccess(true);
        request.setDurationMs(1152L);
        request.setRaw(raw);

        service.saveResult("100:CASE_001", request);

        Map<String, Object> storedRecord = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        Map<String, Object> storedResult = (Map<String, Object>)storedRecord.get("playwrightResult");
        assertThat(storedRecord.get("startedAt")).isEqualTo("2026-07-15 17:15:34");
        assertThat(storedRecord.get("finishedAt")).isEqualTo("2026-07-15 17:15:36");
        assertThat(storedResult.get("started_at")).isEqualTo("2026-07-15 17:15:34");
        assertThat(storedResult.get("finished_at")).isEqualTo("2026-07-15 17:15:36");
        Map<String, Object> storedDetail = (Map<String, Object>)storedResult.get("detail");
        List<Map<String, Object>> consoleEvents = (List<Map<String, Object>>)storedDetail.get("console_events");
        assertThat(consoleEvents.get(0).get("timestamp")).isEqualTo("2026-07-15 17:15:35");
        Map<String, Object> responseSnapshot = (Map<String, Object>)storedResult.get("response_snapshot");
        assertThat(responseSnapshot.get("captured_at")).isEqualTo("2026-07-15 17:15:35");
        verify(sceneMapper).updateById(storedScene);
    }

    private AutomationUiSceneDO scene(Long projectId) {
        StepDO step = new StepDO();
        step.setOrder(1);
        CaseDO caseDO = new CaseDO();
        caseDO.setId("CASE_001");
        caseDO.setName("登录");
        caseDO.setStepList(List.of(step));
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(100L);
        scene.setSceneId("SCENE_001");
        scene.setProjectId(projectId);
        scene.setCaseList(List.of(caseDO));
        return scene;
    }

    private ProjectEnvironmentConfigDO environment(Long projectId) {
        ProjectEnvironmentConfigDO environment = new ProjectEnvironmentConfigDO();
        environment.setId(47L);
        environment.setProjectId(projectId);
        environment.setStatus(DisEnableStatusEnum.ENABLE);
        return environment;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
