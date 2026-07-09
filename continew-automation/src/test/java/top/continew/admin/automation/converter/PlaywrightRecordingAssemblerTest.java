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
import top.continew.admin.common.enums.StatusTypeEnum;

class PlaywrightRecordingAssemblerTest {

    private final PlaywrightRecordingAssembler assembler = new PlaywrightRecordingAssembler(new ObjectMapper());

    @Test
    void shouldPreserveRequiredConfigsAndMaskRawScreenshot() {
        PlaywrightRecordedCaseReq recordedCase = new PlaywrightRecordedCaseReq();
        recordedCase.setId(278);
        recordedCase.setName("登录流程");
        recordedCase.setStartUrl("https://example.com/login");
        recordedCase.setSteps(List.of(recordedStep()));

        CaseDO caseDO = assembler.toCase(recordedCase, new PlaywrightRecordingAssembler
            .RecordingImportContext("rec-001", false, false));

        StepDO stepDO = caseDO.getStepList().get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(caseDO.getStatus()).isEqualTo(StatusTypeEnum.ENABLE);
        assertThat(stepDO.getStatus()).isEqualTo(StatusTypeEnum.ENABLE);
        assertThat(stepDO.getOperationValue()).isEqualTo("pw-click");
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

        CaseDO caseDO = assembler.toCase(recordedCase, new PlaywrightRecordingAssembler
            .RecordingImportContext("rec-002", false, false));
        StepDO stepDO = caseDO.getStepList().get(0);
        Map<String, String> configs = stepDO.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(stepDO.getOperationValue()).isEqualTo("pw-custom");
        assertThat(configs).containsEntry("unknown_action_type", "custom_magic");
        assertThat(configs.get("playwright_step")).contains("\"action_type\":\"custom_magic\"");
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
}
