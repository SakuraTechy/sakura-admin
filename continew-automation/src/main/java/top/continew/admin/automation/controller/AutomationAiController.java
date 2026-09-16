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
import cn.dev33.satoken.annotation.SaIgnore;
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
import top.continew.admin.automation.model.req.ai.AutomationAiStepPlanReq;
import top.continew.admin.automation.model.req.ai.AutomationAiVisionRecognizeReq;
import top.continew.admin.automation.model.req.ai.AutomationVariableExtractRuleReq;
import top.continew.admin.automation.model.resp.ai.AutomationAiStepPlanResp;
import top.continew.admin.automation.model.resp.ai.AutomationAiVisionRecognizeResp;
import top.continew.admin.automation.model.resp.ai.AutomationVariableExtractRuleResp;
import top.continew.admin.automation.service.AutomationAiStepPlanService;
import top.continew.admin.automation.service.AutomationAiVisionRecognizeService;
import top.continew.admin.automation.service.AutomationVariableExtractRuleService;
import top.continew.starter.web.model.R;

/**
 * 自动化 AI 功能 API
 *
 * @author Codex
 * @since 2026/09/13
 */
@Tag(name = "自动化管理 AI API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/automation/ai")
public class AutomationAiController {

    private final AutomationVariableExtractRuleService variableExtractRuleService;
    private final AutomationAiStepPlanService stepPlanService;
    private final AutomationAiVisionRecognizeService visionRecognizeService;

    /**
     * 生成变量提取规则
     *
     * @param req 变量提取规则请求参数
     * @return 生成的提取规则
     */
    @Operation(summary = "生成变量提取规则", description = "根据原始值和提取指令，通过 AI 生成正则表达式提取规则")
    @SaIgnore
    @SaCheckPermission(value = {"automation:automationUiScene:create",
        "automation:automationUiScene:update"}, mode = SaMode.OR)
    @PostMapping("/variable-extract-rule")
    public ResponseEntity<R<AutomationVariableExtractRuleResp>> generateVariableExtractRule(@Valid @RequestBody AutomationVariableExtractRuleReq req) {
        AutomationVariableExtractRuleResp result = variableExtractRuleService.generateRule(req);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(result));
    }

    /**
     * 生成步骤规划
     *
     * @param req 步骤规划请求参数
     * @return 生成的操作计划
     */
    @Operation(summary = "生成步骤规划", description = "根据自然语言指令和页面结构，通过 AI 生成具体操作计划")
    @SaIgnore
    @SaCheckPermission(value = {"automation:automationUiScene:create",
        "automation:automationUiScene:update"}, mode = SaMode.OR)
    @PostMapping("/step-plan")
    public ResponseEntity<R<AutomationAiStepPlanResp>> generateStepPlan(@Valid @RequestBody AutomationAiStepPlanReq req) {
        AutomationAiStepPlanResp result = stepPlanService.generateStepPlan(req);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(result));
    }

    /**
     * 视觉识别（验证码识别等）
     *
     * @param req 视觉识别请求参数
     * @return 识别结果
     */
    @Operation(summary = "AI 视觉识别", description = "通过 AI 识别图片中的文本内容，支持验证码识别")
    @SaIgnore
    @SaCheckPermission(value = {"automation:automationUiScene:create",
        "automation:automationUiScene:update"}, mode = SaMode.OR)
    @PostMapping("/vision-recognize")
    public ResponseEntity<R<AutomationAiVisionRecognizeResp>> visionRecognize(@Valid @RequestBody AutomationAiVisionRecognizeReq req) {
        AutomationAiVisionRecognizeResp result = visionRecognizeService.recognizeVision(req);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(result));
    }
}
