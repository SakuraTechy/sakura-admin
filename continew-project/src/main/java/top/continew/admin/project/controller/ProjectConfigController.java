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

package top.continew.admin.project.controller;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.project.model.query.ProjectConfigQuery;
import top.continew.admin.project.model.req.ProjectConfigReq;
import top.continew.admin.project.model.resp.ProjectConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectConfigResp;
import top.continew.admin.project.service.ProjectConfigService;

import top.continew.starter.file.excel.util.ExcelUtils;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;

/**
 * 项目管理-项目配置管理 API
 *
 * @author hagyao520
 * @since 2025/04/25 18:00
 */
@Tag(name = "项目管理-项目配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/project/projectConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE,
    Api.EXPORT})
public class ProjectConfigController extends BaseController<ProjectConfigService, ProjectConfigResp, ProjectConfigDetailResp, ProjectConfigQuery, ProjectConfigReq> {
    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("project:projectConfig:list")
    @GetMapping("/list")
    public List<ProjectConfigResp> list(@Validated ProjectConfigQuery query, @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("project:projectConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody ProjectConfigReq req) {
        String name = req.getName();
        String abbreviate = req.getAbbreviate();
        CheckUtils.throwIf(baseService.isExists(name, abbreviate, null), "新增失败，项目管理-项目配置 [{}] 已存在", name, abbreviate);
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("project:projectConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody ProjectConfigReq req,
                       @PathVariable("id") Long id) {
        String name = req.getName();
        String abbreviate = req.getAbbreviate();
        CheckUtils.throwIf(baseService.isExists(name, abbreviate, id), "修改失败，项目管理-项目配置 [{}] 已存在", name, abbreviate);
        super.update(req, id);
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("project:projectConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        //        baseService.deleteByIds(ids);
        ProjectConfigReq req = new ProjectConfigReq();
        ids.forEach(id -> {
            req.setDelFlag(StatusTypeEnum.ABNORMAL);
            super.update(req, id);
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("project:projectConfig:export")
    @GetMapping("/export")
    public void export(@Validated ProjectConfigQuery query,
                       @Validated SortQuery sortQuery,
                       HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
            if (!ids.isEmpty() && query.getName().equals("批量选择导出")) {
                List<ProjectConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", ProjectConfigDetailResp.class, response);
            } else {
                throw new IllegalArgumentException("No valid IDs provided");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID format", e);
        } catch (Exception e) {
            baseService.export(query, sortQuery, response);
        }
    }
}
