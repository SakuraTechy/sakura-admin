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
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import top.continew.admin.automation.service.AutomationEnvironmentConfigService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.automation.model.query.AutomationJenkinsConfigQuery;
import top.continew.admin.automation.model.req.AutomationJenkinsConfigReq;
import top.continew.admin.automation.model.resp.AutomationJenkinsConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationJenkinsConfigResp;
import top.continew.admin.automation.service.AutomationJenkinsConfigService;

import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;

/**
 * 自动化管理-Jenkins配置管理 API
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Tag(name = "自动化管理-Jenkins配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationJenkinsConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE,
    Api.DELETE, Api.EXPORT})
public class AutomationJenkinsConfigController extends BaseController<AutomationJenkinsConfigService, AutomationJenkinsConfigResp, AutomationJenkinsConfigDetailResp, AutomationJenkinsConfigQuery, AutomationJenkinsConfigReq> {

    private final AutomationEnvironmentConfigService automationEnvironmentConfigService;

    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("automation:automationJenkinsConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody AutomationJenkinsConfigReq req) {
        Object[] param = new Object[] {req.getIp(), req.getPort()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，自动化管理-Jenkins配置 [{}] 已存在", param[0]);
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationJenkinsConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationJenkinsConfigReq req,
                       @PathVariable("id") Long id) {
        Object[] param = new Object[] {req.getIp(), req.getPort()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-Jenkins配置 [{}] 已存在", param[0]);
        super.update(req, id);
        if (!automationEnvironmentConfigService.updateJenkinsConfig("update", id)) {
            throw new IllegalArgumentException("修改失败，自动化环境关联Jenkins配置信息异常");
        }
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationJenkinsConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        //        baseService.deleteByIds(ids);
        AutomationJenkinsConfigReq req = new AutomationJenkinsConfigReq();
        ids.forEach(id -> {
            if (automationEnvironmentConfigService.updateJenkinsConfig("delete", id)) {
                req.setDelFlag(StatusTypeEnum.ABNORMAL);
                super.update(req, id);
            } else {
                throw new IllegalArgumentException("删除失败，自动化环境关联Jenkins配置信息异常");
            }
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationJenkinsConfig:export")
    @GetMapping("/export")
    public void export(@Validated AutomationJenkinsConfigQuery query,
                       @Validated SortQuery sortQuery,
                       HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
            if (!ids.isEmpty() && query.getIp().equals("批量选择导出")) {
                List<AutomationJenkinsConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", AutomationJenkinsConfigDetailResp.class, response);
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
