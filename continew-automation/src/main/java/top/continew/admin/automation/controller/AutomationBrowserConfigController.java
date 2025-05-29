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
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.automation.model.query.AutomationBrowserConfigQuery;
import top.continew.admin.automation.model.req.AutomationBrowserConfigReq;
import top.continew.admin.automation.model.resp.AutomationBrowserConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationBrowserConfigResp;
import top.continew.admin.automation.service.AutomationBrowserConfigService;

import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;

/**
 * 自动化管理-浏览器配置管理 API
 *
 * @author hagyao520
 * @since 2025/05/29 15:41
 */
@Tag(name = "自动化管理-浏览器配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationBrowserConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class AutomationBrowserConfigController extends BaseController<AutomationBrowserConfigService, AutomationBrowserConfigResp, AutomationBrowserConfigDetailResp, AutomationBrowserConfigQuery, AutomationBrowserConfigReq> {
    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("automation:automationBrowserConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody AutomationBrowserConfigReq req) {
        Object[] param = new Object[]{req.getType(), req.getName(), req.getVersion()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，自动化管理-浏览器配置 [{}] 已存在", param[1]);
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationBrowserConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationBrowserConfigReq req, @PathVariable("id") Long id) {
        Object[] param = new Object[]{req.getType(), req.getName(), req.getVersion()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-浏览器配置 [{}] 已存在", param[1]);
        super.update(req, id);
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationBrowserConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
//        baseService.deleteByIds(ids);
        AutomationBrowserConfigReq req = new AutomationBrowserConfigReq();
        ids.forEach(id -> {
            req.setDelFlag(StatusTypeEnum.ABNORMAL);
            super.update(req, id);
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:AutomationBrowserConfig:export")
    @GetMapping("/export")
    public void export(@Validated AutomationBrowserConfigQuery query, @Validated SortQuery sortQuery, HttpServletResponse response) {
        try {
            String idStr = Objects.requireNonNull(query.getId(), "ID string is null");
            List<Long> ids = Arrays.stream(idStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
            if (!ids.isEmpty() && query.getName().equals("批量选择导出")) {
                List<AutomationBrowserConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", AutomationBrowserConfigDetailResp.class, response);
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