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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import cn.hutool.crypto.digest.DigestUtil;

/** 生成和校验短时 execution capability；数据库只保存摘要，不能用它恢复明文凭据。 */
public final class AutomationExecutionCapability {

    public static final int TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AutomationExecutionCapability() {
    }

    public static String issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String digest(String token) {
        return DigestUtil.sha256Hex(token == null ? "" : token);
    }

    public static LocalDateTime expiresAt() {
        return LocalDateTime.now().plusMinutes(TTL_MINUTES);
    }

    public static boolean matches(String token, String expectedDigest, LocalDateTime expiresAt) {
        if (token == null || token.isBlank() || expectedDigest == null || expectedDigest
            .isBlank() || expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            return false;
        }
        return MessageDigest.isEqual(digest(token).getBytes(StandardCharsets.UTF_8), expectedDigest
            .getBytes(StandardCharsets.UTF_8));
    }
}
