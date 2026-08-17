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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCaseStatusReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightBatchResp;
import top.continew.admin.automation.model.resp.playwright.AutomationCdpPlaybackAvailabilityResp;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseCancellationResp;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.admin.automation.support.AutomationCdpPlaybackPolicy;
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
    private final AutomationPlaywrightRunnerJobService runnerJobService;
    private final AutomationCdpPlaybackPolicy cdpPlaybackPolicy;

    @Operation(summary = "查询 CDP 受控会话灰度资格")
    @SaCheckPermission("automation:automationUiScene:execute")
    @GetMapping("/cdp-playback/availability")
    public R<AutomationCdpPlaybackAvailabilityResp> getCdpPlaybackAvailability() {
        AutomationCdpPlaybackAvailabilityResp response = new AutomationCdpPlaybackAvailabilityResp();
        boolean allowed = cdpPlaybackPolicy.isManagedContextAllowed();
        response.setManagedContextEnabled(allowed);
        response.setReason(allowed ? "" : cdpPlaybackPolicy.unavailableReason());
        return R.ok(response);
    }

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
        // 先固化批次取消终态，再终止进程并删除认证状态，迟到结果不能覆盖 cancelled。
        automationPlaywrightCaseService.cancelBatch(sceneKey, batchId);
        runnerJobService.cancelBatch(batchId);
        return R.ok();
    }

    @Operation(summary = "取消批次内单个用例")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PatchMapping("/{sceneKey}/{batchId}/cases/{caseId}/cancel")
    public R<Void> cancelCase(@PathVariable String sceneKey,
                              @PathVariable String batchId,
                              @PathVariable String caseId) {
        // 先固化单用例取消事实，防止进程退出后的迟到结果恢复终态。
        automationPlaywrightCaseService.cancelCase(sceneKey, batchId, caseId);
        runnerJobService.cancelCase(sceneKey, batchId, caseId);
        return R.ok();
    }

    @Operation(summary = "查询批次内用例取消请求")
    @SaCheckPermission("automation:automationUiScene:execute")
    @GetMapping("/{sceneKey}/{batchId}/cases/{caseId}/cancellation")
    public R<AutomationPlaywrightCaseCancellationResp> getCaseCancellation(@PathVariable String sceneKey,
                                                                           @PathVariable String batchId,
                                                                           @PathVariable String caseId) {
        return R.ok(automationPlaywrightCaseService.getCaseCancellation(sceneKey, batchId, caseId));
    }
}
