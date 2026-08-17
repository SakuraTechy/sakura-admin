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
import top.continew.admin.test.model.query.TestTimedTaskQuery;
import top.continew.admin.test.model.query.TestTimedTaskLogQuery;
import top.continew.admin.test.model.query.TestTimedTaskRunQuery;
import top.continew.admin.test.model.req.TestTimedTaskReq;
import top.continew.admin.test.model.resp.TestTimedTaskCapabilityResp;
import top.continew.admin.test.model.resp.TestTimedTaskDetailResp;
import top.continew.admin.test.model.resp.TestTimedTaskLogResp;
import top.continew.admin.test.model.resp.TestTimedTaskResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunResp;
import top.continew.admin.test.service.TestTimedTaskScheduleCapabilityService;
import top.continew.admin.test.service.TestTimedTaskService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Tag(name = "测试定时任务管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/test/timedTask", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE,
    Api.EXPORT})
public class TestTimedTaskController extends BaseController<TestTimedTaskService, TestTimedTaskResp, TestTimedTaskDetailResp, TestTimedTaskQuery, TestTimedTaskReq> {

    private final TestTimedTaskScheduleCapabilityService capabilityService;

    @Operation(summary = "查询测试定时任务调度能力")
    @SaCheckPermission("test:timedTask:list")
    @GetMapping("/capability")
    public TestTimedTaskCapabilityResp capability() {
        return capabilityService.probe();
    }

    @Override
    @SaCheckPermission("test:timedTask:list")
    @GetMapping("/list")
    public List<TestTimedTaskResp> list(@Validated TestTimedTaskQuery query, @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @SaCheckPermission("test:timedTask:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody TestTimedTaskReq req) {
        CheckUtils.throwIf(baseService.isExists(req.getName(), req.getTestPlanId(), null), "测试定时任务名称已存在");
        return super.create(req);
    }

    @Override
    @SaCheckPermission("test:timedTask:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody TestTimedTaskReq req,
                       @PathVariable("id") Long id) {
        CheckUtils.throwIf(baseService.isExists(req.getName(), req.getTestPlanId(), id), "测试定时任务名称已存在");
        super.update(req, id);
    }

    @Operation(summary = "删除测试定时任务")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("test:timedTask:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        baseService.deleteByIds(ids);
    }

    @Operation(summary = "导出测试定时任务")
    @SaCheckPermission("test:timedTask:export")
    @GetMapping("/export")
    public void export(@Validated TestTimedTaskQuery query,
                       @Validated SortQuery sortQuery,
                       HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
            if (!ids.isEmpty()) {
                ExcelUtils.export(baseService.selectByIds(ids), "测试定时任务", TestTimedTaskDetailResp.class, response);
                return;
            }
        } catch (Exception ignored) {
        }
        baseService.export(query, sortQuery, response);
    }

    @SaCheckPermission("test:timedTask:updateStatus")
    @PostMapping("/{id}/status")
    public void updateStatus(@PathVariable Long id, @RequestParam String status) {
        baseService.updateStatus(id, status);
    }

    @Operation(summary = "重试同步测试定时任务")
    @SaCheckPermission("test:timedTask:update")
    @PostMapping("/{id}/sync")
    public void retrySync(@PathVariable Long id) {
        baseService.retrySync(id);
    }

    @SaCheckPermission("test:timedTask:execute")
    @PostMapping("/{id}/trigger")
    public void trigger(@PathVariable Long id) {
        baseService.trigger(id);
    }

    @Operation(summary = "分页查询业务执行记录")
    @SaCheckPermission("test:timedTask:list")
    @GetMapping("/{id}/runs")
    public PageResp<TestTimedTaskRunResp> pageRuns(@PathVariable Long id,
                                                   @Validated TestTimedTaskRunQuery query,
                                                   @Validated PageQuery pageQuery) {
        return baseService.pageRuns(id, query, pageQuery);
    }

    @SaCheckPermission("test:timedTask:list")
    @GetMapping("/{id}/logs")
    public PageResp<TestTimedTaskLogResp> pageLogs(@PathVariable Long id,
                                                   @Validated TestTimedTaskLogQuery query,
                                                   @Validated PageQuery pageQuery) {
        return baseService.pageLogs(id, query, pageQuery);
    }
}
