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
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

class RecordingAppendCasePositionResolverTest {

    @Test
    void shouldResolveFirst() {
        List<CaseDO> cases = cases("CASE_002", 2, "CASE_001", 1);
        RecordingAppendCasePositionResolver.normalizeOrder(cases);

        assertEquals(0, RecordingAppendCasePositionResolver.resolveIndex(cases, "FIRST", null));
    }

    @Test
    void shouldResolveLast() {
        List<CaseDO> cases = cases("CASE_002", 2, "CASE_001", 1);
        RecordingAppendCasePositionResolver.normalizeOrder(cases);

        assertEquals(2, RecordingAppendCasePositionResolver.resolveIndex(cases, "LAST", null));
    }

    @Test
    void shouldResolveAfterTheSpecifiedCaseInDisplayOrder() {
        List<CaseDO> cases = cases("CASE_003", 3, "CASE_001", 1, "CASE_002", 2);
        RecordingAppendCasePositionResolver.normalizeOrder(cases);

        assertEquals(2, RecordingAppendCasePositionResolver.resolveIndex(cases, "AFTER", "CASE_002"));
        assertEquals(List.of("CASE_001", "CASE_002", "CASE_003"), cases.stream().map(CaseDO::getId).toList());
    }

    @Test
    void shouldRenumberCaseIdsAndStepParentsAfterInsertion() {
        List<CaseDO> cases = cases("SCENE_CASE_001", 1, "SCENE_CASE_002", 2);
        CaseDO inserted = cases("SCENE_CASE_999", 1).get(0);
        inserted.setStepList(new ArrayList<>());
        StepDO step = new StepDO();
        step.setPid(inserted.getId());
        inserted.getStepList().add(step);
        cases.add(0, inserted);

        RecordingAppendCasePositionResolver.renumberCaseIds(cases, "SCENE_CASE_");

        assertEquals(List.of("SCENE_CASE_001", "SCENE_CASE_002", "SCENE_CASE_003"), cases.stream()
            .map(CaseDO::getId)
            .toList());
        assertEquals("SCENE_CASE_001", cases.get(0).getStepList().get(0).getPid());
        assertEquals(List.of(1, 2, 3), cases.stream().map(CaseDO::getOrder).toList());
    }

    private static List<CaseDO> cases(Object... values) {
        List<CaseDO> cases = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            CaseDO caseDO = new CaseDO();
            caseDO.setId((String)values[i]);
            caseDO.setOrder((Integer)values[i + 1]);
            cases.add(caseDO);
        }
        return cases;
    }
}
