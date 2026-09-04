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

package top.continew.admin.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.system.model.query.FileQuery;
import top.continew.admin.system.model.req.FileDownloadReq;
import top.continew.admin.system.model.req.FileReq;
import top.continew.admin.system.model.req.WebhookMessageReq;
import top.continew.admin.system.model.resp.file.FileResp;
import top.continew.admin.system.model.resp.file.FileStatisticsResp;
import top.continew.admin.system.service.FileService;
import top.continew.starter.core.validation.ValidationUtils;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.web.model.R;

/**
 * 文件管理 API
 *
 * @author Charles7c
 * @since 2023/12/23 10:38
 */
@Tag(name = "文件管理 API")
@RestController
@RequiredArgsConstructor
@CrudRequestMapping(value = "/system/file", api = {Api.PAGE, Api.UPDATE, Api.DELETE})
public class FileController extends BaseController<FileService, FileResp, FileResp, FileQuery, FileReq> {

    @Log(ignore = true)
    @Operation(summary = "查询文件资源统计", description = "查询文件资源统计")
    @SaCheckPermission("system:file:list")
    @GetMapping("/statistics")
    public FileStatisticsResp statistics() {
        return baseService.statistics();
    }

    @Log("下载远程文件到服务器")
    @Operation(summary = "下载远程文件到服务器", description = "从证书系统下载文件并保存到服务器指定路径")
    @SaCheckPermission("system:file:download")
    @PostMapping("/downloadFile")
    public R<String> downloadFile(@Valid @ModelAttribute FileDownloadReq req) {
        ValidationUtils.validate(req);
        String filePath = baseService.downloadFile(req);
        return R.ok("文件下载成功", filePath);
    }

    @Log("发送企业微信消息")
    @Operation(summary = "发送企业微信消息", description = "批量制作证书完成后推送通知到企业微信群")
    @SaCheckPermission("system:file:webhook")
    @PostMapping("/sendWebhookMessage")
    public R<Void> sendWebhookMessage(@Valid @RequestBody WebhookMessageReq req) {
        ValidationUtils.validate(req);
        baseService.sendWebhookMessage(req);
        return R.ok("消息发送成功");
    }
}
