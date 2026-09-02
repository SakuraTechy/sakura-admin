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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.nullable;

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
import top.continew.admin.automation.model.entity.AutomationFileAssetDO;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.playwright.AutomationCdpPlaybackOptionsReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCaseStatusReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService;
import top.continew.admin.automation.service.AutomationEnvironmentResourceService;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService;
import top.continew.admin.automation.service.AutomationCaseExecutionClassifier;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.service.AutomationUiCaseReviewGateService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService.FrozenExecutionCase;
import top.continew.admin.automation.service.EffectiveExecutionConfigResolver;
import top.continew.admin.automation.support.AutomationCdpPlaybackPolicy;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.automation.mapper.AutomationFileAssetMapper;

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

    @Mock
    private AutomationPlaywrightSessionStateService sessionStateService;

    @Mock
    private AutomationUiExecutionRecordService executionRecordService;

    @Mock
    private AutomationUiCaseReviewGateService caseReviewGateService;

    @Mock
    private AutomationEnvironmentResourceService environmentResourceService;

    @Mock
    private AutomationCertificateWorkspaceService certificateWorkspaceService;

    @Mock
    private AutomationFileAssetMapper fileAssetMapper;

    private AutomationPlaywrightCaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationPlaywrightCaseServiceImpl(sceneMapper, stepExtractor, environmentMapper, projectConfigMapper, new AutomationPlaybackUrlRewriter(), List
            .of(), sessionStateService, executionRecordService, caseReviewGateService, new EffectiveExecutionConfigResolver(), new AutomationCaseExecutionClassifier(stepExtractor), new AutomationCdpPlaybackPolicy(true, "*"), environmentResourceService, certificateWorkspaceService, fileAssetMapper);
        lenient().when(stepExtractor.extract(any(StepDO.class), anyInt()))
            .thenReturn(Map.of("action_type", "navigate", "start_url", "https://172.19.5.45/login"));
        // 这些旧单测通过内存 mock 模拟规范化执行表，生产代码不会再写 scene JSON。
        lenient().doAnswer(invocation -> {
            AutomationUiSceneDO scene = invocation.getArgument(0);
            Map<String, Object> record = invocation.getArgument(1);
            boolean testRecord = record.get("testPlanId") != null;
            List<Object> records = testRecord ? scene.getTestRecord() : scene.getDebugRecord();
            if (records == null) {
                records = new ArrayList<>();
                if (testRecord) {
                    scene.setTestRecord(records);
                } else {
                    scene.setDebugRecord(records);
                }
            } else if (!(records instanceof ArrayList<?>)) {
                records = new ArrayList<>(records);
                if (testRecord) {
                    scene.setTestRecord(records);
                } else {
                    scene.setDebugRecord(records);
                }
            }
            String batchId = String.valueOf(record.get("batchId"));
            records.removeIf(item -> item instanceof Map<?, ?> map && batchId.equals(String.valueOf(map
                .get("batchId"))));
            records.add(0, record);
            sceneMapper.updateById(scene);
            return null;
        })
            .when(executionRecordService)
            .saveRecord(any(AutomationUiSceneDO.class), any(Map.class), nullable(String.class));
        lenient().when(executionRecordService.findBatch(any(Long.class), any(String.class))).thenAnswer(invocation -> {
            AutomationUiSceneDO scene = sceneMapper.selectById(100L);
            String batchId = invocation.getArgument(1);
            for (List<Object> records : java.util.Arrays.asList(scene.getDebugRecord(), scene.getTestRecord())) {
                if (records == null) {
                    continue;
                }
                for (Object item : records) {
                    if (item instanceof Map<?, ?> map && batchId.equals(String.valueOf(map.get("batchId")))) {
                        return (Map<String, Object>)item;
                    }
                }
            }
            return null;
        });
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
    void shouldReadCaseExecutionConfigBeforeLegacyStepConfig() {
        AutomationUiSceneDO storedScene = scene(1L);
        CaseExecutionConfigDO config = new CaseExecutionConfigDO();
        config.setStartUrl("https://case-config.example/login");
        config.setWindowSizeMode("custom");
        config.setViewportWidth(1280);
        config.setViewportHeight(720);
        config.setScreenshotMode("full");
        config.setPageErrorCheckEnabled(1);
        storedScene.getCaseList().get(0).setExecutionConfig(config);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenReturn(Map
            .of("start_url", "https://legacy-step.example/login", "window_size_mode", "maximized"));

        AutomationPlaywrightCaseResp response = service.getCase("100:CASE_001");

        assertThat(response.getStartUrl()).isEqualTo("https://case-config.example/login");
        assertThat(response.getWindowSizeMode()).isEqualTo("custom");
        assertThat(response.getViewportWidth()).isEqualTo(1280);
        assertThat(response.getViewportHeight()).isEqualTo(720);
        assertThat(response.getScreenshotMode()).isEqualTo("full");
        assertThat(response.getPageErrorCheckEnabled()).isEqualTo(1);
    }

    @Test
    void shouldOnlyReturnEnabledStepsWithDefinitionIndexes() {
        AutomationUiSceneDO storedScene = scene(1L);
        StepDO disabledStep = step("STEP_002", "禁用断言", 2, StatusTypeEnum.DISABLE);
        StepDO enabledStep = step("STEP_003", "提交", 3, StatusTypeEnum.ENABLE);
        storedScene.getCaseList()
            .get(0)
            .setStepList(List.of(storedScene.getCaseList().get(0).getStepList().get(0), disabledStep, enabledStep));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenAnswer(invocation -> {
            StepDO source = invocation.getArgument(0);
            int index = invocation.getArgument(1);
            return Map.of("id", source.getId(), "step_index", index, "action_type", "click");
        });

        AutomationPlaywrightCaseResp response = service.getCase("100:CASE_001");

        assertThat(response.getSteps()).extracting(item -> item.get("id")).containsExactly("STEP_001", "STEP_003");
        assertThat(response.getSteps()).extracting(item -> item.get("step_index")).containsExactly(0, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeDisabledStepsFromBatchTotals() {
        AutomationUiSceneDO storedScene = scene(1L);
        StepDO disabledStep = step("STEP_002", "禁用断言", 2, StatusTypeEnum.DISABLE);
        storedScene.getCaseList()
            .get(0)
            .setStepList(List.of(storedScene.getCaseList().get(0).getStepList().get(0), disabledStep));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));

        AutomationPlaywrightBatchResp response = service.createBatch(batchRequest());

        assertThat(response.getCases()).hasSize(1);
        assertThat(response.getCases().get(0).getStepTotal()).isEqualTo(1);
        Map<String, Object> storedBatch = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        Map<String, Object> storedCase = (Map<String, Object>)((List<?>)storedBatch.get("caseResults")).get(0);
        assertThat(storedCase).containsEntry("step_total", 1).containsEntry("step_skip", 0);
        assertThat(storedBatch).containsEntry("stepTotal", 1).containsEntry("stepSkip", 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFreezeManagedCdpSessionConfigAtBatchLevel() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        AutomationPlaywrightBatchCreateReq request = batchRequest();
        request.setExecutionType("extension-cdp");
        AutomationCdpPlaybackOptionsReq options = new AutomationCdpPlaybackOptionsReq();
        options.setBrowserSessionSource("managed-context");
        options.setSessionMode("reuse-browser");
        options.setWindowSizeMode("custom");
        options.setViewportWidth(1440);
        options.setViewportHeight(900);
        options.setPageErrorCheckEnabled(false);
        request.setCdpOptions(options);

        AutomationPlaywrightBatchResp response = service.createBatch(request);

        assertThat(response.getSessionConfig()).containsEntry("browserSessionSource", "managed-context")
            .containsEntry("sessionMode", "reuse-browser");
        assertThat(response.getCases().get(0).getEffectiveExecutionConfig())
            .containsEntry("browser_session_source", "managed-context")
            .containsEntry("session_mode", "reuse-browser")
            .containsEntry("ignore_https_errors", false)
            .containsEntry("window_size_mode", "custom")
            .containsEntry("viewport_width", 1440)
            .containsEntry("viewport_height", 900)
            .containsEntry("page_error_check_enabled", false);
        Map<String, Object> storedBatch = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        assertThat((Map<String, Object>)storedBatch.get("sessionConfig")).containsEntry("sessionMode", "reuse-browser");
    }

    @Test
    void shouldKeepOldCdpBatchInLegacyProfileMode() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        AutomationPlaywrightBatchCreateReq request = batchRequest();
        request.setExecutionType("extension-cdp");

        AutomationPlaywrightBatchResp response = service.createBatch(request);

        assertThat(response.getSessionConfig()).containsEntry("browserSessionSource", "current-profile")
            .containsEntry("sessionMode", "legacy-profile");
        assertThat(response.getCases().get(0).getEffectiveExecutionConfig())
            .containsEntry("browser_session_source", "current-profile")
            .containsEntry("session_mode", "legacy-profile")
            .containsEntry("ignore_https_errors", false);
    }

    @Test
    void shouldRejectMismatchedCdpSessionSourceAndMode() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        AutomationPlaywrightBatchCreateReq request = batchRequest();
        request.setExecutionType("extension-cdp");
        AutomationCdpPlaybackOptionsReq options = new AutomationCdpPlaybackOptionsReq();
        options.setBrowserSessionSource("current-profile");
        options.setSessionMode("reuse-auth");
        request.setCdpOptions(options);

        assertThatThrownBy(() -> service.createBatch(request)).hasMessageContaining("浏览器会话来源与用例会话模式不匹配");
    }

    @Test
    void shouldRejectManagedCdpSessionWhenCurrentUserIsNotInGrayWhitelist() {
        service = new AutomationPlaywrightCaseServiceImpl(sceneMapper, stepExtractor, environmentMapper, projectConfigMapper, new AutomationPlaybackUrlRewriter(), List
            .of(), sessionStateService, executionRecordService, caseReviewGateService, new EffectiveExecutionConfigResolver(), new AutomationCaseExecutionClassifier(stepExtractor), new AutomationCdpPlaybackPolicy(false, "test-user"), environmentResourceService, certificateWorkspaceService, fileAssetMapper);
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        AutomationPlaywrightBatchCreateReq request = batchRequest();
        request.setExecutionType("extension-cdp");
        AutomationCdpPlaybackOptionsReq options = new AutomationCdpPlaybackOptionsReq();
        options.setBrowserSessionSource("managed-context");
        options.setSessionMode("isolated");
        request.setCdpOptions(options);

        assertThatThrownBy(() -> service.createBatch(request)).hasMessageContaining("CDP_MANAGED_CONTEXT_NOT_ALLOWED")
            .hasMessageContaining("灰度开关未开启");
    }

    @Test
    void shouldReadBatchCaseAndEffectiveConfigFromBoundRevision() {
        AutomationUiSceneDO currentScene = scene(1L);
        currentScene.setCaseList(List.of());
        when(sceneMapper.selectById(100L)).thenReturn(currentScene);
        when(executionRecordService.findBatch(100L, "BATCH_FROZEN")).thenReturn(Map
            .of("batchId", "BATCH_FROZEN", "caseResults", List.of(Map.of("case_id", "CASE_001"))));
        when(executionRecordService.matchesExecutionCapability(100L, "BATCH_FROZEN", "capability")).thenReturn(true);

        CaseDO frozenCase = scene(1L).getCaseList().get(0);
        frozenCase.setName("冻结用例");
        CaseExecutionConfigDO frozenConfig = new CaseExecutionConfigDO();
        frozenConfig.setStartUrl("https://revision.example/original");
        frozenCase.setExecutionConfig(frozenConfig);
        Map<String, Object> effectiveConfig = Map
            .of("start_url", "https://revision.example/effective", "window_size_mode", "viewport", "viewport_width", 1440, "viewport_height", 900, "page_error_check_enabled", true);
        when(executionRecordService.findFrozenCase(100L, "BATCH_FROZEN", "CASE_001"))
            .thenReturn(new FrozenExecutionCase(frozenCase, 7001L, null, effectiveConfig));

        AutomationPlaywrightCaseResp response = service.getCase("100:CASE_001", null, "BATCH_FROZEN", "capability");

        assertThat(response.getName()).isEqualTo("冻结用例");
        assertThat(response.getDefinitionRevisionId()).isEqualTo(7001L);
        assertThat(response.getStartUrl()).isEqualTo("https://revision.example/effective");
        assertThat(response.getViewportWidth()).isEqualTo(1440);
        assertThat(response.getViewportHeight()).isEqualTo(900);
        assertThat(response.getPageErrorCheckEnabled()).isEqualTo(1);
        assertThat(response.getEffectiveExecutionConfig()).isEqualTo(effectiveConfig);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMaterializeEnvironmentCertificateForCdpWhenBatchUsesExecutorField() {
        AutomationUiSceneDO currentScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(currentScene);
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        Map<String, Object> batchRecord = Map
            .of("batchId", "BATCH_CDP", "executor", "extension-cdp", "caseResults", List.of(Map
                .of("case_id", "CASE_001")));
        when(executionRecordService.findBatch(100L, "BATCH_CDP")).thenReturn(batchRecord);
        when(executionRecordService.matchesExecutionCapability(100L, "BATCH_CDP", "capability")).thenReturn(true);
        CaseDO frozenCase = scene(1L).getCaseList().get(0);
        when(executionRecordService.findFrozenCase(100L, "BATCH_CDP", "CASE_001"))
            .thenReturn(new FrozenExecutionCase(frozenCase, 7002L, 47L, Map.of()));

        Map<String, Object> certificateRef = Map
            .of("scope", "project_environment", "kind", "certificate", "slot_id", "878671771996430365");
        Map<String, Object> certificateStep = new LinkedHashMap<>();
        certificateStep.put("id", "STEP_CERT");
        certificateStep.put("action_type", "certificate_upload");
        certificateStep.put("start_url", "https://172.19.5.45/login");
        certificateStep.put("certificate_ref", certificateRef);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenReturn(certificateStep);
        when(environmentResourceService
            .resolve(47L, 1L, AutomationEnvironmentResourceService.CERTIFICATE, certificateRef))
            .thenReturn(new AutomationEnvironmentResourceService.ResolvedResource(878671771996430365L, "audit-license", AutomationEnvironmentResourceService.CERTIFICATE, 91L, 1));
        AutomationFileAssetDO asset = new AutomationFileAssetDO();
        asset.setId(91L);
        asset.setOriginalName("172_19_5_45_audit.lic");
        asset.setSize(1024L);
        asset.setSha256("sha256");
        when(fileAssetMapper.selectById(91L)).thenReturn(asset);

        AutomationPlaywrightCaseResp response = service.getCase("100:CASE_001", 47L, "BATCH_CDP", "capability");

        Map<String, Object> executionReference = (Map<String, Object>)response.getSteps().get(0).get("certificate_ref");
        assertThat(executionReference).containsEntry("type", "admin_execution_file")
            .containsEntry("asset_id", 91L)
            .containsEntry("file_name", "172_19_5_45_audit.lic");
        assertThat(executionReference.get("download_path"))
            .isEqualTo("/automation/playwright/testcases/100/CASE_001/execution-files/STEP_CERT?projectEnvironmentId=47&batchId=BATCH_CDP");
    }

    @Test
    void shouldRejectEnvironmentWithoutFrontendAddress() {
        when(sceneMapper.selectById(100L)).thenReturn(scene(1L));
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setLastDomain(null);
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
        raw.put("artifacts", Map.of("execution_log", "/automation/playwright/artifacts/files/124"));
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
        assertThat(storedRecord).doesNotContainKeys("playwrightArtifacts", "artifactUrls", "artifactUploadErrors");
        Map<String, Object> storedCase = (Map<String, Object>)((List<?>)storedRecord.get("caseResults")).get(0);
        assertThat(storedCase.get("artifact_urls")).isEqualTo(Map
            .of("execution_log", "/automation/playwright/artifacts/files/124"));
        verify(sceneMapper).updateById(storedScene);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCorrelateStepResultsByIndexBeforeDuplicateStepId() {
        AutomationUiSceneDO storedScene = scene(1L);
        StepDO secondStep = new StepDO();
        secondStep.setId("STEP_002");
        secondStep.setName("提交");
        secondStep.setOrder(2);
        storedScene.getCaseList()
            .get(0)
            .setStepList(List.of(storedScene.getCaseList().get(0).getStepList().get(0), secondStep));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenAnswer(invocation -> {
            int index = invocation.getArgument(1);
            return Map
                .of("id", "DUPLICATE_STEP_ID", "step_index", index, "action_type", "click", "description", "源步骤-" + index);
        });

        Map<String, Object> firstResult = new LinkedHashMap<>();
        firstResult.put("step_index", 0);
        firstResult.put("step_id", "DUPLICATE_STEP_ID");
        firstResult.put("status", "passed");
        Map<String, Object> secondResult = new LinkedHashMap<>();
        secondResult.put("step_index", 1);
        secondResult.put("step_id", "DUPLICATE_STEP_ID__1");
        secondResult.put("status", "failed");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("steps", List.of(secondResult, firstResult));

        AutomationPlaywrightResultReq request = new AutomationPlaywrightResultReq();
        request.setStatus("failed");
        request.setSuccess(false);
        request.setDurationMs(100L);
        request.setRaw(raw);

        service.saveResult("100:CASE_001", request);

        Map<String, Object> record = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        List<Map<String, Object>> stepResults = (List<Map<String, Object>>)record.get("stepResults");
        assertThat(stepResults).extracting(item -> item.get("step_index")).containsExactly(0, 1);
        assertThat(stepResults).extracting(item -> item.get("status")).containsExactly("passed", "failed");
        assertThat(stepResults).extracting(item -> item.get("step_id"))
            .containsExactly("DUPLICATE_STEP_ID", "DUPLICATE_STEP_ID__1");
        assertThat(stepResults.get(1).get("source_step_id")).isEqualTo("DUPLICATE_STEP_ID");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkMissingStructuredStepResultAsFailedInsteadOfSkipped() {
        AutomationUiSceneDO storedScene = scene(1L);
        StepDO secondStep = new StepDO();
        secondStep.setId("STEP_002");
        secondStep.setName("提交");
        secondStep.setOrder(2);
        storedScene.getCaseList()
            .get(0)
            .setStepList(List.of(storedScene.getCaseList().get(0).getStepList().get(0), secondStep));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenAnswer(invocation -> {
            int index = invocation.getArgument(1);
            return Map.of("id", index == 0
                ? "STEP_001"
                : "STEP_002", "step_index", index, "action_type", "click", "description", index == 0 ? "打开" : "提交");
        });

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("steps", List.of(Map.of("step_index", 0, "step_id", "STEP_001", "status", "passed")));
        AutomationPlaywrightResultReq request = new AutomationPlaywrightResultReq();
        request.setStatus("failed");
        request.setSuccess(false);
        request.setDurationMs(100L);
        request.setRaw(raw);

        service.saveResult("100:CASE_001", request);

        Map<String, Object> record = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        List<Map<String, Object>> stepResults = (List<Map<String, Object>>)record.get("stepResults");
        assertThat(stepResults).extracting(item -> item.get("status")).containsExactly("passed", "failed");
        assertThat(stepResults.get(1)).containsEntry("result_incomplete", true)
            .containsEntry("error", "执行结果缺失：Runner 未回传该步骤结果");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistInfrastructurePreviewInStepDetails() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenReturn(Map
            .of("id", "STEP_001", "step_index", 0, "action_type", "server_command"));

        Map<String, Object> infrastructure = Map
            .of("schemaVersion", 2, "kind", "SERVER_COMMAND", "exitCode", 0, "durationMs", 18, "results", List
                .of(), "stdout", "command completed", "stderr", "", "truncated", false);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("steps", List.of(Map
            .of("step_index", 0, "step_id", "STEP_001", "action_type", "server_command", "status", "passed", "infrastructure_task_id", "INFRA_001", "details", Map
                .of("infrastructure", infrastructure))));
        AutomationPlaywrightResultReq request = new AutomationPlaywrightResultReq();
        request.setStatus("passed");
        request.setSuccess(true);
        request.setDurationMs(18L);
        request.setRaw(raw);

        service.saveResult("100:CASE_001", request);

        Map<String, Object> record = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        Map<String, Object> storedStep = (Map<String, Object>)((List<?>)record.get("stepResults")).get(0);
        assertThat(storedStep).containsEntry("infrastructure_task_id", "INFRA_001");
        assertThat((Map<String, Object>)storedStep.get("details")).containsEntry("infrastructure", infrastructure);
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
        assertThat(response.getCases().get(0).getStatus()).isEqualTo("queued");
        assertThat(response.getCases().get(0).getExecutionId()).matches("\\d{14}").isNotEqualTo(response.getBatchId());
        assertThat(response.getCases().get(0).getEffectiveExecutionConfig())
            .containsEntry("start_url", "https://172.19.5.47/login");
        assertThat((Map<String, String>)response.getCases().get(0).getEffectiveExecutionConfig().get("sources"))
            .containsEntry("start_url", "environment");
        Map<String, Object> storedBatch = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        Map<String, Object> storedCase = (Map<String, Object>)((List<?>)storedBatch.get("caseResults")).get(0);
        assertThat(storedBatch.get("batchId")).isEqualTo(response.getBatchId());
        assertThat(storedBatch.get("executeStatus")).isEqualTo("running");
        assertThat(storedBatch.get("executeResult")).isEqualTo("pending");
        assertThat(storedCase.get("execution_id")).isEqualTo(response.getCases().get(0).getExecutionId());
        assertThat(storedCase.get("executeStatus")).isEqualTo("queued");
        assertThat(storedCase.get("executeResult")).isEqualTo("not_executed");
        verify(sceneMapper).updateById(storedScene);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectBatchWhenOnlyCaseIsDisabled() {
        AutomationUiSceneDO storedScene = scene(1L);
        storedScene.getCaseList().get(0).setStatus(StatusTypeEnum.DISABLE);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        ProjectEnvironmentConfigDO environment = environment(1L);
        when(environmentMapper.selectById(47L)).thenReturn(environment);
        AutomationPlaywrightBatchCreateReq request = batchRequest();

        assertThatThrownBy(() -> service.createBatch(request)).hasMessageContaining("没有启用且包含启用步骤的用例");
        assertThat(storedScene.getDebugRecord()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeDisabledStepFromExecutionResultAndTotals() {
        AutomationUiSceneDO storedScene = scene(1L);
        StepDO disabledStep = step("STEP_002", "禁用断言", 2, StatusTypeEnum.DISABLE);
        storedScene.getCaseList()
            .get(0)
            .setStepList(List.of(storedScene.getCaseList().get(0).getStepList().get(0), disabledStep));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(stepExtractor.extract(any(StepDO.class), anyInt())).thenAnswer(invocation -> {
            StepDO source = invocation.getArgument(0);
            int index = invocation.getArgument(1);
            return Map.of("id", source.getId(), "step_index", index, "action_type", "click");
        });
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("steps", List.of(Map.of("step_index", 0, "step_id", "STEP_001", "status", "passed")));
        AutomationPlaywrightResultReq request = new AutomationPlaywrightResultReq();
        request.setStatus("passed");
        request.setSuccess(true);
        request.setDurationMs(100L);
        request.setRaw(raw);

        service.saveResult("100:CASE_001", request);

        Map<String, Object> record = (Map<String, Object>)storedScene.getDebugRecord().get(0);
        List<Map<String, Object>> stepResults = (List<Map<String, Object>>)record.get("stepResults");
        assertThat(stepResults).extracting(item -> item.get("status")).containsExactly("passed");
        assertThat(stepResults).extracting(item -> item.get("step_id")).containsExactly("STEP_001");
        assertThat(record).containsEntry("stepTotal", 1).containsEntry("stepSkip", 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStorePlanBatchAndDetailedResultInTestRecord() {
        AutomationUiSceneDO storedScene = scene(1L);
        storedScene.setTestPlanId(List.<Object>of("PLAN_001"));
        Map<String, Object> otherPlanRecord = new LinkedHashMap<>();
        otherPlanRecord.put("testPlanId", "PLAN_002");
        otherPlanRecord.put("executionId", "OTHER_PLAN_EXECUTION");
        storedScene.setTestRecord(new ArrayList<>(List.of(otherPlanRecord)));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        ProjectEnvironmentConfigDO environment = environment(1L);
        environment.setName("测试环境");
        when(environmentMapper.selectById(47L)).thenReturn(environment);
        AutomationPlaywrightBatchCreateReq request = new AutomationPlaywrightBatchCreateReq();
        request.setSceneKey("100");
        request.setExecutionType("playwright-runner");
        request.setCaseIds(List.of("CASE_001"));
        request.setProjectEnvironmentId(47L);
        request.setTestPlanId("PLAN_001");

        AutomationPlaywrightBatchResp response = service.createBatch(request);

        assertThat(storedScene.getDebugRecord()).isNull();
        assertThat(storedScene.getTestRecord()).hasSize(2);
        Map<String, Object> storedBatch = (Map<String, Object>)storedScene.getTestRecord().get(0);
        assertThat(storedBatch.get("testPlanId")).isEqualTo("PLAN_001");
        assertThat(storedScene.getTestRecord().get(1)).isSameAs(otherPlanRecord);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("executor", "playwright-runner");
        raw.put("batch_id", response.getBatchId());
        raw.put("run_id", response.getCases().get(0).getExecutionId());
        raw.put("started_at", "2026-07-19T02:00:01Z");
        raw.put("finished_at", "2026-07-19T02:00:02Z");
        raw.put("steps", List.of(Map.of("step_index", 0, "status", "passed", "duration_ms", 320)));
        AutomationPlaywrightResultReq result = new AutomationPlaywrightResultReq();
        result.setStatus("passed");
        result.setSuccess(true);
        result.setDurationMs(1000L);
        result.setRaw(raw);

        service.saveResult("100:CASE_001", result);

        assertThat(storedScene.getDebugRecord()).isNull();
        assertThat(storedScene.getTestRecord()).hasSize(2);
        assertThat(otherPlanRecord.get("executionId")).isEqualTo("OTHER_PLAN_EXECUTION");
        Map<String, Object> mergedCase = (Map<String, Object>)((List<?>)storedBatch.get("caseResults")).get(0);
        assertThat(mergedCase.get("status")).isEqualTo("passed");
        assertThat(mergedCase.get("result_detailed")).isEqualTo(true);
        assertThat(mergedCase.get("step_pass")).isEqualTo(1);
    }

    @Test
    void shouldRejectResultForUnknownBatchInsteadOfCreatingStandaloneRecord() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("batch_id", "BATCH_MISSING");
        raw.put("run_id", "RUN_MISSING");
        AutomationPlaywrightResultReq result = new AutomationPlaywrightResultReq();
        result.setStatus("passed");
        result.setSuccess(true);
        result.setRaw(raw);

        assertThatThrownBy(() -> service.saveResult("100:CASE_001", result)).hasMessageContaining("执行批次不存在")
            .hasMessageContaining("BATCH_MISSING");
        verify(executionRecordService, never())
            .saveRecord(any(AutomationUiSceneDO.class), any(Map.class), nullable(String.class));
    }

    @Test
    void shouldRequireMatchingCapabilityForRunnerBatchCallback() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        AutomationPlaywrightBatchCreateReq batchRequest = new AutomationPlaywrightBatchCreateReq();
        batchRequest.setSceneKey("100");
        batchRequest.setExecutionType("playwright-runner");
        batchRequest.setCaseIds(List.of("CASE_001"));
        batchRequest.setProjectEnvironmentId(47L);
        AutomationPlaywrightBatchResp batch = service.createBatch(batchRequest);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("batch_id", batch.getBatchId());
        raw.put("run_id", batch.getCases().get(0).getExecutionId());
        AutomationPlaywrightResultReq result = new AutomationPlaywrightResultReq();
        result.setStatus("passed");
        result.setSuccess(true);
        result.setRaw(raw);

        when(executionRecordService.matchesExecutionCapability(100L, batch.getBatchId(), batch
            .getExecutionCapability())).thenReturn(false);
        assertThatThrownBy(() -> service.saveResult("100:CASE_001", result, batch.getExecutionCapability()))
            .hasMessageContaining("EXECUTION_SCOPE_DENIED");

        when(executionRecordService.matchesExecutionCapability(100L, batch.getBatchId(), batch
            .getExecutionCapability())).thenReturn(true);
        service.saveResult("100:CASE_001", result, batch.getExecutionCapability());
        verify(executionRecordService, atLeastOnce())
            .saveRecord(any(AutomationUiSceneDO.class), any(Map.class), nullable(String.class));
    }

    @Test
    void shouldRequireMatchingCapabilityWhenRunnerReadsBatchCase() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        when(environmentMapper.selectById(47L)).thenReturn(environment(1L));
        AutomationPlaywrightBatchCreateReq batchRequest = new AutomationPlaywrightBatchCreateReq();
        batchRequest.setSceneKey("100");
        batchRequest.setExecutionType("playwright-runner");
        batchRequest.setCaseIds(List.of("CASE_001"));
        batchRequest.setProjectEnvironmentId(47L);
        AutomationPlaywrightBatchResp batch = service.createBatch(batchRequest);

        when(executionRecordService.matchesExecutionCapability(100L, batch.getBatchId(), batch
            .getExecutionCapability())).thenReturn(false);
        assertThatThrownBy(() -> service.getCase("100:CASE_001", 47L, batch.getBatchId(), batch
            .getExecutionCapability())).hasMessageContaining("EXECUTION_SCOPE_DENIED");
    }

    @Test
    void shouldRejectPlanBatchWhenSceneIsNotRelated() {
        AutomationUiSceneDO storedScene = scene(1L);
        storedScene.setTestPlanId(List.<Object>of("PLAN_002"));
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        AutomationPlaywrightBatchCreateReq request = new AutomationPlaywrightBatchCreateReq();
        request.setSceneKey("100");
        request.setExecutionType("extension-cdp");
        request.setCaseIds(List.of("CASE_001"));
        request.setProjectEnvironmentId(47L);
        request.setTestPlanId("PLAN_001");

        assertThatThrownBy(() -> service.createBatch(request)).hasMessageContaining("测试计划未关联当前场景")
            .hasMessageContaining("testPlanId=PLAN_001");
        verify(environmentMapper, never()).selectById(47L);
        verify(sceneMapper, never()).updateById(storedScene);
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
        pendingCase.put("job_id", "JOB_001");
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
        raw.put("executionCapability", "short-lived-secret");
        raw.put("steps", List.of(stepResult));
        raw.put("execution_logs", List.of(Map.of("timestamp", "2026-07-17T02:00:00Z", "message", "Runner 任务开始"), Map
            .of("timestamp", "2026-07-17T02:00:01.200Z", "message", "[runner] passed")));
        raw.put("screenshot_base64", "data:image/png;base64,AAAA");
        raw.put("diagnostics", Map
            .of("safe", "保留", "nested_screenshot_base64", "AAAA", "preview", "data:image/png;base64,BBBB"));
        raw.put("artifacts", Map.of("report_html", "/api/report/RUN_001", "trace", "D:\\runner\\RUN_001\\trace.zip"));
        raw.put("artifact_file_ids", Map.of("report_html", "10001"));
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
        assertThat(storedCase.get("job_id")).isEqualTo("JOB_001");
        assertThat(storedCase.get("duration_ms")).isEqualTo(1000L);
        assertThat(storedCase.get("step_duration_ms")).isEqualTo(320L);
        assertThat(storedCase.get("wall_clock_duration_ms")).isEqualTo(1000L);
        assertThat(storedCase.get("step_pass_rate")).isEqualTo("100%");
        assertThat(storedCase.get("artifact_urls")).isEqualTo(Map.of("report_html", "/api/report/RUN_001"));
        assertThat(storedCase.get("artifact_file_ids")).isEqualTo(Map.of("report_html", "10001"));
        Map<String, Object> storedRawResult = (Map<String, Object>)storedCase.get("playwright_result");
        assertThat(storedRawResult).doesNotContainKey("screenshot_base64");
        assertThat(storedRawResult).doesNotContainKey("executionCapability");
        assertThat(storedRawResult).containsEntry("diagnostics", Map.of("safe", "保留"));
        assertThat(storedRawResult)
            .doesNotContainKeys("steps", "artifacts", "artifact_file_ids", "artifact_upload_errors");
        assertThat(storedStep.get("step_name")).isEqualTo("点击登录按钮");
        assertThat(storedStep.get("actual_locator_source")).isEqualTo("meta:css_id");
        assertThat(storedStep.get("actual_locator_type")).isEqualTo("css_id");
        assertThat(storedStep.get("actual_locator_value")).isEqualTo("#login");
        assertThat(storedBatch.get("caseCompleted")).isEqualTo(1);
        assertThat(storedBatch.get("casePass")).isEqualTo(1);
        assertThat(storedBatch.get("duration")).isEqualTo(1000L);
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldCancelOnlyRequestedCaseAndKeepOtherCasesRunnable() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        Map<String, Object> runningCase = new LinkedHashMap<>();
        runningCase.put("case_id", "CASE_001");
        runningCase.put("status", "running");
        Map<String, Object> waitingCase = new LinkedHashMap<>();
        waitingCase.put("case_id", "CASE_002");
        waitingCase.put("status", "waiting");
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "BATCH_CASE_CANCEL");
        batch.put("startedAt", "2026-07-24 10:00:00");
        batch.put("caseResults", new ArrayList<>(List.of(runningCase, waitingCase)));
        storedScene.setDebugRecord(new ArrayList<>(List.of(batch)));

        service.cancelCase("100", "BATCH_CASE_CANCEL", "CASE_001");

        List<Map<String, Object>> caseResults = (List<Map<String, Object>>)(List<?>)batch.get("caseResults");
        assertThat(caseResults).extracting(item -> item.get("status")).containsExactly("cancelled", "waiting");
        assertThat(caseResults.get(0).get("caseCancelRequested")).isEqualTo(true);
        assertThat(batch.get("cancelRequested")).isNull();
        assertThat(batch.get("executeStatus")).isEqualTo("running");
        assertThat(service.getCaseCancellation("100", "BATCH_CASE_CANCEL", "CASE_001").isCaseCancelRequested())
            .isTrue();
        verify(sceneMapper).updateById(storedScene);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFinishBatchAsCancelledWhenLastRunningCaseWasCancelled() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        Map<String, Object> passedCase = new LinkedHashMap<>();
        passedCase.put("case_id", "CASE_001");
        passedCase.put("status", "passed");
        Map<String, Object> runningCase = new LinkedHashMap<>();
        runningCase.put("case_id", "CASE_002");
        runningCase.put("status", "running");
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "BATCH_LAST_CASE_CANCEL");
        batch.put("startedAt", "2026-07-25 10:00:00");
        batch.put("caseResults", new ArrayList<>(List.of(passedCase, runningCase)));
        storedScene.setDebugRecord(new ArrayList<>(List.of(batch)));

        service.cancelCase("100", "BATCH_LAST_CASE_CANCEL", "CASE_002");

        List<Map<String, Object>> caseResults = (List<Map<String, Object>>)(List<?>)batch.get("caseResults");
        assertThat(caseResults).extracting(item -> item.get("status")).containsExactly("passed", "cancelled");
        assertThat(caseResults).extracting(item -> item.get("executeStatus")).containsExactly("completed", "cancelled");
        assertThat(caseResults).extracting(item -> item.get("executeResult")).containsExactly("passed", "cancelled");
        assertThat(batch.get("executeStatus")).isEqualTo("cancelled");
        assertThat(batch.get("executeResult")).isEqualTo("cancelled");
        assertThat(batch.get("casePass")).isEqualTo(1);
        assertThat(batch.get("caseCancelled")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowRunnerCommitFailureToOverrideDetailedPassedResult() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        Map<String, Object> passedCase = new LinkedHashMap<>();
        passedCase.put("case_id", "CASE_001");
        passedCase.put("status", "passed");
        passedCase.put("result_detailed", true);
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "BATCH_COMMIT_FAILED");
        batch.put("startedAt", "2026-07-17 10:00:00");
        batch.put("caseResults", new ArrayList<>(List.of(passedCase)));
        storedScene.setDebugRecord(new ArrayList<>(List.of(batch)));
        AutomationPlaywrightBatchCaseStatusReq request = new AutomationPlaywrightBatchCaseStatusReq();
        request.setStatus("failed");
        request.setError("提交 Playwright 批次登录态失败");

        service.updateBatchCaseStatus("100", "BATCH_COMMIT_FAILED", "CASE_001", request);

        Map<String, Object> storedCase = (Map<String, Object>)((List<?>)batch.get("caseResults")).get(0);
        assertThat(storedCase.get("status")).isEqualTo("failed");
        assertThat(storedCase.get("result_detailed")).isEqualTo(true);
        assertThat(storedCase.get("error")).isEqualTo("提交 Playwright 批次登录态失败");
        verify(sessionStateService).cleanupBatch("BATCH_COMMIT_FAILED");
    }

    @Test
    void shouldRejectReusableStateFromAnotherEnvironment() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);
        Map<String, Object> waitingCase = new LinkedHashMap<>();
        waitingCase.put("case_id", "CASE_001");
        waitingCase.put("status", "starting");
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "BATCH_ENV_47");
        batch.put("projectEnvironmentId", 47L);
        batch.put("caseResults", new ArrayList<>(List.of(waitingCase)));
        storedScene.setDebugRecord(new ArrayList<>(List.of(batch)));

        assertThatThrownBy(() -> service.validateReusableBatchCase("100", "BATCH_ENV_47", "CASE_001", 48L))
            .hasMessageContaining("产品环境不匹配");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCancelPlanBatchInTestRecord() {
        AutomationUiSceneDO storedScene = scene(1L);
        when(sceneMapper.selectById(100L)).thenReturn(storedScene);

        Map<String, Object> runningCase = new LinkedHashMap<>();
        runningCase.put("case_id", "CASE_001");
        runningCase.put("status", "running");
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("recordType", "playwright-batch");
        batch.put("batchId", "PLAN_BATCH_CANCEL");
        batch.put("testPlanId", "PLAN_001");
        batch.put("startedAt", "2026-07-19 10:00:00");
        batch.put("caseResults", new ArrayList<>(List.of(runningCase)));
        storedScene.setTestRecord(new ArrayList<>(List.of(batch)));

        service.cancelBatch("100", "PLAN_BATCH_CANCEL");

        List<Map<String, Object>> caseResults = (List<Map<String, Object>>)(List<?>)batch.get("caseResults");
        assertThat(storedScene.getDebugRecord()).isNull();
        assertThat(caseResults.get(0).get("status")).isEqualTo("cancelled");
        assertThat(batch.get("executeStatus")).isEqualTo("cancelled");
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
        environment.setLastDomain("https://172.19.5.47");
        environment.setStatus(DisEnableStatusEnum.ENABLE);
        return environment;
    }

    private AutomationPlaywrightBatchCreateReq batchRequest() {
        AutomationPlaywrightBatchCreateReq request = new AutomationPlaywrightBatchCreateReq();
        request.setSceneKey("100");
        request.setExecutionType("playwright-runner");
        request.setCaseIds(List.of("CASE_001"));
        request.setProjectEnvironmentId(47L);
        return request;
    }

    private StepDO step(String id, String name, int order, StatusTypeEnum status) {
        StepDO step = new StepDO();
        step.setId(id);
        step.setName(name);
        step.setOrder(order);
        step.setStatus(status);
        return step;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
