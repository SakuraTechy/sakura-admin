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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskCreateReq;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskDispositionReq;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureTaskResp;
import top.continew.admin.automation.service.AutomationInfrastructureTaskService;
import top.continew.admin.automation.service.AutomationInfrastructureTaskService.ArtifactDownload;
import top.continew.starter.web.model.R;

/** 基础设施步骤任务 API。 */
@Tag(name = "自动化管理基础设施任务 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/infrastructure/tasks")
public class AutomationInfrastructureTaskController {

    private final AutomationInfrastructureTaskService taskService;

    @Operation(summary = "创建基础设施步骤任务")
    @SaCheckPermission("automation:automationUiScene:execute-infrastructure")
    @PostMapping
    public R<AutomationInfrastructureTaskResp> create(@Valid @RequestBody AutomationInfrastructureTaskCreateReq req,
                                                      @RequestHeader(value = "X-Execution-Capability", required = false) String executionCapability) {
        if ((req.getExecutionCapability() == null || req.getExecutionCapability()
            .isBlank()) && executionCapability != null && !executionCapability.isBlank()) {
            req.setExecutionCapability(executionCapability);
        }
        return R.ok(taskService.create(req));
    }

    @Operation(summary = "查询基础设施步骤任务")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{taskId}")
    public R<AutomationInfrastructureTaskResp> get(@PathVariable String taskId,
                                                   @RequestParam(required = false) Long afterSequence,
                                                   @RequestHeader(value = "X-Execution-Capability", required = false) String executionCapability) {
        return R.ok(taskService.get(taskId, afterSequence, executionCapability));
    }

    @Operation(summary = "下载基础设施步骤结果附件")
    @SaCheckPermission("automation:automationUiScene:download-infrastructure-artifact")
    @GetMapping("/{taskId}/artifact")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable String taskId,
                                                   @RequestHeader(value = "X-Execution-Capability", required = false) String executionCapability) {
        ArtifactDownload artifact = taskService.downloadArtifact(taskId, executionCapability);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artifact.contentType()))
            .contentLength(artifact.bytes().length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(artifact.fileName())
                .build()
                .toString())
            .header("X-Content-Sha256", artifact.sha256())
            .body(artifact.bytes());
    }

    @Operation(summary = "取消基础设施步骤任务")
    @SaCheckPermission("automation:automationUiScene:execute-infrastructure")
    @DeleteMapping("/{taskId}")
    public R<AutomationInfrastructureTaskResp> cancel(@PathVariable String taskId,
                                                      @RequestHeader(value = "X-Execution-Capability", required = false) String executionCapability) {
        return R.ok(taskService.cancel(taskId, executionCapability));
    }

    @Operation(summary = "人工核验结果不确定的基础设施任务")
    @SaCheckPermission("automation:automationUiScene:dispose-infrastructure-unknown-outcome")
    @PostMapping("/{taskId}/disposition")
    public R<AutomationInfrastructureTaskResp> disposeUnknownOutcome(@PathVariable String taskId,
                                                                     @Valid @RequestBody AutomationInfrastructureTaskDispositionReq req,
                                                                     @RequestHeader(value = "X-Execution-Capability", required = false) String executionCapability) {
        return R.ok(taskService.disposeUnknownOutcome(taskId, req, executionCapability));
    }

}
