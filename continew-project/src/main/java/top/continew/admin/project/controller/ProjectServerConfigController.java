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

import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.project.model.query.ProjectServerConfigQuery;
import top.continew.admin.project.model.req.ProjectServerConfigReq;
import top.continew.admin.project.model.resp.ProjectServerConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectServerConfigResp;
import top.continew.admin.project.service.ProjectServerConfigService;

import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;

/**
 * 项目管理-服务器配置管理 API
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Tag(name = "项目管理-服务器配置管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/project/projectServerConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class ProjectServerConfigController extends BaseController<ProjectServerConfigService, ProjectServerConfigResp, ProjectServerConfigDetailResp, ProjectServerConfigQuery, ProjectServerConfigReq> {
    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("project:ProjectServerConfig:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody ProjectServerConfigReq req) {
        Object projectId = req.getProjectId();
        Object ip = req.getIp();
        Object port = req.getPort();
        CheckUtils.throwIf(baseService.isExists(null, projectId, ip, port), "新增失败，该项目环境下服务器配置 [{}] 已存在", projectId, ip, port);
        return super.create(req);
    }

    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("project:ProjectServerConfig:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody ProjectServerConfigReq req, @PathVariable("id") Long id) {
        Object projectId = req.getProjectId();
        Object ip = req.getIp();
        Object port = req.getPort();
        CheckUtils.throwIf(baseService.isExists(id, projectId, ip, port), "修改失败，该项目环境下服务器配置 [{}] 已存在", projectId, ip, port);
        super.update(req, id);
    }

    @Operation(summary = "删除数据", description = "根据ID列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("project:ProjectServerConfig:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
//        baseService.deleteByIds(ids);
        ProjectServerConfigReq req = new ProjectServerConfigReq();
        ids.forEach(id -> {
            req.setDelFlag(0);
            super.update(req, id);
        });
    }

    @Operation(summary = "导出数据", description = "根据ID列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的ID列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("project:ProjectServerConfig:export")
    @GetMapping("/export")
    public void export(@Validated ProjectServerConfigQuery query, @Validated SortQuery sortQuery, HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
            if (!ids.isEmpty() && query.getIp().equals("批量选择导出")) {
                List<ProjectServerConfigDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", ProjectServerConfigDetailResp.class, response);
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