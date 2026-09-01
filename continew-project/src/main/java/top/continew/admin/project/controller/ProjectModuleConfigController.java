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

import top.continew.admin.project.model.req.ProjectModuleDragReq;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.model.query.ProjectModuleConfigQuery;
import top.continew.admin.project.model.req.ProjectModuleConfigReq;
import top.continew.admin.project.model.resp.ProjectModuleConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectModuleConfigResp;
import top.continew.admin.project.service.ProjectModuleConfigService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;

/**
 * 项目管理-模块配置管理 API
 *
 * @author hagyao520
 * @since 2025/06/06 17:44
 */
@Tag(name = "项目管理-模块配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/project/projectModuleConfig", api = {Api.TREE, Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE,
    Api.DELETE, Api.EXPORT})
public class ProjectModuleConfigController extends BaseController<ProjectModuleConfigService, ProjectModuleConfigResp, ProjectModuleConfigDetailResp, ProjectModuleConfigQuery, ProjectModuleConfigReq> {

    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("project:projectModuleConfig:list")
    @GetMapping("/list")
    public List<ProjectModuleConfigResp> list(@Validated ProjectModuleConfigQuery query,
                                              @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("project:projectModuleConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody ProjectModuleConfigReq req) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getParentId(), req.getName()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，项目管理-模块配置 [{}] 已存在", req.getName());
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("project:projectModuleConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody ProjectModuleConfigReq req,
                       @PathVariable("id") Long id) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getParentId(), req.getName()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，项目管理-模块配置 [{}] 已存在", req.getName());
        super.update(req, id);
    }

    @Operation(summary = "拖拽排序", description = "拖拽排序")
    @SaCheckPermission("project:projectModuleConfig:drag")
    @PostMapping("/drag")
    public void drag(@RequestBody ProjectModuleDragReq req) {
        baseService.drag(req);
        //        if(req.getDropPosition() == -1){
        ////            ProjectModuleConfigDetailResp dragNode = super.get(req.getDragNodeId());
        //            ProjectModuleConfigReq dragNode = new ProjectModuleConfigReq();
        //            dragNode.setParentId(req.getDropNode().getParentId());
        //            dragNode.setSort(req.getDropNode().getSort());
        //            super.update(dragNode, req.getDragNode().getId());
        //
        //            ProjectModuleConfigQuery query = new ProjectModuleConfigQuery();
        //            query.setParentId(req.getDropNode().getParentId());
        //            List<ProjectModuleConfigResp> list = baseService.list(query,null);
        //            list.forEach(item -> {
        //                if(item.getSort() >= req.getDropNode().getSort()){
        //                    ProjectModuleConfigReq dropNode = new ProjectModuleConfigReq();
        //                    dropNode.setSort(item.getSort() + 1);
        //                    super.update(dropNode, item.getId());
        //                }
        //            });
        //
        ////            IntStream.range(0, list.size()).forEach(index -> {
////                list.get(index).getName()
////            });
        //
        //        }

    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("project:projectModuleConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        //        baseService.deleteByIds(ids);
        ProjectModuleConfigReq req = new ProjectModuleConfigReq();
        ids.forEach(id -> {
            req.setDelFlag(StatusTypeEnum.ABNORMAL);
            super.update(req, id);
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("project:projectModuleConfig:export")
    @GetMapping("/export")
    public void export(@Validated ProjectModuleConfigQuery query,
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
                List<ProjectModuleConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", ProjectModuleConfigDetailResp.class, response);
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
