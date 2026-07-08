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
import org.springframework.web.bind.annotation.*;
import top.continew.admin.test.model.query.TestMetricQuery;
import top.continew.admin.test.model.resp.TestMetricResp;
import top.continew.admin.test.service.TestMetricService;
import top.continew.starter.web.model.R;

@Tag(name = "测试度量 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/test/testMetric")
public class TestMetricController {

    private final TestMetricService testMetricService;

    @Operation(summary = "查询测试度量总览")
    @SaCheckPermission("test:testMetric:list")
    @GetMapping("/overview")
    public R<TestMetricResp> overview(@Validated TestMetricQuery query) {
        return R.ok(testMetricService.getOverview(query));
    }

    @Operation(summary = "查询测试度量（兼容旧接口）")
    @SaCheckPermission("test:testMetric:list")
    @PostMapping("/list")
    public R<TestMetricResp> list(@Validated @RequestBody TestMetricQuery query) {
        return R.ok(testMetricService.getOverview(query));
    }
}
