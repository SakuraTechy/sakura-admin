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

import java.io.IOException;
import java.nio.file.Files;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightArtifactResp;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService.Artifact;
import top.continew.admin.automation.service.AutomationPlaywrightArtifactService.ArtifactResource;
import top.continew.starter.web.model.R;

/**
 * Playwright Runner 产物 API。
 *
 * @author Codex
 */
@Tag(name = "自动化管理 - Playwright Runner 产物 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/playwright/artifacts")
public class AutomationPlaywrightArtifactController {

    private final AutomationPlaywrightArtifactService automationPlaywrightArtifactService;

    @Operation(summary = "上传 Playwright Runner 产物")
    @SaCheckPermission(value = {"automation:automationUiScene:update",
        "automation:automationUiScene:execute"}, mode = SaMode.OR)
    @PostMapping
    public R<AutomationPlaywrightArtifactResp> upload(@RequestParam String runId,
                                                      @RequestParam String artifactType,
                                                      @RequestParam MultipartFile file) {
        Artifact artifact = automationPlaywrightArtifactService.store(runId, artifactType, file);
        return R.ok(AutomationPlaywrightArtifactResp.builder()
            .runId(artifact.runId())
            .artifactType(artifact.artifactType())
            .fileName(artifact.fileName())
            .url(artifact.url())
            .contentType(artifact.contentType())
            .size(artifact.size())
            .build());
    }

    @Operation(summary = "读取 Playwright Runner 产物")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{runId}/{fileName}")
    public ResponseEntity<InputStreamResource> get(@PathVariable String runId,
                                                   @PathVariable String fileName) throws IOException {
        ArtifactResource resource = automationPlaywrightArtifactService.load(runId, fileName);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .contentLength(Files.size(resource.path()))
            .contentType(MediaType.parseMediaType(resource.contentType()))
            .body(new InputStreamResource(Files.newInputStream(resource.path())));
    }
}
