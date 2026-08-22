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

import java.util.Locale;
import java.util.Set;

import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.starter.core.exception.BadRequestException;

/** 显式执行作用域的统一校验，禁止查询层静默回退到全局 latest。 */
public final class AutomationUiExecutionScopeSupport {

    private static final Set<String> SOURCES = Set.of("debug", "test");
    private static final Set<String> STATUSES = Set
        .of("10", "11", "12", "17", "queued", "running", "completed", "passed", "failed", "cancelled", "interrupted", "blocked", "skipped");
    private static final Set<String> RESULTS = Set
        .of("13", "14", "15", "16", "17", "not_executed", "passed", "failed", "skipped", "cancelled");

    private AutomationUiExecutionScopeSupport() {
    }

    public static AutomationUiExecutionScopeReq normalize(AutomationUiExecutionScopeReq source) {
        if (source == null || source.getRecordSource() == null) {
            throw new BadRequestException("EXECUTION_SCOPE_REQUIRED：必须提供 recordSource=debug|test");
        }
        String recordSource = source.getRecordSource().trim().toLowerCase(Locale.ROOT);
        if (!SOURCES.contains(recordSource)) {
            throw new BadRequestException("INVALID_RECORD_SOURCE：recordSource 只能是 debug 或 test");
        }
        if ("debug".equals(recordSource) && (source.getTestPlanId() != null || source.getTestReportId() != null)) {
            throw new BadRequestException("INVALID_EXECUTION_SCOPE：debug 作用域不能携带测试计划或测试报告 ID");
        }
        AutomationUiExecutionScopeReq normalized = new AutomationUiExecutionScopeReq();
        normalized.setRecordSource(recordSource);
        normalized.setTestPlanId(source.getTestPlanId());
        normalized.setTestReportId(source.getTestReportId());
        normalized.setBuildNumber(source.getBuildNumber());
        return normalized;
    }

    public static String validateStatus(String status) {
        return validateOptional(status, STATUSES, "status");
    }

    public static String validateResult(String result) {
        return validateOptional(result, RESULTS, "result");
    }

    private static String validateOptional(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("INVALID_" + field.toUpperCase(Locale.ROOT) + "：不支持的执行筛选值");
        }
        return normalized;
    }
}
