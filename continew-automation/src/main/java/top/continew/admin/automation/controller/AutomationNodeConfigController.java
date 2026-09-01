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

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import top.continew.admin.automation.service.AutomationEnvironmentConfigService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.model.query.AutomationNodeConfigQuery;
import top.continew.admin.automation.model.req.AutomationNodeConfigReq;
import top.continew.admin.automation.model.resp.AutomationNodeConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationNodeConfigResp;
import top.continew.admin.automation.service.AutomationNodeConfigService;

import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;
import top.continew.starter.web.model.R;

/**
 * 自动化管理-节点配置管理 API
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Tag(name = "自动化管理-节点配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationNodeConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE,
    Api.DELETE, Api.EXPORT})
public class AutomationNodeConfigController extends BaseController<AutomationNodeConfigService, AutomationNodeConfigResp, AutomationNodeConfigDetailResp, AutomationNodeConfigQuery, AutomationNodeConfigReq> {

    private final AutomationEnvironmentConfigService automationEnvironmentConfigService;

    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("automation:automationNodeConfig:list")
    @GetMapping("/list")
    public List<AutomationNodeConfigResp> list(@Validated AutomationNodeConfigQuery query,
                                               @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("automation:automationNodeConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody AutomationNodeConfigReq req) {
        Object[] param = new Object[] {req.getName()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，自动化管理-节点配置 [{}] 已存在", param[0]);
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationNodeConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationNodeConfigReq req,
                       @PathVariable("id") Long id) {
        Object[] param = new Object[] {req.getName()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-节点配置 [{}] 已存在", param[0]);
        super.update(req, id);
    }

    @Operation(summary = "添加节点配置", description = "添加节点配置")
    @SaCheckPermission("automation:automationNodeConfig:create")
    @PostMapping("/addNode")
    public R addNode(@RequestBody AutomationNodeConfigDO automationNodeConfigDO) {
        return baseService.addNode(automationNodeConfigDO)
            ? R.ok("添加成功", null)
            : R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "添加失败，节点已存在或配置信息错误，请检查后重试！");
    }

    @Operation(summary = "修改节点配置", description = "修改节点配置")
    @SaCheckPermission("automation:automationNodeConfig:update")
    @PostMapping("/updateNode")
    public R updateNode(@RequestBody AutomationNodeConfigDO automationNodeConfigDO) {
        return baseService.updateNode(automationNodeConfigDO)
            ? R.ok("修改成功", null)
            : R.fail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "修改失败，节点已存在或配置信息错误，请检查后重试！");
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationNodeConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        //        baseService.deleteByIds(ids);
        AutomationNodeConfigReq req = new AutomationNodeConfigReq();
        ids.forEach(id -> {
            // 删除Jenkins远程节点
            AutomationNodeConfigDetailResp automationNodeConfigDetailResp = baseService.get(id);
            if (automationEnvironmentConfigService.updateNodeConfig("delete", id)) {
                if (baseService.delNode(automationNodeConfigDetailResp)) {
                    req.setDelFlag(StatusTypeEnum.ABNORMAL);
                    super.update(req, id);
                } else {
                    throw new BusinessException("【" + automationNodeConfigDetailResp.getName() + "】删除失败");
                }
            } else {
                throw new IllegalArgumentException("删除失败，自动化环境关联节点配置信息异常：" + id);
            }
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationNodeConfig:export")
    @GetMapping("/export")
    public void export(@Validated AutomationNodeConfigQuery query,
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
                List<AutomationNodeConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", AutomationNodeConfigDetailResp.class, response);
            } else {
                throw new IllegalArgumentException("No valid IDs provided");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID format", e);
        } catch (Exception e) {
            baseService.export(query, sortQuery, response);
        }
    }

    @Operation(summary = "同步所有节点", description = "同步所有节点")
    @Parameter(name = "jenkinsId", description = "jenkinsId", example = "1", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationNodeConfig:sync")
    @GetMapping("/syncAllNode/{jenkinsId}")
    public R syncAllNode(@PathVariable Long jenkinsId) {
        return baseService.syncAllNode(jenkinsId)
            ? R.ok("同步成功", null)
            : R.fail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "同步失败");
    }

    @Operation(summary = "同步单个节点", description = "根据ID列表同步单个节点")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationNodeConfig:sync")
    @GetMapping("/syncNode/{ids}")
    public R syncNode(@PathVariable List<Long> ids) {
        return baseService.syncNode(ids)
            ? R.ok("同步成功", null)
            : R.fail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "同步失败");
    }
}
