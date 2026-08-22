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

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.resp.AutomationUiSceneGlobalRevisionResp;
import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.query.AutomationUiExecutionCaseHistoryQuery;
import top.continew.admin.automation.model.req.AutomationUiPageReq;
import top.continew.admin.automation.model.resp.AutomationUiExecutionArtifactResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseHistoryResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionPageResp;
import top.continew.admin.automation.service.AutomationUiExecutionQueryService;
import top.continew.admin.automation.service.AutomationUiSceneQueryService;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.web.model.R;

/** UI 自动化执行轻量查询 API。 */
@Tag(name = "自动化管理-UI 自动化执行轻量查询 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/executions")
public class AutomationUiExecutionQueryController {

    private final AutomationUiSceneQueryService sceneQueryService;
    private final AutomationUiExecutionQueryService executionQueryService;

    @Operation(summary = "分页查询执行历史", description = "只返回执行摘要，不包含 case、step、诊断或原始结果")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping
    public AutomationUiExecutionPageResp page(@Validated AutomationUiExecutionQuery query,
                                              @Validated AutomationUiPageReq pageQuery) {
        return executionQueryService.page(query, pageQuery);
    }

    @Operation(summary = "查询执行详情")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{executionDbId}")
    public AutomationUiExecutionDetailResp detail(@org.springframework.web.bind.annotation.PathVariable Long executionDbId) {
        return executionQueryService.detail(executionDbId);
    }

    @Operation(summary = "分页查询执行用例")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{executionDbId}/cases")
    public PageResp<AutomationUiExecutionCaseResp> cases(@org.springframework.web.bind.annotation.PathVariable Long executionDbId,
                                                         @Validated AutomationUiPageReq pageQuery) {
        return executionQueryService.cases(executionDbId, pageQuery);
    }

    @Operation(summary = "按场景和用例分页查询执行历史")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/cases/history")
    public PageResp<AutomationUiExecutionCaseHistoryResp> caseHistory(@Validated AutomationUiExecutionCaseHistoryQuery query,
                                                                      @Validated AutomationUiPageReq pageQuery) {
        return executionQueryService.caseHistory(query, pageQuery);
    }

    @Operation(summary = "分页查询执行 Artifact 安全元数据")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{executionDbId}/artifacts")
    public PageResp<AutomationUiExecutionArtifactResp> artifacts(@org.springframework.web.bind.annotation.PathVariable Long executionDbId,
                                                                 @Validated AutomationUiPageReq pageQuery) {
        return executionQueryService.artifacts(executionDbId, pageQuery);
    }

    @Operation(summary = "查询场景全局执行版本", description = "无权限和不存在的场景统一省略")
    @SaCheckPermission("automation:automationUiScene:list")
    @GetMapping("/revisions")
    public R<List<AutomationUiSceneGlobalRevisionResp>> revisions(@RequestParam("sceneIds") List<Long> sceneDbIds) {
        return R.ok(sceneQueryService.revisions(sceneDbIds));
    }
}
