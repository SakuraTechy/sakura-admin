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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.entity.ui.StepDO;

class RecordingStepPositionResolverTest {

    @Test
    void shouldResolveStepPositions() {
        List<StepDO> steps = steps("CASE_STEP_002", 2, "CASE_STEP_001", 1);
        RecordingStepPositionResolver.normalizeOrder(steps);

        assertEquals(0, RecordingStepPositionResolver.resolveIndex(steps, "FIRST", null));
        assertEquals(2, RecordingStepPositionResolver.resolveIndex(steps, "LAST", null));
        assertEquals(2, RecordingStepPositionResolver.resolveIndex(steps, "AFTER", "CASE_STEP_002"));
    }

    @Test
    void shouldRenumberOnlyStepsInsideTargetCase() {
        List<StepDO> steps = steps("CASE_STEP_001", 1, "CASE_STEP_001", 1, "CASE_STEP_003", 3);

        RecordingStepPositionResolver.renumberStepIds(steps, "SCENE_CASE_002");

        assertEquals(List.of("CASE_STEP_001", "CASE_STEP_002", "CASE_STEP_003"), steps.stream()
            .map(StepDO::getId)
            .toList());
        assertEquals(List.of(1, 2, 3), steps.stream().map(StepDO::getOrder).toList());
        assertEquals(List.of("SCENE_CASE_002", "SCENE_CASE_002", "SCENE_CASE_002"), steps.stream()
            .map(StepDO::getPid)
            .toList());
    }

    private static List<StepDO> steps(Object... values) {
        List<StepDO> steps = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            StepDO step = new StepDO();
            step.setId((String)values[i]);
            step.setOrder((Integer)values[i + 1]);
            steps.add(step);
        }
        return steps;
    }
}
