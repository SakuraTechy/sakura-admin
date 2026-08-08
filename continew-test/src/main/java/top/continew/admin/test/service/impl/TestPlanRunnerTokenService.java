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

package top.continew.admin.test.service.impl;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

/** Issues a short-lived, independently revocable token for unattended Runner callbacks. */
@Service
public class TestPlanRunnerTokenService {

    private static final long DEFAULT_TOKEN_TIMEOUT_SECONDS = 6 * 60 * 60;

    @Value("${automation.playwright-runner.delegated-token-timeout-seconds:21600}")
    private long tokenTimeoutSeconds = DEFAULT_TOKEN_TIMEOUT_SECONDS;

    public TokenLease issue(Long ownerUserId, String reportId) {
        if (ownerUserId == null) {
            throw new BusinessException("测试计划缺少创建人，无法签发 Runner 调度凭据");
        }
        SaLoginModel loginModel = new SaLoginModel().setDevice("playwright-runner:" + StringUtils
            .defaultIfBlank(reportId, "unknown"))
            .setTimeout(Math.max(60L, tokenTimeoutSeconds))
            .setExtra("purpose", "scheduled-playwright-runner")
            .setExtra("reportId", StringUtils.defaultString(reportId));
        String token = StpUtil.getStpLogic().createLoginSession(ownerUserId, loginModel);
        if (StringUtils.isBlank(token)) {
            throw new BusinessException("Runner 调度凭据签发失败");
        }
        return new TokenLease(token);
    }

    public void release(TokenLease lease) {
        if (lease == null || StringUtils.isBlank(lease.token())) {
            return;
        }
        StpUtil.getStpLogic().logoutByTokenValue(lease.token());
    }

    public record TokenLease(String token) {
    }
}
