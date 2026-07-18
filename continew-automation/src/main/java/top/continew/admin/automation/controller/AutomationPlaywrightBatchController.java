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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCaseStatusReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.starter.web.model.R;

/**
 * Playwright/CDP 执行批次 API。
 */
@Tag(name = "自动化管理-Playwright 执行批次")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/playwright/execution-batches")
public class AutomationPlaywrightBatchController {

    private final AutomationPlaywrightCaseService automationPlaywrightCaseService;

    @Operation(summary = "创建执行批次")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PostMapping
    public R<AutomationPlaywrightBatchResp> create(@Valid @RequestBody AutomationPlaywrightBatchCreateReq req) {
        return R.ok(automationPlaywrightCaseService.createBatch(req));
    }

    @Operation(summary = "更新批次内用例状态")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PatchMapping("/{sceneKey}/{batchId}/cases/{caseId}")
    public R<Void> updateCaseStatus(@PathVariable String sceneKey,
                                    @PathVariable String batchId,
                                    @PathVariable String caseId,
                                    @Valid @RequestBody AutomationPlaywrightBatchCaseStatusReq req) {
        automationPlaywrightCaseService.updateBatchCaseStatus(sceneKey, batchId, caseId, req);
        return R.ok();
    }

    @Operation(summary = "取消执行批次")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PatchMapping("/{sceneKey}/{batchId}/cancel")
    public R<Void> cancel(@PathVariable String sceneKey, @PathVariable String batchId) {
        automationPlaywrightCaseService.cancelBatch(sceneKey, batchId);
        return R.ok();
    }
}
