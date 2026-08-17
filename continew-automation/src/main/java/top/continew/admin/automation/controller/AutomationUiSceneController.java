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
import java.util.List;
import java.util.Objects;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiSceneClearReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecAllReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecReq;
import top.continew.admin.automation.model.req.AutomationUiSceneReq;
import top.continew.admin.automation.model.req.AutomationUiSceneUploadResultReq;
import top.continew.admin.automation.model.req.AutomationUiTreeCopyReq;
import top.continew.admin.automation.model.req.AutomationUiTreeDeleteReq;
import top.continew.admin.automation.model.req.AutomationUiTreeMoveReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneRevisionResp;
import top.continew.admin.automation.model.resp.AutomationUiTreeMutationResp;
import top.continew.admin.automation.model.req.ui.AutomationUiCaseEditReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepEditReq;
import top.continew.admin.automation.model.resp.ui.AutomationUiCaseDetailResp;
import top.continew.admin.automation.model.resp.ui.AutomationCertificateUploadResp;
import top.continew.admin.automation.model.resp.ui.AutomationUiStepDetailResp;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService.CertificateFile;
import top.continew.admin.automation.service.AutomationUiCaseTreeService;
import top.continew.admin.automation.service.AutomationUiCaseDetailService;
import top.continew.admin.automation.service.AutomationUiSceneDefinitionScanService;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.automation.model.resp.AutomationUiSceneDefinitionScanResp;
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
 * 自动化管理-UI 自动化场景管理 API
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Tag(name = "自动化管理-UI 自动化场景管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/automation/automationUiScene", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE,
    Api.DELETE, Api.EXPORT})
public class AutomationUiSceneController extends BaseController<AutomationUiSceneService, AutomationUiSceneResp, AutomationUiSceneDetailResp, AutomationUiSceneQuery, AutomationUiSceneReq> {

    private final AutomationUiCaseTreeService caseTreeService;
    private final AutomationUiCaseDetailService caseDetailService;
    private final AutomationUiSceneDefinitionScanService definitionScanService;
    private final AutomationCertificateWorkspaceService certificateWorkspaceService;

    @Operation(summary = "上传证书到 Playwright Runner 工作区")
    @SaCheckPermission("automation:automationUiScene:updateStep")
    @PostMapping("/{sceneDbId}/certificate-files")
    public R<AutomationCertificateUploadResp> uploadCertificate(@PathVariable Long sceneDbId,
                                                                @RequestParam("file") MultipartFile file) {
        CertificateFile uploaded = certificateWorkspaceService.upload(sceneDbId, file);
        return R.ok(AutomationCertificateUploadResp.builder()
            .reference(uploaded.reference())
            .fileName(uploaded.fileName())
            .size(uploaded.size())
            .build());
    }

    @Operation(summary = "查询用例详情 DTO", description = "返回统一运行配置、来源和只读步骤诊断")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{sceneDbId}/cases/{caseId}/detail")
    public R<AutomationUiCaseDetailResp> getCaseDetail(@PathVariable Long sceneDbId, @PathVariable String caseId) {
        return R.ok(caseDetailService.getCaseDetail(sceneDbId, caseId));
    }

    @Operation(summary = "修改用例详情 DTO")
    @SaCheckPermission("automation:automationUiScene:updateCase")
    @PutMapping("/{sceneDbId}/cases/{caseId}")
    public R<AutomationUiCaseDetailResp> updateCaseDetail(@PathVariable Long sceneDbId,
                                                          @PathVariable String caseId,
                                                          @Validated @RequestBody AutomationUiCaseEditReq request) {
        request.setId(caseId);
        return R.ok(caseDetailService.updateCase(sceneDbId, request));
    }

    @Operation(summary = "查询步骤详情 DTO")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{sceneDbId}/cases/{caseId}/steps/{stepId}/detail")
    public R<AutomationUiStepDetailResp> getStepDetail(@PathVariable Long sceneDbId,
                                                       @PathVariable String caseId,
                                                       @PathVariable String stepId) {
        return R.ok(caseDetailService.getStepDetail(sceneDbId, caseId, stepId));
    }

    @Operation(summary = "修改步骤语义 DTO")
    @SaCheckPermission("automation:automationUiScene:updateStep")
    @PutMapping("/{sceneDbId}/cases/{caseId}/steps/{stepId}")
    public R<AutomationUiStepDetailResp> updateStepDetail(@PathVariable Long sceneDbId,
                                                          @PathVariable String caseId,
                                                          @PathVariable String stepId,
                                                          @Validated @RequestBody AutomationUiStepEditReq request) {
        request.setPid(caseId);
        request.setId(stepId);
        return R.ok(caseDetailService.updateStep(sceneDbId, request));
    }

    @Operation(summary = "只读扫描场景定义", description = "统计历史 caseList 异常，不执行修复或写库")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/definition/scan")
    public AutomationUiSceneDefinitionScanResp scanDefinition() {
        return definitionScanService.scan();
    }

    @Override
    @Operation(summary = "查询详情", description = "展示 DTO 会脱敏录制步骤中声明为 value_masked 的值")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{id}")
    public AutomationUiSceneDetailResp get(@PathVariable("id") Long id) {
        AutomationUiSceneDetailResp detail = super.get(id);
        detail.setCaseList(maskDisplayCaseList(detail.getCaseList()));
        return detail;
    }

    /** 普通管理端只得到脱敏副本；Runner 继续从专用受控服务读取原始定义。 */
    @SuppressWarnings("unchecked")
    private List<Object> maskDisplayCaseList(List<Object> caseList) {
        if (caseList == null || caseList.isEmpty())
            return caseList;
        JSONArray cases = JSONUtil.parseArray(JSONUtil.toJsonStr(caseList));
        for (Object caseObject : cases) {
            if (!(caseObject instanceof cn.hutool.json.JSONObject caseJson))
                continue;
            normalizeDisplayStatus(caseJson);
            JSONArray steps = caseJson.getJSONArray("stepList");
            if (steps == null)
                continue;
            for (Object stepObject : steps) {
                if (!(stepObject instanceof cn.hutool.json.JSONObject stepJson))
                    continue;
                normalizeDisplayStatus(stepJson);
                JSONArray configs = stepJson.getJSONArray("configList");
                if (!isMasked(configs))
                    continue;
                stepJson.set("operationValue", "******");
                for (Object configObject : configs) {
                    if (!(configObject instanceof cn.hutool.json.JSONObject config))
                        continue;
                    String name = config.getStr("paramsName");
                    if ("value".equals(name) || "operationValue".equals(name))
                        config.set("paramsValue", "******");
                    if ("playwright_step".equals(name)) {
                        MaskedPlaywrightStep maskedStep = maskPlaywrightStep(config.getStr("paramsValue"));
                        config.set("paramsValue", maskedStep.value());
                        if (maskedStep.rawMasked())
                            stepJson.set("rawMasked", true);
                    }
                }
            }
        }
        return (List<Object>)(List<?>)JSONUtil.toList(cases, Object.class);
    }

    private void normalizeDisplayStatus(cn.hutool.json.JSONObject node) {
        Object raw = node.get("status");
        String status = raw instanceof cn.hutool.json.JSONObject statusObject
            ? statusObject.getStr("value", statusObject.getStr("code", statusObject.getStr("name")))
            : node.getStr("status");
        if ("ENABLE".equalsIgnoreCase(status) || "启用".equals(status))
            node.set("status", 1);
        else if ("DISABLE".equalsIgnoreCase(status) || "禁用".equals(status))
            node.set("status", 2);
    }

    private boolean isMasked(JSONArray configs) {
        if (configs == null)
            return false;
        return configs.stream()
            .filter(cn.hutool.json.JSONObject.class::isInstance)
            .map(cn.hutool.json.JSONObject.class::cast)
            .anyMatch(config -> "value_masked".equals(config.getStr("paramsName")) && "1".equals(config
                .getStr("paramsValue")));
    }

    private MaskedPlaywrightStep maskPlaywrightStep(String raw) {
        if (!JSONUtil.isTypeJSON(raw))
            return new MaskedPlaywrightStep("******", true);
        cn.hutool.json.JSONObject json = JSONUtil.parseObj(raw);
        json.set("value", "******");
        return new MaskedPlaywrightStep(json.toString(), false);
    }

    private record MaskedPlaywrightStep(String value, boolean rawMasked) {
    }

    /**
     * 查询场景列表
     *
     * @param query     查询条件
     * @param sortQuery 排序条件
     * @return 场景列表
     */
    @Override
    @Operation(summary = "查询数据", description = "根据查询条件查询数据")
    @SaCheckPermission("automation:automationUiScene:list")
    @GetMapping("/list")
    public List<AutomationUiSceneResp> list(@Validated AutomationUiSceneQuery query, @Validated SortQuery sortQuery) {
        // includeDefinition 只供服务端构建场景树使用，普通列表请求不得借此读取未脱敏定义。
        query.setIncludeDefinition(false);
        return super.list(query, sortQuery);
    }

    /**
     * 新增场景
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
     * 修改场景
     *
     * @param req 请求参数
     * @param id  场景 ID
     */
    @Override
    @Operation(summary = "修改数据", description = "修改数据")
    @SaCheckPermission("automation:automationUiScene:update")
    public void update(@Validated(CrudValidationGroup.Update.class) @RequestBody AutomationUiSceneReq req,
                       @PathVariable("id") Long id) {
        Object[] param = new Object[] {req.getProjectId(), req.getVersionId(), req.getSceneId()};
        CheckUtils.throwIf(baseService.isExists(id, param), "修改失败，自动化管理-UI自动化场景 [{}] 已存在", req.getSceneId());
        super.update(req, id);
    }

    /**
     * 根据源场景复制数据
     *
     * @param id  源场景 ID
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
     * 删除场景
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
     * 导出场景数据
     *
     * @param query     查询条件
     * @param sortQuery 排序条件
     * @param response  HTTP 响应
     */
    @Operation(summary = "导出数据", description = "根据 ID 列表导出数据")
    @Parameter(name = "ids", description = "逗号分隔的 ID 列表", example = "1,2", in = ParameterIn.PATH)
    @SaCheckPermission("automation:AutomationUiScene:export")
    @GetMapping("/export")
    public void export(@Validated AutomationUiSceneQuery query,
                       @Validated SortQuery sortQuery,
                       HttpServletResponse response) {
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
     * 导出指定场景 XML
     *
     * @param ids      场景 ID 列表
     * @param response HTTP 响应
     */
    @Operation(summary = "导出场景 XML", description = "导出选中场景 XML")
    @SaCheckPermission("automation:automationUiScene:export")
    @GetMapping("/exportXml/{ids}")
    public void exportXml(@PathVariable List<Long> ids, HttpServletResponse response) {
        baseService.exportXml(ids, response);
    }

    /**
     * 导出当前查询范围内全部场景 XML
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @Operation(summary = "导出全部场景 XML", description = "导出当前查询范围内全部场景 XML")
    @SaCheckPermission("automation:automationUiScene:export")
    @GetMapping("/exportXmlAll")
    public void exportXmlAll(@Validated AutomationUiSceneQuery query, HttpServletResponse response) {
        baseService.exportXmlAll(query, response);
    }

    /**
     * 查询场景用例树
     *
     * @param query     查询条件
     * @param sortQuery 排序条件
     * @return 场景树
     */
    @Operation(summary = "查询场景用例树", description = "查询场景用例树")
    @SaCheckPermission("automation:automationUiScene:getCase")
    @GetMapping("/getCaseTree")
    public List<Tree<Long>> getCaseTree(AutomationUiSceneQuery query, SortQuery sortQuery) {
        query.setIncludeDefinition(true);
        return baseService.tree(query, sortQuery, true);
    }

    @Operation(summary = "复制场景树节点", description = "只接收节点引用，服务端完成深复制")
    @PostMapping("/{sceneDbId}/caseTree/copy")
    public R<AutomationUiTreeMutationResp> copyCaseTree(@PathVariable Long sceneDbId,
                                                        @Validated @RequestBody AutomationUiTreeCopyReq req) {
        checkTreePermission(req.getSource().getType(), "addCase", "addStep");
        return R.ok(caseTreeService.copy(sceneDbId, req));
    }

    @Operation(summary = "移动场景树节点", description = "用稳定业务 ID 调整用例或步骤顺序")
    @PutMapping("/{sceneDbId}/caseTree/move")
    public R<AutomationUiTreeMutationResp> moveCaseTree(@PathVariable Long sceneDbId,
                                                        @Validated @RequestBody AutomationUiTreeMoveReq req) {
        checkTreePermission(req.getSource().getType(), "dragCase", "dragStep");
        return R.ok(caseTreeService.move(sceneDbId, req));
    }

    @Operation(summary = "删除场景树节点", description = "原子删除用例或步骤，步骤以 caseId 和 stepId 复合定位")
    @PutMapping("/{sceneDbId}/caseTree/delete")
    public R<AutomationUiTreeMutationResp> deleteCaseTree(@PathVariable Long sceneDbId,
                                                          @Validated @RequestBody AutomationUiTreeDeleteReq req) {
        boolean deleteCase = req.getNodes()
            .stream()
            .anyMatch(node -> node
                .getType() == top.continew.admin.automation.model.enums.AutomationUiTreeNodeType.CASE);
        boolean deleteStep = req.getNodes()
            .stream()
            .anyMatch(node -> node
                .getType() == top.continew.admin.automation.model.enums.AutomationUiTreeNodeType.STEP);
        if (deleteCase)
            StpUtil.checkPermission("automation:automationUiScene:deleteCase");
        if (deleteStep)
            StpUtil.checkPermission("automation:automationUiScene:deleteStep");
        return R.ok(caseTreeService.delete(sceneDbId, req));
    }

    private void checkTreePermission(top.continew.admin.automation.model.enums.AutomationUiTreeNodeType type,
                                     String casePermission,
                                     String stepPermission) {
        StpUtil
            .checkPermission("automation:automationUiScene:" + (type == top.continew.admin.automation.model.enums.AutomationUiTreeNodeType.CASE
                ? casePermission
                : stepPermission));
    }

    /**
     * 添加场景用例
     *
     * @param caseDO 用例参数
     * @param id     场景 ID
     */
    @Operation(summary = "添加场景用例", description = "添加场景用例")
    @SaCheckPermission("automation:automationUiScene:addCase")
    @PutMapping("/{id}/addCase")
    public R<String> addCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO,
                             @PathVariable("id") Long id) {
        return R.ok(baseService.addCase(caseDO, id));
    }

    /**
     * 修改场景用例
     *
     * @param caseDO 用例参数
     * @param id     场景 ID
     */
    @Operation(summary = "修改场景用例", description = "修改场景用例")
    @SaCheckPermission("automation:automationUiScene:updateCase")
    @PutMapping("/{id}/updateCase")
    public void updateCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO,
                           @PathVariable("id") Long id) {
        baseService.updateCase(caseDO, id);
    }

    /**
     * 删除场景用例
     *
     * @param caseDO 用例参数
     * @param id     场景 ID
     */
    @Operation(summary = "删除场景用例", description = "删除场景用例")
    @SaCheckPermission("automation:automationUiScene:deleteCase")
    @PutMapping("/{id}/deleteCase")
    public void deleteCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO,
                           @PathVariable("id") Long id) {
        baseService.deleteCase(caseDO, id);
    }

    /**
     * 拖拽场景用例
     *
     * @param caseDO 用例参数
     * @param id     场景 ID
     */
    @Operation(summary = "拖拽场景用例", description = "拖拽场景用例")
    @SaCheckPermission("automation:automationUiScene:dragCase")
    @PutMapping("/{id}/dragCase")
    public void dragCase(@Validated(CrudValidationGroup.Update.class) @RequestBody CaseDO caseDO,
                         @PathVariable("id") Long id) {
        baseService.dragCase(caseDO, id);
    }

    /**
     * 添加场景步骤
     *
     * @param stepDO 步骤参数
     * @param id     场景 ID
     * @return 步骤 ID
     */
    @Operation(summary = "添加场景用例步骤", description = "添加场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:addStep")
    @PutMapping("/{id}/addStep")
    public R<String> addStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO,
                             @PathVariable("id") Long id) {
        String stepId = baseService.addStep(stepDO, id);
        return R.ok(stepId);
    }

    /**
     * 修改场景步骤
     *
     * @param stepDO 步骤参数
     * @param id     场景 ID
     */
    @Operation(summary = "修改场景用例步骤", description = "修改场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:updateStep")
    @PutMapping("/{id}/updateStep")
    public void updateStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO,
                           @PathVariable("id") Long id) {
        baseService.updateStep(stepDO, id);
    }

    /**
     * 删除场景步骤
     *
     * @param stepDO 步骤参数
     * @param id     场景 ID
     */
    @Operation(summary = "删除场景用例步骤", description = "删除场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:deleteStep")
    @PutMapping("/{id}/deleteStep")
    public void deleteStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO,
                           @PathVariable("id") Long id) {
        baseService.deleteStep(stepDO, id);
    }

    /**
     * 拖拽场景步骤
     *
     * @param stepDO 步骤参数
     * @param id     场景 ID
     */
    @Operation(summary = "拖拽场景用例步骤", description = "拖拽场景用例步骤")
    @SaCheckPermission("automation:automationUiScene:dragStep")
    @PutMapping("/{id}/dragStep")
    public void dragStep(@Validated(CrudValidationGroup.Update.class) @RequestBody StepDO stepDO,
                         @PathVariable("id") Long id) {
        baseService.dragStep(stepDO, id);
    }

    /**
     * 执行指定场景
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
     * 执行当前查询范围内全部场景
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
     * 根据 ID 列表查询场景
     *
     * @param ids 场景 ID 列表
     * @return 场景列表
     */
    @Operation(summary = "查询选中场景", description = "根据 ID 列表查询场景")
    @SaCheckPermission("automation:automationUiScene:list")
    @PostMapping("/selected")
    public R<List<AutomationUiSceneResp>> selected(@RequestBody List<Long> ids) {
        Collection<Long> targetIds = ids == null ? new ArrayList<>() : ids;
        return R.ok(baseService.listSceneRespByIds(targetIds));
    }

    @Operation(summary = "查询选中场景版本", description = "只返回 ID 和修改时间，供执行历史增量刷新")
    @SaCheckPermission("automation:automationUiScene:list")
    @PostMapping("/selected/revisions")
    public R<List<AutomationUiSceneRevisionResp>> selectedRevisions(@RequestBody List<Long> ids) {
        Collection<Long> targetIds = ids == null ? List.of() : ids;
        return R.ok(baseService.listSceneRevisions(targetIds));
    }

    /**
     * 清空执行结果
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
     * 接收执行结果回调
     *
     * @param req 回调参数
     * @return 响应结果
     */
    @Operation(summary = "上传执行结果", description = "接收 UI 自动化场景执行结果回调")
    @SaCheckPermission(value = {"automation:automationUiScene:update",
        "automation:automationUiScene:execute"}, mode = SaMode.OR)
    @PutMapping("/uploadResults")
    public R<Void> uploadResults(@Validated @RequestBody AutomationUiSceneUploadResultReq req) {
        baseService.uploadResults(req);
        return R.ok();
    }

}
