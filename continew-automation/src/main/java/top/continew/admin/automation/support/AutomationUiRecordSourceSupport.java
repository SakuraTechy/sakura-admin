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

package top.continew.admin.automation.support;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/** UI 执行来源的服务端固定分类规则。 */
public final class AutomationUiRecordSourceSupport {

    public static final String DEBUG = "debug";
    public static final String TEST = "test";
    public static final String INTERNAL = "internal";

    private static final Set<String> INTERNAL_RECORD_TYPES = Set
        .of("internal-interactive-context", "interactive-execution-context");
    private static final Set<String> TEST_TRIGGER_TYPES = Set.of("test-plan", "schedule");

    private AutomationUiRecordSourceSupport() {
    }

    /**
     * 来源只能由服务端已有执行事实推导，不能接收调用方提交的 recordSource。
     * buildNumber 可能来自调试 Jenkins，不能单独判为测试执行。
     */
    public static String classify(String recordType, String triggerType, Long testPlanId, Long testReportId) {
        if (isInternal(recordType)) {
            return INTERNAL;
        }
        String normalizedTrigger = StringUtils.lowerCase(StringUtils.trimToEmpty(triggerType));
        if (testPlanId != null || testReportId != null || TEST_TRIGGER_TYPES.contains(normalizedTrigger)) {
            return TEST;
        }
        return DEBUG;
    }

    public static boolean isInternal(String recordType) {
        return INTERNAL_RECORD_TYPES.contains(StringUtils.lowerCase(StringUtils.trimToEmpty(recordType)));
    }
}
