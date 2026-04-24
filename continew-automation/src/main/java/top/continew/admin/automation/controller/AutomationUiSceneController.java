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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.lang.tree.Tree;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiSceneClearReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecAllReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecReq;
import top.continew.admin.automation.model.req.AutomationUiSceneReq;
import top.continew.admin.automation.model.req.AutomationUiSceneUploadResultReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneResp;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.SortQuery;
import top.continew.starter.extension.crud.model.resp.BaseIdResp;
import top.continew.starter.extension.crud.validation.CrudValidationGroup;
import top.continew.starter.file.excel.util.ExcelUtils;
import top.continew.starter.web.model.R;

/**
 * 自动化管理-UI 自动化场景管理 API。
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Tag(name = "自动化管理-UI 自动化场景管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationUiScene", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class AutomationUiSceneController extends BaseController<AutomationUiSceneService, AutomationUiSceneResp, AutomationUiSceneDetailResp, AutomationUiSceneQuery, AutomationUiSceneReq> {

    /**
     * 查询场景列表。
     *
     * @param query 查询条件
     * @param sortQuery 排序条件
     * @return 场景列表
     */
    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("automation:automationUiScene:list")
    @GetMapping("/list")
    public List<AutomationUiSceneResp> list(@Validated AutomationUiSceneQuery query, @Validated SortQuery sortQuery) {
        List<AutomationUiSceneResp> result = super.list(query, sortQuery);
        result.forEach(this::normalizeSceneResp);
        return result;
    }

    /**
     * 新增场景。
     *
     * @param req 请求参数
     * @return 新增结果
     */
    @Override
    @Operation(summary = "新增数据", description = "新增数据")
    @SaCheckPermission("automation:automationUiScene:create")
    public BaseIdResp<Long> create(@Validated(CrudValidationGroup.Create.class) @RequestBody AutomationUiSceneReq req) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(null, param), "新增失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        return super.create(req);
    }

    /**
     * 修改场景。
     *
     * @param req 请求参数
     * @param id 场景 ID
     */
    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationUiScene:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationUiSceneReq req, @PathVariable("id") Long id) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        super.update(req, id);
    }

    /**
     * 根据源场景复制数据。
     *
     * @param id 源场景 ID
     * @param req 新场景参数
     * @return 新场景 ID
     */
    @Operation(summary = "复制数据", description = "根据源场景复制数据")
    @SaCheckPermission("automation:automationUiScene:copy")
    @PostMapping("/{id}/copy")
    public BaseIdResp<Long> copy(@PathVariable("id") Long id,
                                 @Validated(CrudValidationGroup.Create.class) @RequestBody AutomationUiSceneReq req) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(null, param), "复制失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        return new BaseIdResp<>(baseService.copy(id, req));
    }

    /**
     * 删除场景。
     *
     * @param ids 场景 ID 列表
     */
    @Operation(summary = "删除数据", description = "根据 ID 列表删除数据")
    @Parameter(name = "ids", description = "逗号分隔的 ID 列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:automationUiScene:delete")
    @DeleteMapping("/{ids}")
    public void delete(@PathVariable List<Long> ids) {
        AutomationUiSceneReq req = new AutomationUiSceneReq();
        ids.forEach(id -> {
            req.setDelFlag(StatusTypeEnum.ABNORMAL);
            super.update(req, id);
        });
    }

    /**
     * 导出场景数据。
     *
     * @param query 查询条件
     * @param sortQuery 排序条件
     * @param response HTTP 响应
     */
    @Operation(summary = "导出数据", description = "根据 ID 列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的 ID 列表", example = "1,2", in = ParameterIn.PATH)
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
            if (!ids.isEmpty() && "批量选择导出".equals(query.getName())) {
                List<AutomationUiSceneDetailResp> list = baseService.selectByIds(ids);
                ExcelUtils.export(list, "导出数据", AutomationUiSceneDetailResp.class, response);
                return;
            }
            throw new IllegalArgumentException("No valid IDs provided");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID format", e);
        } catch (Exception e) {
            baseService.export(query, sortQuery, response);
        }
    }

    /**
     * 查询场景用例树。
     *
     * @param query 查询条件
     * @param sortQuery 排序条件
     * @return 场景树
     */
    @Operation(summary = "查询场景用例树", description = "查询场景用例树")
    @SaCheckPermission("automation:automationUiScene:getCase")
    @GetMapping("/getCaseTree")
    public List<Tree<Long>> getCaseTree(AutomationUiSceneQuery query, SortQuery sortQuery) {
        return baseService.tree(query, sortQuery, true);
    }

    /**
     * 添加场景用例。
     *
     * @param caseDO 用例参数
     * @param id 场景 ID
     */
    @Operation(summary = "添加场景用例", description = "添加场景用例")
    @SaCheckPermission("automation:automationUiScene:addCase")
    @PutMapping("/{id}/addCase")
    public void addCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.addCase(caseDO, id);
    }

    /**
     * 修改场景用例。
     *
     * @param caseDO 用例参数
     * @param id 场景 ID
     */
    @Operation(summary = "修改场景用例", description = "修改场景用例")
    @SaCheckPermission("automation:automationUiScene:updateCase")
    @PutMapping("/{id}/updateCase")
    public void updateCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.updateCase(caseDO, id);
    }

    /**
     * 删除场景用例。
     *
     * @param caseDO 用例参数
     * @param id 场景 ID
     */
    @Operation(summary = "删除场景用例", description = "删除场景用例")
    @SaCheckPermission("automation:automationUiScene:deleteCase")
    @PutMapping("/{id}/deleteCase")
    public void deleteCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.deleteCase(caseDO, id);
    }

    /**
     * 拖拽场景用例。
     *
     * @param caseDO 用例参数
     * @param id 场景 ID
     */
    @Operation(summary = "拖拽场景用例", description = "拖拽场景用例")
    @SaCheckPermission("automation:automationUiScene:dragCase")
    @PutMapping("/{id}/dragCase")
    public void dragCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO, @PathVariable("id") Long id) {
        baseService.dragCase(caseDO, id);
    }

    /**
     * 添加场景步骤。
     *
     * @param stepDO 步骤参数
     * @param id 场景 ID
     * @return 步骤 ID
     */
    @Operation(summary = "添加场景用例步骤", description = "添加场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:addStep")
    @PutMapping("/{id}/addStep")
    public R<String> addStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        String stepId = baseService.addStep(stepDO, id);
        return R.ok(stepId);
    }

    /**
     * 修改场景步骤。
     *
     * @param stepDO 步骤参数
     * @param id 场景 ID
     */
    @Operation(summary = "修改场景用例步骤", description = "修改场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:updateStep")
    @PutMapping("/{id}/updateStep")
    public void updateStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        baseService.updateStep(stepDO, id);
    }

    /**
     * 删除场景步骤。
     *
     * @param stepDO 步骤参数
     * @param id 场景 ID
     */
    @Operation(summary = "删除场景用例步骤", description = "删除场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:deleteStep")
    @PutMapping("/{id}/deleteStep")
    public void deleteStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        baseService.deleteStep(stepDO, id);
    }

    /**
     * 拖拽场景步骤。
     *
     * @param stepDO 步骤参数
     * @param id 场景 ID
     */
    @Operation(summary = "拖拽场景用例步骤", description = "拖拽场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:dragStep")
    @PutMapping("/{id}/dragStep")
    public void dragStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO, @PathVariable("id") Long id) {
        baseService.dragStep(stepDO, id);
    }

    /**
     * 执行指定场景。
     *
     * @param req 执行参数
     * @return 执行结果
     */
    @Operation(summary = "执行场景", description = "执行选中的 UI 自动化场景")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PostMapping("/exec")
    public R<AutomationUiSceneExecResp> exec(@Validated @RequestBody AutomationUiSceneExecReq req) {
        return R.ok(baseService.exec(req));
    }

    /**
     * 执行当前查询范围内全部场景。
     *
     * @param req 执行参数
     * @return 执行结果
     */
    @Operation(summary = "执行全部场景", description = "执行当前查询范围内全部 UI 自动化场景")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PostMapping("/execAll")
    public R<AutomationUiSceneExecResp> execAll(@Validated @RequestBody AutomationUiSceneExecAllReq req) {
        return R.ok(baseService.execAll(req));
    }

    /**
     * 根据 ID 列表查询场景。
     *
     * @param ids 场景 ID 列表
     * @return 场景列表
     */
    @Operation(summary = "查询选中场景", description = "根据 ID 列表查询场景")
    @SaCheckPermission("automation:automationUiScene:list")
    @PostMapping("/selected")
    public R<List<AutomationUiSceneResp>> selected(@RequestBody List<Long> ids) {
        Collection<Long> targetIds = ids == null ? new ArrayList<>() : ids;
        List<AutomationUiSceneResp> sceneList = baseService.listSceneRespByIds(targetIds);
        sceneList.forEach(this::normalizeSceneResp);
        return R.ok(sceneList);
    }

    /**
     * 导出指定场景 XML。
     *
     * @param ids 场景 ID 列表
     * @param response HTTP 响应
     */
    @Operation(summary = "导出场景 XML", description = "导出选中场景 XML")
    @SaCheckPermission("automation:automationUiScene:export")
    @GetMapping("/exportXml/{ids}")
    public void exportXml(@PathVariable List<Long> ids, HttpServletResponse response) {
        baseService.exportXml(ids, response);
    }

    /**
     * 导出当前查询范围内全部场景 XML。
     *
     * @param query 查询条件
     * @param response HTTP 响应
     */
    @Operation(summary = "导出全部场景 XML", description = "导出当前查询范围内全部场景 XML")
    @SaCheckPermission("automation:automationUiScene:export")
    @GetMapping("/exportXmlAll")
    public void exportXmlAll(@Validated AutomationUiSceneQuery query, HttpServletResponse response) {
        baseService.exportXmlAll(query, response);
    }

    /**
     * 清空执行结果。
     *
     * @param req 清理参数
     */
    @Operation(summary = "清空执行结果", description = "清空选中场景执行结果")
    @SaCheckPermission("automation:automationUiScene:update")
    @PutMapping("/clearResults")
    public void clearResults(@Validated @RequestBody AutomationUiSceneClearReq req) {
        baseService.clearResults(req);
    }

    /**
     * 接收执行结果回调。
     *
     * @param req 回调参数
     * @return 响应结果
     */
    @SaIgnore
    @Operation(summary = "上传执行结果", description = "接收 UI 自动化场景执行结果回调")
    @PutMapping("/uploadResults")
    public R<Void> uploadResults(@Validated @RequestBody AutomationUiSceneUploadResultReq req) {
        baseService.uploadResults(req);
        return R.ok();
    }

    /**
     * 归一化场景展示字段。
     *
     * @param scene 场景响应
     */
    private void normalizeSceneResp(AutomationUiSceneResp scene) {
        if (scene == null) {
            return;
        }
        scene.setExecuteStatus(toDisplayStatus(scene.getExecuteStatus()));
        scene.setExecuteResult(toDisplayResult(scene.getExecuteResult()));
        scene.setLastResult(toDisplayResult(scene.getLastResult()));
        scene.setDebugRecord(normalizeRecordHistory(scene.getDebugRecord()));
        scene.setTestRecord(normalizeRecordHistory(scene.getTestRecord()));
    }

    /**
     * 归一化历史记录中的状态与结果。
     *
     * @param history 历史记录
     * @return 归一化后的历史记录
     */
    private List<Object> normalizeRecordHistory(List<Object> history) {
        if (history == null || history.isEmpty()) {
            return history;
        }
        List<Object> normalized = new ArrayList<>(history.size());
        for (Object item : history) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    record.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                record.computeIfPresent("executeStatus", (key, value) -> toDisplayStatus(String.valueOf(value)));
                record.computeIfPresent("executeResult", (key, value) -> toDisplayResult(String.valueOf(value)));
                normalized.add(record);
            } else {
                normalized.add(item);
            }
        }
        return normalized;
    }

    /**
     * 转换执行状态展示值。
     *
     * @param value 原始状态
     * @return 展示状态
     */
    private String toDisplayStatus(String value) {
        if ("RUNNING".equalsIgnoreCase(value)) {
            return "进行中";
        }
        if ("COMPLETED".equalsIgnoreCase(value)) {
            return "已完成";
        }
        if ("NOT_STARTED".equalsIgnoreCase(value)) {
            return "未开始";
        }
        return value;
    }

    /**
     * 转换执行结果展示值。
     *
     * @param value 原始结果
     * @return 展示结果
     */
    private String toDisplayResult(String value) {
        if ("PASSED".equalsIgnoreCase(value)) {
            return "全部通过";
        }
        if ("FAILED".equalsIgnoreCase(value)) {
            return "不通过";
        }
        if ("SKIPPED".equalsIgnoreCase(value)) {
            return "跳过";
        }
        if ("RUNNING".equalsIgnoreCase(value)) {
            return "-";
        }
        if ("NOT_EXECUTED".equalsIgnoreCase(value)) {
            return "未执行";
        }
        return value;
    }
}
