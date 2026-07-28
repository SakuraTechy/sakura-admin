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

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightResultReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.starter.web.model.R;

/**
 * Playwright Runner 读取和回传 API。
 *
 * @author Codex
 */
@Tag(name = "自动化管理 Playwright Runner API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/playwright/testcases")
public class AutomationPlaywrightCaseController {

    private final AutomationPlaywrightCaseService automationPlaywrightCaseService;

    @Operation(summary = "读取 Playwright 可执行用例", description = "caseKey 格式为业务 sceneId:caseId，兼容数据库场景主键")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{caseKey}")
    public R<AutomationPlaywrightCaseResp> getCase(@PathVariable String caseKey,
                                                   @RequestParam(required = false) Long projectEnvironmentId) {
        return R.ok(automationPlaywrightCaseService.getCase(caseKey, projectEnvironmentId));
    }

    @Operation(summary = "按场景和用例读取 Playwright 用例")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{sceneKey}/{caseId}")
    public R<AutomationPlaywrightCaseResp> getCaseByParts(@PathVariable String sceneKey,
                                                          @PathVariable String caseId,
                                                          @RequestParam(required = false) Long projectEnvironmentId) {
        return R.ok(automationPlaywrightCaseService.getCase(sceneKey + ":" + caseId, projectEnvironmentId));
    }

    @Operation(summary = "回传 Playwright 执行结果", description = "写入规范化执行事实表，并合并用例统计和步骤明细")
    @SaCheckPermission(value = {"automation:automationUiScene:update",
        "automation:automationUiScene:execute"}, mode = SaMode.OR)
    @PostMapping("/{caseKey}/results")
    public R<Void> saveResult(@PathVariable String caseKey, @RequestBody AutomationPlaywrightResultReq req) {
        automationPlaywrightCaseService.saveResult(caseKey, req);
        return R.ok();
    }

    @Operation(summary = "按场景和用例回传 Playwright 执行结果")
    @SaCheckPermission(value = {"automation:automationUiScene:update",
        "automation:automationUiScene:execute"}, mode = SaMode.OR)
    @PostMapping("/{sceneKey}/{caseId}/results")
    public R<Void> saveResultByParts(@PathVariable String sceneKey,
                                     @PathVariable String caseId,
                                     @RequestBody AutomationPlaywrightResultReq req) {
        automationPlaywrightCaseService.saveResult(sceneKey + ":" + caseId, req);
        return R.ok();
    }
}
