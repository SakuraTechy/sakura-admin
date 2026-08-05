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

package top.continew.admin.test.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.test.model.query.TestMetricScopeQuery;
import top.continew.admin.test.model.resp.TestMetricBreakdownResp;
import top.continew.admin.test.model.resp.TestMetricFailureResp;
import top.continew.admin.test.model.resp.TestMetricSummaryResp;
import top.continew.admin.test.model.resp.TestMetricTrendResp;
import top.continew.admin.test.service.TestMetricQueryService;
import top.continew.starter.web.model.R;

@Tag(name = "测试度量 v2 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/test/metrics")
public class TestMetricV2Controller {

    private final TestMetricQueryService testMetricQueryService;

    @Operation(summary = "查询指标概览")
    @SaCheckPermission("test:testMetric:list")
    @GetMapping("/summary")
    public R<TestMetricSummaryResp> summary(@Validated TestMetricScopeQuery query) {
        return R.ok(testMetricQueryService.getSummary(query));
    }

    @Operation(summary = "查询指标趋势")
    @SaCheckPermission("test:testMetric:list")
    @GetMapping("/trends")
    public R<TestMetricTrendResp> trends(@Validated TestMetricScopeQuery query) {
        return R.ok(testMetricQueryService.getTrends(query));
    }

    @Operation(summary = "查询指标维度分布")
    @SaCheckPermission("test:testMetric:list")
    @GetMapping("/breakdowns")
    public R<TestMetricBreakdownResp> breakdowns(@Validated TestMetricScopeQuery query,
                                                 @RequestParam(defaultValue = "result") String dimension) {
        return R.ok(testMetricQueryService.getBreakdown(query, dimension));
    }

    @Operation(summary = "查询失败场景排行")
    @SaCheckPermission("test:testMetric:list")
    @GetMapping("/failures")
    public R<TestMetricFailureResp> failures(@Validated TestMetricScopeQuery query,
                                             @RequestParam(defaultValue = "10") Integer limit) {
        return R.ok(testMetricQueryService.getFailures(query, limit));
    }
}
