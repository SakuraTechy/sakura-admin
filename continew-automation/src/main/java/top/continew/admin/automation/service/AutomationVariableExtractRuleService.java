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

package top.continew.admin.automation.service;

import top.continew.admin.automation.model.req.ai.AutomationVariableExtractRuleReq;
import top.continew.admin.automation.model.resp.ai.AutomationVariableExtractRuleResp;

/**
 * 变量提取规则生成服务接口
 *
 * @author liuzhi
 * @since 2026-09-12
 */
public interface AutomationVariableExtractRuleService {

    /**
     * 根据原始值和提取指令生成变量提取规则
     *
     * @param req 变量提取规则请求参数
     * @return 变量提取规则响应
     * @throws top.continew.starter.core.exception.BusinessException 业务异常
     */
    AutomationVariableExtractRuleResp generateRule(AutomationVariableExtractRuleReq req);
}
