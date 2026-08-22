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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.model.req.environment.AutomationEnvironmentResourceBindingReq;
import top.continew.admin.automation.model.resp.environment.AutomationEnvironmentResourceResp;
import top.continew.admin.automation.service.AutomationEnvironmentResourceService;
import top.continew.starter.web.model.R;

/** 项目环境自动化资源绑定 API。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/environment-resources")
public class AutomationEnvironmentResourceController {

    private final AutomationEnvironmentResourceService resourceService;

    @GetMapping("/slots")
    @SaCheckPermission("automation:automationUiScene:get")
    public R<List<AutomationEnvironmentResourceResp>> listSlots(@RequestParam Long projectId,
                                                                @RequestParam(required = false) String kind,
                                                                @RequestParam(required = false) Long environmentId) {
        return R.ok(resourceService.listSlots(projectId, kind, environmentId));
    }

    @PostMapping("/slots/custom-database")
    @SaCheckPermission("project:projectEnvironmentConfig:update")
    public R<AutomationEnvironmentResourceResp> createCustomDatabaseSlot(@RequestParam Long projectId) {
        return R.ok(resourceService.createCustomDatabaseSlot(projectId));
    }

    @DeleteMapping("/slots/{slotId}")
    @SaCheckPermission("project:projectEnvironmentConfig:update")
    public R<Void> deleteCustomDatabaseSlot(@PathVariable Long slotId) {
        resourceService.deleteCustomDatabaseSlot(slotId);
        return R.ok();
    }

    @GetMapping("/environments/{environmentId}")
    @SaCheckPermission("project:projectEnvironmentConfig:get")
    public R<List<AutomationEnvironmentResourceResp>> listEnvironmentResources(@PathVariable Long environmentId) {
        return R.ok(resourceService.listEnvironmentResources(environmentId));
    }

    @PutMapping("/environments/{environmentId}/bindings/{slotId}")
    @SaCheckPermission("project:projectEnvironmentConfig:update")
    public R<AutomationEnvironmentResourceResp> bind(@PathVariable Long environmentId,
                                                     @PathVariable Long slotId,
                                                     @Valid @RequestBody AutomationEnvironmentResourceBindingReq req) {
        return R.ok(resourceService.bind(environmentId, slotId, req.getResourceId()));
    }

    @PostMapping("/environments/{environmentId}/bindings/{slotId}/certificate")
    @SaCheckPermission("project:projectEnvironmentConfig:update")
    public R<AutomationEnvironmentResourceResp> uploadCertificate(@PathVariable Long environmentId,
                                                                  @PathVariable Long slotId,
                                                                  @RequestParam("file") MultipartFile file) {
        return R.ok(resourceService.uploadCertificate(environmentId, slotId, file));
    }

    @DeleteMapping("/environments/{environmentId}/bindings/{slotId}")
    @SaCheckPermission("project:projectEnvironmentConfig:update")
    public R<Void> unbind(@PathVariable Long environmentId, @PathVariable Long slotId) {
        resourceService.unbind(environmentId, slotId);
        return R.ok();
    }
}
