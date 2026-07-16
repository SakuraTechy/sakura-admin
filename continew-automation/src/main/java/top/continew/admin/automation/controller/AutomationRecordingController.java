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
import java.util.LinkedHashMap;
import java.util.Map;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.recording.AutomationRecordingImportReq;
import top.continew.admin.automation.model.resp.recording.AutomationRecordingImportResp;
import top.continew.admin.automation.service.AutomationRecordingImportService;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService;
import top.continew.admin.automation.service.AutomationRecordingScreenshotService.ScreenshotResource;

/**
 * Playwright 录制导入 API。
 *
 * @author Codex
 */
@Tag(name = "自动化管理 Playwright 录制导入 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/automationUiScene/recordings")
public class AutomationRecordingController {

    private final AutomationRecordingImportService automationRecordingImportService;
    private final AutomationRecordingScreenshotService automationRecordingScreenshotService;

    /**
     * 导入 Playwright 录制结果。
     *
     * @param req 导入请求
     * @return 导入结果
     */
    @Operation(summary = "导入 Playwright 录制结果", description = "支持 createScene、appendCase、replaceCase、appendStep、replaceStep")
    @PostMapping("/import")
    public Map<String, Object> importRecording(@Valid @RequestBody AutomationRecordingImportReq req) {
        AutomationRecordingImportResp resp = automationRecordingImportService.importRecording(req);
        Map<String, Object> result = new LinkedHashMap<>();
        // CueCast 旧客户端以 code === 0 判断成功；admin-ui 统一拦截器以 success 判断成功。
        result.put("success", true);
        result.put("code", 0);
        result.put("msg", "success");
        result.put("message", "success");
        result.put("data", resp);
        return result;
    }

    /**
     * 读取录制截图 artifact。
     *
     * @param recordingId 录制 ID
     * @param fileName    文件名
     * @return 截图文件
     * @throws IOException 文件读取失败
     */
    @Operation(summary = "读取 Playwright 录制截图", description = "读取已文件化保存的录制截图 artifact")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/screenshots/{recordingId}/{fileName}")
    public ResponseEntity<InputStreamResource> getScreenshot(@PathVariable String recordingId,
                                                             @PathVariable String fileName) throws IOException {
        ScreenshotResource resource = automationRecordingScreenshotService.load(recordingId, fileName);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .contentType(MediaType.parseMediaType(resource.contentType()))
            .body(new InputStreamResource(Files.newInputStream(resource.path())));
    }
}
