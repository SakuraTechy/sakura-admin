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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * Resolves insertion positions and normalizes steps inside one case.
 *
 * @author Codex
 */
final class RecordingStepPositionResolver {

    private static final String POSITION_FIRST = "FIRST";
    private static final String POSITION_LAST = "LAST";
    private static final String POSITION_AFTER = "AFTER";
    private static final String STEP_ID_PREFIX = "CASE_STEP_";

    private RecordingStepPositionResolver() {
    }

    static void normalizeOrder(List<StepDO> stepList) {
        stepList.removeIf(step -> step == null);
        stepList.sort(Comparator.comparing(RecordingStepPositionResolver::orderOf));
    }

    static void renumberStepIds(List<StepDO> stepList, String caseId) {
        for (int i = 0; i < stepList.size(); i++) {
            StepDO step = stepList.get(i);
            if (step == null) {
                continue;
            }
            step.setId(STEP_ID_PREFIX + String.format("%03d", i + 1));
            step.setOrder(i + 1);
            step.setPid(caseId);
        }
    }

    static int resolveIndex(List<StepDO> stepList, String appendPosition, String appendAfterStepId) {
        String position = appendPosition == null ? "" : appendPosition.trim().toUpperCase(Locale.ROOT);
        if (POSITION_FIRST.equals(position)) {
            return 0;
        }
        if (POSITION_LAST.equals(position)) {
            return stepList.size();
        }
        if (POSITION_AFTER.equals(position)) {
            if (appendAfterStepId == null || appendAfterStepId.isBlank()) {
                throw new BusinessException("录制导入失败：stepAppendPosition=AFTER 时 appendAfterStepId 不能为空");
            }
            for (int i = 0; i < stepList.size(); i++) {
                StepDO step = stepList.get(i);
                if (step != null && appendAfterStepId.equals(step.getId())) {
                    return i + 1;
                }
            }
            throw new BusinessException("录制导入失败：步骤追加锚点不存在，appendAfterStepId=" + appendAfterStepId);
        }
        if (!position.isEmpty()) {
            throw new BusinessException("录制导入失败：不支持的 stepAppendPosition=" + appendPosition);
        }
        return stepList.size();
    }

    private static int orderOf(StepDO step) {
        if (step == null || step.getOrder() == null || step.getOrder() < 1) {
            return Integer.MAX_VALUE;
        }
        return step.getOrder();
    }
}
