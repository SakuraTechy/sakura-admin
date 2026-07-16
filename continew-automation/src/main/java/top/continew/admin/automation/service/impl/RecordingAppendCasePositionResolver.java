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

import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * Resolves the insertion index for recording-imported cases.
 *
 * @author Codex
 */
final class RecordingAppendCasePositionResolver {

    private static final String POSITION_FIRST = "FIRST";
    private static final String POSITION_LAST = "LAST";
    private static final String POSITION_AFTER = "AFTER";
    private static final String LEGACY_POSITION_FIRST = "__FIRST__";
    private static final String DEFAULT_CASE_ID_PREFIX = "SCENE_CASE_";

    private RecordingAppendCasePositionResolver() {
    }

    static void normalizeOrder(List<CaseDO> caseList) {
        caseList.sort(Comparator.comparing(RecordingAppendCasePositionResolver::orderOf));
    }

    static String resolveCaseIdPrefix(List<CaseDO> caseList) {
        if (caseList != null) {
            for (CaseDO caseDO : caseList) {
                if (caseDO != null && caseDO.getId() != null && !caseDO.getId().isBlank()) {
                    return caseDO.getId().replaceFirst("\\d+$", "");
                }
            }
        }
        return DEFAULT_CASE_ID_PREFIX;
    }

    static void renumberCaseIds(List<CaseDO> caseList, String prefix) {
        String caseIdPrefix = prefix == null || prefix.isBlank() ? DEFAULT_CASE_ID_PREFIX : prefix;
        for (int i = 0; i < caseList.size(); i++) {
            CaseDO caseDO = caseList.get(i);
            if (caseDO == null) {
                continue;
            }
            String oldCaseId = caseDO.getId();
            String caseId = caseIdPrefix + String.format("%03d", i + 1);
            caseDO.setOrder(i + 1);
            caseDO.setId(caseId);
            if (caseDO.getStepList() != null) {
                caseDO.getStepList().forEach(stepDO -> {
                    if (stepDO != null) {
                        stepDO.setPid(caseId);
                        if (oldCaseId != null && oldCaseId.equals(stepDO.getCopyPid())) {
                            stepDO.setCopyPid(caseId);
                        }
                    }
                });
            }
        }
    }

    static int resolveIndex(List<CaseDO> caseList, String appendPosition, String appendAfterCaseId) {
        String position = appendPosition == null ? "" : appendPosition.trim().toUpperCase(Locale.ROOT);
        if (POSITION_FIRST.equals(position)) {
            return 0;
        }
        if (POSITION_LAST.equals(position)) {
            return caseList.size();
        }
        if (POSITION_AFTER.equals(position)) {
            if (appendAfterCaseId == null || appendAfterCaseId.isBlank()) {
                throw new BusinessException("录制导入失败：appendPosition=AFTER 时 appendAfterCaseId 不能为空");
            }
            return indexAfter(caseList, appendAfterCaseId);
        }
        if (!position.isEmpty()) {
            throw new BusinessException("录制导入失败：不支持的 appendPosition=" + appendPosition);
        }

        // Compatible with requests emitted before appendPosition was introduced.
        if (LEGACY_POSITION_FIRST.equals(appendAfterCaseId)) {
            return 0;
        }
        if (appendAfterCaseId == null || appendAfterCaseId.isBlank()) {
            return caseList.size();
        }
        return indexAfter(caseList, appendAfterCaseId);
    }

    private static int indexAfter(List<CaseDO> caseList, String appendAfterCaseId) {
        for (int i = 0; i < caseList.size(); i++) {
            CaseDO caseDO = caseList.get(i);
            if (caseDO != null && appendAfterCaseId.equals(caseDO.getId())) {
                return i + 1;
            }
        }
        throw new BusinessException("录制导入失败：追加位置用例不存在，appendAfterCaseId=" + appendAfterCaseId);
    }

    private static int orderOf(CaseDO caseDO) {
        if (caseDO == null || caseDO.getOrder() == null || caseDO.getOrder() < 1) {
            return Integer.MAX_VALUE;
        }
        return caseDO.getOrder();
    }
}
