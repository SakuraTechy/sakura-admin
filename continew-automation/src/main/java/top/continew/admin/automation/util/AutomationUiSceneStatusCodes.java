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

package top.continew.admin.automation.util;

import top.continew.admin.common.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * UI 场景执行状态/结果字典 value（与 sys_dict_item status_type 10-16 一致，库内仅存此类值）。
 */
public final class AutomationUiSceneStatusCodes {

    public static final String STATUS_NOT_STARTED = "10";
    public static final String STATUS_RUNNING = "11";
    public static final String STATUS_COMPLETED = "12";

    public static final String RESULT_NOT_EXECUTED = "13";
    public static final String RESULT_PASSED = "14";
    public static final String RESULT_FAILED = "15";
    public static final String RESULT_SKIPPED = "16";

    private static final Set<String> STATUS_VALUES = Set.of(STATUS_NOT_STARTED, STATUS_RUNNING, STATUS_COMPLETED);
    private static final Set<String> RESULT_VALUES = Set
        .of(RESULT_NOT_EXECUTED, RESULT_PASSED, RESULT_FAILED, RESULT_SKIPPED);

    private static final Map<String, String> STATUS_LEGACY = Map.ofEntries(Map
        .entry("NOT_STARTED", STATUS_NOT_STARTED), Map.entry("RUNNING", STATUS_RUNNING), Map
            .entry("COMPLETED", STATUS_COMPLETED), Map.entry("未开始", STATUS_NOT_STARTED), Map
                .entry("进行中", STATUS_RUNNING), Map.entry("已完成", STATUS_COMPLETED));

    private static final Map<String, String> RESULT_LEGACY = Map.ofEntries(Map
        .entry("NOT_EXECUTED", RESULT_NOT_EXECUTED), Map.entry("PASSED", RESULT_PASSED), Map
            .entry("FAILED", RESULT_FAILED), Map.entry("SKIPPED", RESULT_SKIPPED), Map
                .entry("未执行", RESULT_NOT_EXECUTED), Map.entry("全部通过", RESULT_PASSED), Map
                    .entry("不通过", RESULT_FAILED), Map.entry("跳过", RESULT_SKIPPED), Map
                        .entry("-", RESULT_NOT_EXECUTED), Map.entry("RUNNING", RESULT_NOT_EXECUTED));

    private AutomationUiSceneStatusCodes() {
    }

    /**
     * 归一化执行状态为字典 value（仅在外部回调等入库边界使用）。
     */
    public static String normalizeStatus(String raw) {
        if (StringUtils.isBlank(raw)) {
            return STATUS_COMPLETED;
        }
        String trimmed = raw.trim();
        if (STATUS_VALUES.contains(trimmed)) {
            return trimmed;
        }
        String mapped = STATUS_LEGACY.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        mapped = STATUS_LEGACY.get(trimmed.toUpperCase());
        return mapped != null ? mapped : trimmed;
    }

    /**
     * 归一化执行结果为字典 value；统计字段用于回调结果缺失时的兜底。
     */
    public static String normalizeResult(String raw, Integer total, Integer pass, Integer fail, Integer skip) {
        if (StringUtils.isNotBlank(raw)) {
            String trimmed = raw.trim();
            if (RESULT_VALUES.contains(trimmed)) {
                return trimmed;
            }
            String mapped = RESULT_LEGACY.get(trimmed);
            if (mapped != null) {
                return mapped;
            }
            mapped = RESULT_LEGACY.get(trimmed.toUpperCase());
            if (mapped != null) {
                return mapped;
            }
        }
        int totalCount = total == null ? 0 : total;
        int passCount = pass == null ? 0 : pass;
        int failCount = fail == null ? 0 : fail;
        int skipCount = skip == null ? 0 : skip;
        if (failCount > 0) {
            return RESULT_FAILED;
        }
        if (passCount > 0 && passCount + skipCount >= totalCount) {
            return RESULT_PASSED;
        }
        if (skipCount > 0 && passCount == 0 && failCount == 0) {
            return RESULT_SKIPPED;
        }
        return RESULT_NOT_EXECUTED;
    }

    public static boolean isPassedResult(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        return RESULT_PASSED.equals(trimmed) || "PASSED".equalsIgnoreCase(trimmed) || "全部通过".equals(trimmed);
    }
}
