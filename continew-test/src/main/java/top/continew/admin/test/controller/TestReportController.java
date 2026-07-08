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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.test.model.query.TestReportQuery;
import top.continew.admin.test.model.req.TestReportReq;
import top.continew.admin.test.model.req.TestReportUploadReq;
import top.continew.admin.test.model.resp.TestReportDetailResp;
import top.continew.admin.test.model.resp.TestReportResp;
import top.continew.admin.test.service.TestReportService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Tag(name = "测试报告管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/test/testReport", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE,
    Api.EXPORT})
public class TestReportController extends BaseController<TestReportService, TestReportResp, TestReportDetailResp, TestReportQuery, TestReportReq> {

    @Override
    @SaCheckPermission("test:testReport:list")
    @GetMapping("/list")
    public List<TestReportResp> list(@Validated TestReportQuery query, @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @SaCheckPermission("test:testReport:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody TestReportReq req) {
        CheckUtils.throwIf(baseService.isExists(req.getName(), req.getProjectId(), null), "测试报告名称已存在");
        return super.create(req);
    }

    @Override
    @SaCheckPermission("test:testReport:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody TestReportReq req,
                       @PathVariable("id") Long id) {
        CheckUtils.throwIf(baseService.isExists(req.getName(), req.getProjectId(), id), "测试报告名称已存在");
        super.update(req, id);
    }

    @Operation(summary = "删除测试报告")
    @Parameter(name = "ids", description = "逗号分隔的 ID 列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("test:testReport:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        baseService.deleteByIds(ids);
    }

    @Operation(summary = "导出测试报告")
    @SaCheckPermission("test:testReport:export")
    @GetMapping("/export")
    public void export(@Validated TestReportQuery query, @Validated SortQuery sortQuery, HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
            if (!ids.isEmpty()) {
                ExcelUtils.export(baseService.selectByIds(ids), "测试报告", TestReportDetailResp.class, response);
                return;
            }
        } catch (Exception ignored) {
        }
        baseService.export(query, sortQuery, response);
    }

    @Operation(summary = "上传测试报告结果")
    @SaCheckPermission("test:testReport:uploadResult")
    @PostMapping("/uploadResult")
    public void uploadResult(@RequestBody TestReportUploadReq req) {
        baseService.uploadResult(req);
    }

    @Operation(summary = "上传测试报告结果（兼容旧接口）")
    @SaCheckPermission("test:testReport:uploadResult")
    @PutMapping("/uploadResults")
    public void uploadResults(@RequestBody TestReportUploadReq req) {
        baseService.uploadResult(req);
    }
}
