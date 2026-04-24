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
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.test.model.query.TestPlanQuery;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestPlanReq;
import top.continew.admin.test.model.req.TestPlanSceneRelationReq;
import top.continew.admin.test.model.resp.TestPlanDetailResp;
import top.continew.admin.test.model.resp.TestPlanResp;
import top.continew.admin.test.service.TestPlanService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;
import top.continew.starter.web.model.R;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Tag(name = "测试计划管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/test/testPlan", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class TestPlanController extends BaseController<TestPlanService, TestPlanResp, TestPlanDetailResp, TestPlanQuery, TestPlanReq> {

    @Override
    @SaCheckPermission("test:testPlan:list")
    @GetMapping("/list")
    public List<TestPlanResp> list(@Validated TestPlanQuery query, @Validated SortQuery sortQuery) {
        return super.list(query, sortQuery);
    }

    @Override
    @SaCheckPermission("test:testPlan:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody TestPlanReq req) {
        CheckUtils.throwIf(baseService.isExists(req.getName(), req.getProjectId(), null), "测试计划名称已存在");
        return super.create(req);
    }

    @Override
    @SaCheckPermission("test:testPlan:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody TestPlanReq req, @PathVariable("id") Long id) {
        CheckUtils.throwIf(baseService.isExists(req.getName(), req.getProjectId(), id), "测试计划名称已存在");
        super.update(req, id);
    }

    @Operation(summary = "删除测试计划")
    @Parameter(name = "ids", description = "逗号分隔的 ID 列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("test:testPlan:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        baseService.deleteByIds(ids);
    }

    @Operation(summary = "导出测试计划")
    @SaCheckPermission("test:testPlan:export")
    @GetMapping("/export")
    public void export(@Validated TestPlanQuery query, @Validated SortQuery sortQuery, HttpServletResponse response) {
        try {
            String idStr = String.valueOf(Objects.requireNonNull(query.getId(), "ID string is null"));
            List<Long> ids = Arrays.stream(idStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
            if (!ids.isEmpty()) {
                ExcelUtils.export(baseService.selectByIds(ids), "测试计划", TestPlanDetailResp.class, response);
                return;
            }
        } catch (Exception ignored) {
        }
        baseService.export(query, sortQuery, response);
    }

    @Operation(summary = "关联 UI 自动化场景")
    @SaCheckPermission("test:testPlan:relateScene")
    @PostMapping("/{id}/relateScenes")
    public void relateScenes(@PathVariable Long id, @Validated @RequestBody TestPlanSceneRelationReq req) {
        baseService.relateScenes(id, req);
    }

    @Operation(summary = "移除 UI 自动化场景")
    @SaCheckPermission("test:testPlan:relateScene")
    @PostMapping("/{id}/removeScenes")
    public void removeScenes(@PathVariable Long id, @Validated @RequestBody TestPlanSceneRelationReq req) {
        baseService.removeScenes(id, req);
    }

    @Operation(summary = "执行测试计划")
    @SaCheckPermission("test:testPlan:execute")
    @PostMapping("/{id}/execute")
    public R<AutomationUiSceneExecResp> execute(@PathVariable Long id, @Validated @RequestBody TestPlanExecuteReq req) {
        return R.ok(baseService.execute(id, req));
    }
}
