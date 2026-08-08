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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedCaseReq;
import top.continew.admin.automation.model.req.recording.PlaywrightRecordedStepReq;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService.ScreenshotStorageException;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;
import top.continew.admin.common.enums.StatusTypeEnum;

class PlaywrightRecordingAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AutomationOperationCatalogServiceImpl catalogService = catalogService();
    private final AutomationOperationConfigValidator configValidator = new AutomationOperationConfigValidator();
    private final CuecastRecordingOperationProjector projector = new CuecastRecordingOperationProjector(objectMapper, catalogService, configValidator);
    private final PlaywrightRecordingAssembler assembler = new PlaywrightRecordingAssembler(objectMapper, new NoopScreenshotService(), catalogService, projector);

    @Test
    void shouldPreserveRequiredConfigsAndMaskRawScreenshot() {
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setId(278);
        recordedCase.setName("登录流程");
        recordedCase.setStartUrl("https://example.com/login");
        recordedCase.setSteps(List.of(recordedStep()));

        CaseDO caseDO = assembler
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-001", "AAS_P", "V6.5B06D011", "REC_SCENE_001", false, false));

        StepDO stepDO = caseDO.getStepList().get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(caseDO.getStatus()).isEqualTo(StatusTypeEnum.ENABLE);
        assertThat(stepDO.getId()).isEqualTo("CASE_STEP_001");
        assertThat(stepDO.getStatus()).isEqualTo(StatusTypeEnum.ENABLE);
        assertThat(stepDO.getOperationType()).isEqualTo("点击操作");
        assertThat(stepDO.getOperationName()).isEqualTo("元素点击");
        assertThat(stepDO.getOperationValue()).isEqualTo("web-click");
        assertThat(configs).containsEntry("action_type", "click")
            .containsEntry("source", "sakura-playwright")
            .containsEntry("recording_id", "rec-001")
            .containsEntry("original_case_id", "278")
            .containsEntry("original_step_id", "1")
            .containsEntry("value_masked", "1")
            .containsEntry("screenshot_present", "true");
        assertThat(configs.get("locator_meta")).contains("\"version\":1");
        assertThat(configs.get("playwright_step")).contains("\"locator_meta\"")
            .contains("\"screenshot_present\":true")
            .doesNotContain("data:image/jpeg;base64");
    }

    @Test
    void shouldSaveUnknownActionAsCustomWithoutDroppingRawStep() {
        PlaywrightRecordedStepReq step = recordedStep();
        step.setActionType("custom_magic");
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setName("未知动作");
        recordedCase.setSteps(List.of(step));

        CaseDO caseDO = assembler
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-002", "AAS_P", "V6.5B06D011", "REC_SCENE_002", false, false));
        StepDO stepDO = caseDO.getStepList().get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(stepDO.getOperationValue()).isEqualTo("pw-custom");
        assertThat(configs).containsEntry("unknown_action_type", "custom_magic");
        assertThat(configs.get("playwright_step")).contains("\"action_type\":\"custom_magic\"");
    }

    @Test
    void shouldProjectCuecastVariableAndKeepOriginalRawStep() {
        PlaywrightRecordedStepReq step = new PlaywrightRecordedStepReq();
        step.setId(1);
        step.setActionType("set_variable");
        step.setTargetSelector(".order-number");
        step.setValue("order_number");
        step.addExtra("value_text", "ORD-20260807");
        step.setLocatorMeta(Map.of("candidates", List.of(Map
            .of("type", "css_unique", "value", ".order-number")), "context", Map.of("variable", Map
                .of("name", "order_number", "source", "text", "extract", Map.of("mode", "full")))));
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setSteps(List.of(step));

        StepDO stepDO = assembler
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-variable", "AAS_P", "V1", "SCENE", false, false))
            .getStepList()
            .get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(stepDO.getOperationValue()).isEqualTo("web-set");
        assertThat(configs).containsEntry("method_code", "global.variable.set")
            .containsEntry("method_version", "1")
            .containsEntry("catalog_version", "2026-08-07.1")
            .doesNotContainKey("unknown_action_type");
        assertThat(configs.get("method_config")).contains("\"variable_name\":\"order_number\"")
            .doesNotContain("ORD-20260807");
        assertThat(configs.get("playwright_step")).contains("\"action_type\":\"set_variable\"")
            .contains("\"value_text\":\"ORD-20260807\"")
            .contains("\"locator_meta\"");
    }

    @Test
    void shouldFilterScreenshotBinaryVariantsFromExtraFields() {
        PlaywrightRecordedStepReq step = recordedStep();
        step.setScreenshot(null);
        step.addExtra("screenshot", "raw-base64-payload");
        step.addExtra("nested_payload", Map.of("screenshot", "nested-raw-base64-payload"));
        step.addExtra("screenshot_base64", "data:image/png;base64,large-image");
        step.addExtra("screenshot_meta", Map.of("base64", "large-image"));
        step.addExtra("vendor_flag", "keep-me");
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setSteps(List.of(step));

        StepDO stepDO = assembler
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-003", "AAS_P", "V6.5B06D011", "REC_SCENE_003", false, false))
            .getStepList()
            .get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(configs.get("playwright_step")).contains("\"screenshot_present\":true")
            .contains("\"vendor_flag\":\"keep-me\"")
            .doesNotContain("data:image/png;base64,large-image")
            .doesNotContain("large-image")
            .doesNotContain("raw-base64-payload")
            .doesNotContain("nested-raw-base64-payload");
    }

    @Test
    void shouldNeverKeepRawScreenshotEvenWhenCompatibilityFlagIsEnabled() {
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setSteps(List.of(recordedStep()));

        StepDO stepDO = assembler
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-004", "AAS_P", "V6.5B06D011", "REC_SCENE_004", false, true))
            .getStepList()
            .get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(configs.get("playwright_step")).contains("\"screenshot_present\":true")
            .doesNotContain("data:image/jpeg;base64,AAAA")
            .doesNotContain("\"screenshot\"");
    }

    @Test
    void shouldKeepImportableStepWhenScreenshotStorageFails() {
        PlaywrightRecordingAssembler failingAssembler = new PlaywrightRecordingAssembler(objectMapper, new NoopScreenshotService() {
            @Override
            public ScreenshotArtifact store(String recordingId,
                                            String projectShortName,
                                            String versionName,
                                            String sceneId,
                                            String caseId,
                                            String stepId,
                                            String screenshot) {
                throw new ScreenshotStorageException("storage unavailable", new IllegalStateException("offline"));
            }
        }, catalogService, projector);

        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setSteps(List.of(recordedStep()));
        StepDO stepDO = failingAssembler
            .toCase(recordedCase, new PlaywrightRecordingAssembler.RecordingImportContext("rec-storage", "AAS_P", "V1", "SCENE", true, false))
            .getStepList()
            .get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(configs.get("playwright_step")).contains("\"screenshot_present\":true")
            .doesNotContain("data:image/jpeg;base64,AAAA");
    }

    private PlaywrightRecordedStepReq recordedStep() {
        PlaywrightRecordedStepReq step = new PlaywrightRecordedStepReq();
        step.setId(1);
        step.setActionType("click");
        step.setTargetSelector("#username");
        step.setTargetXpath("//*[@id='username']");
        step.setLocatorMeta(Map.of("version", 1, "context", Map.of("control_kind", "input:text")));
        step.setValue("secret");
        step.setValueMasked(1);
        step.setUrl("https://example.com/login");
        step.setDescription("点击用户名");
        step.setWaitBefore(0);
        step.setIsOverlay(0);
        step.setScreenshot("data:image/jpeg;base64,AAAA");
        return step;
    }

    private static AutomationOperationCatalogServiceImpl catalogService() {
        AutomationOperationCatalogServiceImpl service = new AutomationOperationCatalogServiceImpl(new ObjectMapper());
        service.initialize();
        return service;
    }

    private static class NoopScreenshotService implements AutomationRecordingScreenshotService {

        @Override
        public ScreenshotArtifact store(String recordingId,
                                        String projectShortName,
                                        String versionName,
                                        String sceneId,
                                        String caseId,
                                        String stepId,
                                        String screenshot) {
            return null;
        }

        @Override
        public ScreenshotResource load(String recordingId, String fileName) {
            return null;
        }
    }
}
