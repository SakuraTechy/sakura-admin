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

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.automation.model.query.AutomationEnvironmentConfigQuery;
import top.continew.admin.automation.model.req.AutomationEnvironmentConfigReq;
import top.continew.admin.automation.model.resp.AutomationEnvironmentConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationEnvironmentConfigResp;
import top.continew.admin.automation.service.AutomationEnvironmentConfigService;

import top.continew.starter.file.excel.util.ExcelUtils;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;

/**
 * 自动化管理-环境配置管理 API
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Tag(name = "自动化管理-环境配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationEnvironmentConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class AutomationEnvironmentConfigController extends BaseController<AutomationEnvironmentConfigService, AutomationEnvironmentConfigResp, AutomationEnvironmentConfigDetailResp, AutomationEnvironmentConfigQuery, AutomationEnvironmentConfigReq> {

    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("automation:projectEnvironmentConfig:list")
    @GetMapping("/list")
    public List<AutomationEnvironmentConfigResp> list(@Validated AutomationEnvironmentConfigQuery query, @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("automation:automationEnvironmentConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody AutomationEnvironmentConfigReq req) {
        Object[] param = new Object[]{req.getType(), req.getName()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，自动化管理-环境配置 [{}] 已存在", param[1]);
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationEnvironmentConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationEnvironmentConfigReq req, @PathVariable("id") Long id) {
        Object[] param = new Object[]{req.getType(), req.getName()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-环境配置 [{}] 已存在", param[1]);
        super.update(req, id);
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationEnvironmentConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
//        baseService.deleteByIds(ids);
        AutomationEnvironmentConfigReq req = new AutomationEnvironmentConfigReq();
        ids.forEach(id -> {
            req.setDelFlag(StatusTypeEnum.ABNORMAL);
            super.update(req, id);
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:AutomationEnvironmentConfig:export")
    @GetMapping("/export")
    public void export(@Validated AutomationEnvironmentConfigQuery query, @Validated SortQuery sortQuery, HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
            if (!ids.isEmpty() && query.getName().equals("批量选择导出")) {
                List<AutomationEnvironmentConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", AutomationEnvironmentConfigDetailResp.class, response);
            }else{
                throw new IllegalArgumentException("No valid IDs provided");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID format", e);
        } catch (Exception e) {
            baseService.export(query, sortQuery, response);
        }
    }
}