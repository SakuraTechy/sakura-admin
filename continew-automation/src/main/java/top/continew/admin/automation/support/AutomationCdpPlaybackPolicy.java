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

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.starter.core.exception.BusinessException;

/**
 * CDP 受控 BrowserContext 三种会话模式的灰度访问策略。
 *
 * <p>默认关闭且不以 UI 隐藏作为安全边界，批次创建时必须再次校验当前调用人。</p>
 *
 * @author Codex
 */
@Component
public class AutomationCdpPlaybackPolicy {

    private final boolean enabled;
    private final Set<String> allowedUsers;

    public AutomationCdpPlaybackPolicy(@Value("${automation.cdp-playback.enabled:false}") boolean enabled,
                                       @Value("${automation.cdp-playback.allowed-users:}") String allowedUsers) {
        this.enabled = enabled;
        this.allowedUsers = Arrays.stream(StringUtils.defaultString(allowedUsers).split(","))
            .map(StringUtils::trimToEmpty)
            .filter(StringUtils::isNotBlank)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isManagedContextAllowed() {
        return isManagedContextAllowed(UserContextHolder.getUsername());
    }

    /**
     * 单独暴露用户名判断，供无 Sa-Token 上下文的单元测试和异步调度做确定性校验。
     */
    public boolean isManagedContextAllowed(String username) {
        return enabled && (allowedUsers.contains("*") || (StringUtils.isNotBlank(username) && allowedUsers
            .contains(username.trim().toLowerCase(Locale.ROOT))));
    }

    public String unavailableReason() {
        if (!enabled) {
            return "CDP 受控 BrowserContext 灰度开关未开启";
        }
        return "当前账号不在 CDP 受控 BrowserContext 灰度白名单中";
    }

    public void assertManagedContextAllowed() {
        if (!isManagedContextAllowed()) {
            throw new BusinessException("CDP_MANAGED_CONTEXT_NOT_ALLOWED：" + unavailableReason());
        }
    }
}
