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
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.automation.model.req.ai.AutomationVariableExtractRuleReq;
import top.continew.admin.automation.model.resp.ai.AutomationVariableExtractRuleResp;
import top.continew.admin.automation.service.AutomationVariableExtractRuleService;
import top.continew.starter.web.model.R;

/**
 * AI 变量提取规则生成 API。
 *
 * @author Codex
 * @since 2026/09/12
 */
@Tag(name = "自动化管理 AI 变量提取规则 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/automation/ai")
public class AutomationAiVariableExtractRuleController {

    private final AutomationVariableExtractRuleService variableExtractRuleService;

    /**
     * 生成变量提取规则。
     *
     * @param req 变量提取规则请求参数
     * @return 生成的提取规则
     */
    @Operation(summary = "生成变量提取规则", description = "根据原始值和提取指令，通过 AI 生成正则表达式提取规则")
    @SaCheckPermission(value = {"automation:automationUiScene:create",
        "automation:automationUiScene:update"}, mode = SaMode.OR)
    @PostMapping("/variable-extract-rule")
    public ResponseEntity<R<AutomationVariableExtractRuleResp>> generateVariableExtractRule(@Valid @RequestBody AutomationVariableExtractRuleReq req) {
        AutomationVariableExtractRuleResp result = variableExtractRuleService.generateRule(req);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(result));
    }
}
