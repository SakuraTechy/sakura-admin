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

import java.util.ArrayList;
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
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
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
        service = new AutomationPlaywrightCaseServiceImpl(sceneMapper, stepExtractor, environmentMapper, projectConfigMapper, new AutomationPlaybackUrlRewriter());
        lenient().when(stepExtractor.extract(any(StepDO.class), anyInt()))
            .thenReturn(Map.of("start_url", "https://172.19.5.45/login"));
    }

    @Test
    void shouldRejectEnvironmentFromAnotherProject() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(2L);
        when(environmentMapper.selectById(47L)).thenReturn(environment);

        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L)).hasMessageContaining("产品环境与场景所属项目不一致")
            .hasMessageContaining("projectEnvironmentId=47");
    }

    @Test
    void shouldRejectEnvironmentWithoutFrontendAddress() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setServerConfig(List.of());
        when(environmentMapper.selectById(47L)).thenReturn(environment);

        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L)).hasMessageContaining("未配置服务器信息")
            .hasMessageContaining("caseId=CASE_001");
    }

    @Test
    void shouldIncludeExecutionContextWhenEnvironmentAddressIsInvalid() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setLastDomain("https://");
        when(environmentMapper.selectById(47L)).thenReturn(environment);

        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L)).hasMessageContaining("产品环境前端地址无效")
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
        storedStep.setConfigList(List
            .of(config("playwright_step", rawPlaywrightStep), config("locator_meta", rawLocatorMeta)));
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateFourteenDigitBatchAndExecutionIds() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setName("测试环境");
        when(environmentMapper.selectById(47L)).thenReturn(environment);
        AutomationPlaywrightBatchCreateReq request = new AutomationPlaywrightBatchCreateReq();
        request.setSceneKey("100");
        request.setExecutionType("playwright-runner");
        request.setCaseIds(List.of("CASE_001"));
        request.setProjectEnvironmentId(47L);

        AutomationPlaywrightBatchResp response = service.createBatch(request);

        assertThat(response.getBatchId()).matches("\\d{14}");
        assertThat(response.getCases()).hasSize(1);
        assertThat(response.getCases().get(0).getExecutionId()).matches("\\d{14}").isNotEqualTo(response.getBatchId());
        Map<String, Object> storedBatch = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        Map<String, Object> storedCase = (Map<String, Object>)((List<?>)storedBatch.get("caseResults")).get(0);
        assertThat(storedBatch.get("batchId")).isEqualTo(response.getBatchId());
        assertThat(storedCase.get("execution_id")).isEqualTo(response.getCases().get(0).getExecutionId());
        verify(sceneMapper).updateById(storedScene);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMergeDetailedResultIntoBatchAndEnrichStepIdentityAndLocator() {
        AutomationUiSceneDO storedScene = scene(1L);
        StepDO sourceStep = storedScene.getCaseList().get(0).getStepList().get(0);
        sourceStep.setId("STEP_001");
        sourceStep.setName("点击登录按钮");
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenReturn(Map
            .of("id", "STEP_001", "step_index", 0, "action_type", "click", "description", "点击按钮", "target_selector", "#login", "target_xpath", "//*[@id='login']", "locator_meta", Map
                .of("candidates", List.of(Map.of("type", "css_id", "value", "#login")))));

        Map<String, Object> pendingCase = new LinkedHashMap<>();
        pendingCase.put("case_id", "CASE_001");
        pendingCase.put("case_name", "登录");
        pendingCase.put("execution_id", "RUN_001");
        pendingCase.put("status", "running");
        pendingCase.put("step_total", 1);
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "BATCH_001");
        batch.put("executionId", "BATCH_001");
        batch.put("executionType", "playwright-runner");
        batch.put("executeName", "实际用户");
        batch.put("startedAt", "2026-07-17 10:00:00");
        batch.put("caseResults", List.of(pendingCase));
        storedScene.setDebugRecord(List.of(batch));

        Map<String, Object> stepResult = new LinkedHashMap<>();
        stepResult.put("step_id", "STEP_001");
        stepResult.put("step_index", 0);
        stepResult.put("status", "passed");
        stepResult.put("duration_ms", 320);
        stepResult.put("locator_source", "meta:css_id");
        stepResult.put("locator_type", "css_id");
        stepResult.put("locator_value", "#login");
        stepResult.put("matched_count", 1);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("executor", "playwright-runner");
        raw.put("batch_id", "BATCH_001");
        raw.put("run_id", "RUN_001");
        raw.put("started_at", "2026-07-17T02:00:01Z");
        raw.put("finished_at", "2026-07-17T02:00:02Z");
        raw.put("steps", List.of(stepResult));
        raw.put("artifacts", Map.of("report_html", "/api/report/RUN_001"));
        AutomationPlaywrightResultReq request = new AutomationPlaywrightResultReq();
        request.setStatus("passed");
        request.setSuccess(true);
        request.setDurationMs(1000L);
        request.setRaw(raw);

        service.saveResult("100:CASE_001", request);

        assertThat(storedScene.getDebugRecord()).hasSize(1);
        Map<String, Object> storedBatch = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        Map<String, Object> storedCase = (Map<String, Object>)((List<?>)storedBatch.get("caseResults")).get(0);
        Map<String, Object> storedStep = (Map<String, Object>)((List<?>)storedCase.get("steps")).get(0);
        assertThat(storedCase.get("execution_id")).isEqualTo("RUN_001");
        assertThat(storedCase.get("duration_ms")).isEqualTo(320L);
        assertThat(storedCase.get("wall_clock_duration_ms")).isEqualTo(1000L);
        assertThat(storedCase.get("step_pass_rate")).isEqualTo("100%");
        assertThat(storedCase.get("artifact_urls")).isEqualTo(Map.of("report_html", "/api/report/RUN_001"));
        assertThat(storedStep.get("step_name")).isEqualTo("点击登录按钮");
        assertThat(storedStep.get("actual_locator_source")).isEqualTo("meta:css_id");
        assertThat(storedStep.get("actual_locator_type")).isEqualTo("css_id");
        assertThat(storedStep.get("actual_locator_value")).isEqualTo("#login");
        assertThat(storedBatch.get("caseCompleted")).isEqualTo(1);
        assertThat(storedBatch.get("casePass")).isEqualTo(1);
        assertThat(storedBatch.get("duration")).isEqualTo(320L);
        assertThat(storedBatch.get("stepPassRate")).isEqualTo("100%");
        assertThat(storedBatch.get("executeName")).isEqualTo("实际用户");
        verify(sceneMapper).updateById(storedScene);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCancelOnlyUnfinishedBatchCasesAndRecomputeSummary() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);

        Map<String, Object> passedCase = new LinkedHashMap<>();
        passedCase.put("case_id", "CASE_001");
        passedCase.put("status", "passed");
        Map<String, Object> runningCase = new LinkedHashMap<>();
        runningCase.put("case_id", "CASE_002");
        runningCase.put("status", "running");
        Map<String, Object> waitingCase = new LinkedHashMap<>();
        waitingCase.put("case_id", "CASE_003");
        waitingCase.put("status", "waiting");

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "BATCH_CANCEL");
        batch.put("startedAt", "2026-07-17 10:00:00");
        batch.put("caseResults", new ArrayList<>(List.of(passedCase, runningCase, waitingCase)));
        storedScene.setDebugRecord(new ArrayList<>(List.of(batch)));

        service.cancelBatch("100", "BATCH_CANCEL");

        List<Map<String, Object>> caseResults = (List<Map<String, Object>>)(List<?>)batch.get("caseResults");
        assertThat(caseResults).extracting(item -> item.get("status"))
            .containsExactly("passed", "cancelled", "cancelled");
        assertThat(batch.get("cancelRequested")).isEqualTo(true);
        assertThat(batch.get("caseCompleted")).isEqualTo(3);
        assertThat(batch.get("casePass")).isEqualTo(1);
        assertThat(batch.get("caseCancelled")).isEqualTo(2);
        assertThat(batch.get("executeStatus")).isEqualTo("cancelled");
        assertThat(batch.get("executeResult")).isEqualTo("cancelled");
        verify(sceneMapper).updateById(storedScene);
    }

    private AutomationUiSceneDO scene(Long projectId) {
        StepDO step = new StepDO();
        step.setId("STEP_001");
        step.setName("打开登录页");
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
