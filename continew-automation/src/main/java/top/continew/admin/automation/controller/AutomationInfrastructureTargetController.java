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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureTargetResp;
import top.continew.admin.automation.service.AutomationInfrastructureTaskService;
import top.continew.starter.web.model.R;

/** 基础设施步骤可选目标 API。 */
@Tag(name = "自动化管理基础设施目标 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/infrastructure/targets")
public class AutomationInfrastructureTargetController {

    private final AutomationInfrastructureTaskService taskService;

    @Operation(summary = "查询当前项目可选基础设施目标")
    @SaCheckPermission("automation:automationUiScene:updateStep")
    @GetMapping
    public R<List<AutomationInfrastructureTargetResp>> list(@RequestParam Long projectId, @RequestParam String kind) {
        return R.ok(taskService.listTargets(projectId, kind));
    }
}
