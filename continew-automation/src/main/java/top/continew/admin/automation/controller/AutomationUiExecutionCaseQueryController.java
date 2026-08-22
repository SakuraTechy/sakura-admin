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
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepResp;
import top.continew.admin.automation.model.req.AutomationUiPageReq;
import top.continew.admin.automation.service.AutomationUiExecutionQueryService;
import top.continew.starter.extension.crud.model.resp.PageResp;

/** UI 自动化用例执行分层查询 API。 */
@Tag(name = "自动化管理-UI 自动化用例执行查询 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/execution-cases")
public class AutomationUiExecutionCaseQueryController {

    private final AutomationUiExecutionQueryService executionQueryService;

    @Operation(summary = "分页查询用例执行步骤")
    @SaCheckPermission("automation:automationUiScene:get")
    @GetMapping("/{caseExecutionDbId}/steps")
    public PageResp<AutomationUiExecutionStepResp> steps(@PathVariable Long caseExecutionDbId,
                                                         @Validated AutomationUiPageReq pageQuery) {
        return executionQueryService.steps(caseExecutionDbId, pageQuery);
    }
}
