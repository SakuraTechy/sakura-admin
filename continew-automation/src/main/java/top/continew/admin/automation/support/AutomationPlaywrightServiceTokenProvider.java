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

package top.continew.admin.automation.support;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserService;
import top.continew.starter.core.exception.BusinessException;

/**
 * 为无人值守 Runner 创建和复用专用 Sa-Token。
 *
 * <p>定时任务没有浏览器登录上下文，不能复用用户登录 Token。Token 绑定到配置的服务账号，
 * 权限仍由该账号的角色和权限决定；仅在 Token 不存在或即将过期时重新创建。</p>
 */
@Service
@RequiredArgsConstructor
public class AutomationPlaywrightServiceTokenProvider {

    private final UserService userService;
    private final RoleService roleService;

    @Value("${automation.playwright-runner.service-token-enabled:true}")
    private boolean enabled;

    @Value("${automation.playwright-runner.service-account-user-id:}")
    private Long serviceAccountUserId;

    @Value("${automation.playwright-runner.service-token-timeout-seconds:86400}")
    private long tokenTimeoutSeconds;

    @Value("${automation.playwright-runner.service-token-refresh-before-seconds:3600}")
    private long refreshBeforeSeconds;

    @Value("${automation.playwright-runner.service-token-device:PLAYWRIGHT_RUNNER}")
    private String device;

    /**
     * 获取无人值守 Runner 使用的 Token。
     *
     * @return Bearer 前缀之外的 Token；禁用自动服务 Token 时返回空字符串
     */
    public String getToken() {
        if (!enabled) {
            return "";
        }
        Long userId = serviceAccountUserId;
        if (userId == null) {
            throw new BusinessException("Playwright Runner 服务账号未配置，请设置 automation.playwright-runner.service-account-user-id");
        }
        ensureUserEnabled(userId);
        String currentToken = StpUtil.getTokenValueByLoginId(userId, device);
        if (isReusable(currentToken)) {
            ensureUserContext(userId);
            return currentToken;
        }
        synchronized (this) {
            currentToken = StpUtil.getTokenValueByLoginId(userId, device);
            if (isReusable(currentToken)) {
                ensureUserContext(userId);
                return currentToken;
            }
            ensureUserEnabled(userId);
            String token = StpUtil.createLoginSession(userId, SaLoginConfig.setTimeout(tokenTimeoutSeconds)
                .setActiveTimeout(tokenTimeoutSeconds)
                .setDevice(device));
            ensureUserContext(userId);
            return token;
        }
    }

    private boolean isReusable(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        long timeout = StpUtil.getStpLogic().getTokenTimeout(token);
        return timeout == SaTokenDao.NEVER_EXPIRE || timeout > Math.max(0, refreshBeforeSeconds);
    }

    private void ensureUserEnabled(Long userId) {
        UserDO user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("Playwright Runner 服务账号不存在，userId=" + userId);
        }
        if (!DisEnableStatusEnum.ENABLE.equals(user.getStatus())) {
            throw new BusinessException("Playwright Runner 服务账号已禁用，userId=" + userId);
        }
    }

    private void ensureUserContext(Long userId) {
        UserContext context = UserContextHolder.getContext(userId);
        if (context != null) {
            return;
        }
        UserDO user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("Playwright Runner 服务账号不存在，userId=" + userId);
        }
        context = new UserContext(roleService.listPermissionByUserId(userId), roleService.listByUserId(userId), 0);
        BeanUtil.copyProperties(user, context);
        context.setClientType(device);
        context.setClientId("playwright-runner");
        UserContextHolder.setContext(context);
    }
}
