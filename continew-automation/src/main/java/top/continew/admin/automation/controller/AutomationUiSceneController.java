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

package top.continew.admin.automation.controller;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

import cn.hutool.core.lang.tree.Tree;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiSceneReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneResp;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;
import top.continew.starter.web.model.R;

/**
 * 自动化管理-UI自动化场景管理 API
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Tag(name = "自动化管理-UI自动化场景管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationUiScene", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class AutomationUiSceneController extends BaseController<AutomationUiSceneService, AutomationUiSceneResp, AutomationUiSceneDetailResp, AutomationUiSceneQuery, AutomationUiSceneReq> {

    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("automation:automationUiScene:list")
    @GetMapping("/list")
    public List<AutomationUiSceneResp> list(@Validated AutomationUiSceneQuery query, @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("automation:automationUiScene:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody AutomationUiSceneReq req) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationUiScene:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationUiSceneReq req, @PathVariable("id") Long id) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        super.update(req, id);
    }

    @Operation(summary = "复制数据", description = "根据源场景复制数据")
    @SaCheckPermission("automation:automationUiScene:copy")
    @PostMapping("/{id}/copy")
    public BaseIdResp<Long> copy(@PathVariable("id") Long id,
                                 @Validated(CrudValidationGroup.Create.class) @RequestBody AutomationUiSceneReq req) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(null, param), "复制失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        return new BaseIdResp<>(baseService.copy(id, req));
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationUiScene:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        //        baseService.deleteByIds(ids);
        AutomationUiSceneReq req = new AutomationUiSceneReq();
        ids.forEach(id -> {
            req.setDelFlag(StatusTypeEnum.ABNORMAL);
            super.update(req, id);
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:AutomationUiScene:export")
    @GetMapping("/export")
    public void export(@Validated AutomationUiSceneQuery query, @Validated SortQuery sortQuery, HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
            if (!ids.isEmpty() && query.getName().equals("批量选择导出")) {
                List<AutomationUiSceneDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", AutomationUiSceneDetailResp.class, response);
            } else {
                throw new IllegalArgumentException("No valid IDs provided");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID format", e);
        } catch (Exception e) {
            baseService.export(query, sortQuery, response);
        }
    }

    @Operation(summary = "查询场景用例树", description = "查询场景用例树")
    @SaCheckPermission("automation:automationUiScene:getCase")
    @GetMapping("/getCaseTree")
    public List<Tree<Long>> getCaseTree(AutomationUiSceneQuery query, SortQuery sortQuery) {
        return baseService.tree(query, sortQuery, true);
    }

    @Operation(summary = "添加场景用例", description = "添加场景用例")
    @SaCheckPermission("automation:automationUiScene:addCase")
    @PutMapping("/{id}/addCase")
    public void addCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.addCase(caseDO, id);
    }

    @Operation(summary = "修改场景用例", description = "修改场景用例")
    @SaCheckPermission("automation:automationUiScene:updateCase")
    @PutMapping("/{id}/updateCase")
    public void updateCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.updateCase(caseDO, id);
    }

    @Operation(summary = "删除场景用例", description = "删除场景用例")
    @SaCheckPermission("automation:automationUiScene:deleteCase")
    @PutMapping("/{id}/deleteCase")
    public void deleteCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.deleteCase(caseDO, id);
    }

    @Operation(summary = "拖拽场景用例", description = "拖拽场景用例")
    @SaCheckPermission("automation:automationUiScene:dragCase")
    @PutMapping("/{id}/dragCase")
    public void dragCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.dragCase(caseDO, id);
    }

    @Operation(summary = "添加场景用例步骤", description = "添加场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:addStep")
    @PutMapping("/{id}/addStep")
    public R addStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        String stepId = baseService.addStep(stepDO, id);
        return R.ok(stepId);
    }

    @Operation(summary = "修改场景用例步骤", description = "修改场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:updateStep")
    @PutMapping("/{id}/updateStep")
    public void updateStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        baseService.updateStep(stepDO, id);
    }

    @Operation(summary = "删除场景用例步骤", description = "删除场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:deleteStep")
    @PutMapping("/{id}/deleteStep")
    public void deleteStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        baseService.deleteStep(stepDO, id);
    }

    @Operation(summary = "拖拽场景用例步骤", description = "拖拽场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:dragStep")
    @PutMapping("/{id}/dragStep")
    public void dragStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        baseService.dragStep(stepDO, id);
    }
}