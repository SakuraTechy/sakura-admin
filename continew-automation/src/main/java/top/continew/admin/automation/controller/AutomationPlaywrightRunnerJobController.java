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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerJobReq;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService.LiveFrame;
import top.continew.starter.web.model.R;

/**
 * Playwright Runner 任务 API。
 */
@Tag(name = "自动化管理 Playwright Runner 任务 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/playwright/runner/jobs")
public class AutomationPlaywrightRunnerJobController {

    private final AutomationPlaywrightRunnerJobService runnerJobService;

    @Operation(summary = "创建 Playwright Runner 回放任务")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PostMapping
    public R<AutomationPlaywrightRunnerJobResp> create(@Valid @RequestBody AutomationPlaywrightRunnerJobReq req) {
        return R.ok(runnerJobService.create(req));
    }

    @Operation(summary = "查询 Playwright Runner 回放任务")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{jobId}")
    public R<AutomationPlaywrightRunnerJobResp> get(@PathVariable String jobId,
                                                    @RequestParam(required = false) Long afterSequence) {
        return R.ok(runnerJobService.get(jobId, afterSequence));
    }

    @Operation(summary = "取消 Playwright Runner 回放任务")
    @SaCheckPermission("automation:automationUiScene:execute")
    @DeleteMapping("/{jobId}")
    public R<AutomationPlaywrightRunnerJobResp> cancel(@PathVariable String jobId) {
        return R.ok(runnerJobService.cancel(jobId));
    }

    @Operation(summary = "上传 Playwright Runner 实时画面")
    @SaCheckPermission("automation:automationUiScene:execute")
    @PutMapping(value = "/{jobId}/live-frame", consumes = MediaType.IMAGE_JPEG_VALUE)
    public R<Void> uploadLiveFrame(@PathVariable String jobId, @RequestBody byte[] frame) {
        runnerJobService.acceptLiveFrame(jobId, frame);
        return R.ok();
    }

    @Operation(summary = "查看 Playwright Runner 实时画面")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping(value = "/{jobId}/live-frame", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> liveFrame(@PathVariable String jobId,
                                            @RequestParam(required = false) Long afterSequence) {
        AutomationPlaywrightRunnerJobResp job = runnerJobService.get(jobId);
        LiveFrame frame = runnerJobService.getLiveFrame(jobId);
        if (frame == null) {
            HttpStatus status = job.isLiveAvailable() ? HttpStatus.NO_CONTENT : HttpStatus.GONE;
            return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
        }
        if (afterSequence != null && frame.sequence() <= afterSequence) {
            return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header("X-Sakura-Job-Status", job.getStatus())
                .build();
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_JPEG)
            .header("Access-Control-Expose-Headers", "X-Sakura-Frame-Sequence, X-Sakura-Job-Status")
            .header("X-Sakura-Frame-Sequence", String.valueOf(frame.sequence()))
            .header("X-Sakura-Job-Status", job.getStatus())
            .body(frame.content());
    }

    ResponseEntity<byte[]> liveFrame(String jobId) {
        return liveFrame(jobId, null);
    }
}
