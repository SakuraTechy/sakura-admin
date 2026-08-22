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

import java.util.Set;

import org.springframework.stereotype.Component;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.starter.core.exception.BusinessException;

/** 把登录主体转换为场景查询可使用的最小访问范围。 */
@Component
public class AutomationUiSceneAccessScopeResolver {

    public AccessScope currentScope() {
        UserContext context = UserContextHolder.getContext();
        if (context == null || context.getId() == null) {
            throw new BusinessException("AUTOMATION_SCENE_ACCESS_DENIED：未取得登录主体");
        }
        return new AccessScope(context.getId(), context.isAdmin(), nullToEmpty(context
            .getPermissions()), nullToEmpty(context.getRoleCodes()));
    }

    private Set<String> nullToEmpty(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    /** 权限正文只用于服务端计算不可逆缓存分区摘要，不进入响应或日志。 */
    public record AccessScope(Long userId, boolean admin, Set<String> permissions, Set<String> roleCodes) {
    }
}
