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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;

/** 为执行历史键集分页签发和校验不透明游标。 */
@Component
public class AutomationUiExecutionCursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public AutomationUiExecutionCursorCodec(@Value("${automation.ui-query.cursor-secret:${sa-token.jwt-secret-key:}}") String secret,
                                            @Value("${automation.ui-query.cursor-ttl-seconds:900}") long ttlSeconds) {
        this(secret, ttlSeconds, Clock.systemUTC());
    }

    AutomationUiExecutionCursorCodec(String secret, long ttlSeconds, Clock clock) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("automation.ui-query.cursor-secret 至少需要 16 个字符");
        }
        if (ttlSeconds < 60 || ttlSeconds > 3600) {
            throw new IllegalStateException("automation.ui-query.cursor-ttl-seconds 必须在 60～3600 之间");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    public String encode(Long sceneDbId,
                         String filterDigest,
                         String permissionScopeDigest,
                         LocalDateTime createTime,
                         Long executionDbId,
                         boolean ascending) {
        if (sceneDbId == null || createTime == null || executionDbId == null) {
            throw new BusinessException("CURSOR_SOURCE_INVALID：无法从缺少稳定排序键的记录生成游标");
        }
        long expiresAt = clock.instant().plusSeconds(ttlSeconds).toEpochMilli();
        String payload = String.join("|", "1", sceneDbId.toString(), filterDigest, permissionScopeDigest, createTime
            .toString(), executionDbId.toString(), Long.toString(expiresAt), ascending ? "1" : "0");
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return BASE64_ENCODER.encodeToString(payloadBytes) + "." + BASE64_ENCODER.encodeToString(sign(payloadBytes));
    }

    public CursorClaim decode(String cursor) {
        if (cursor == null || cursor.length() > 2048) {
            throw invalidCursor();
        }
        String[] tokenParts = cursor.split("\\.", -1);
        if (tokenParts.length != 2) {
            throw invalidCursor();
        }
        try {
            byte[] payloadBytes = BASE64_DECODER.decode(tokenParts[0]);
            byte[] signature = BASE64_DECODER.decode(tokenParts[1]);
            if (!MessageDigest.isEqual(signature, sign(payloadBytes))) {
                throw invalidCursor();
            }
            String[] fields = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 8 || !"1".equals(fields[0])) {
                throw invalidCursor();
            }
            long expiresAt = Long.parseLong(fields[6]);
            if (expiresAt <= clock.millis()) {
                throw new BadRequestException("CURSOR_EXPIRED：游标已过期，请重新进入游标模式");
            }
            return new CursorClaim(Long.parseLong(fields[1]), fields[2], fields[3], LocalDateTime.parse(fields[4]), Long
                .parseLong(fields[5]), expiresAt, "1".equals(fields[7]));
        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidCursor();
        }
    }

    public String digest(Collection<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte)':');
                digest.update(bytes);
                digest.update((byte)0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM 不支持 HMAC-SHA256", e);
        }
    }

    private BadRequestException invalidCursor() {
        return new BadRequestException("INVALID_CURSOR：游标无效或已被篡改");
    }

    /** 游标内容只在服务端使用，不写入响应正文或日志。 */
    public record CursorClaim(Long sceneDbId, String filterDigest, String permissionScopeDigest,
                              LocalDateTime createTime, Long executionDbId, long expiresAt, boolean ascending) {
    }
}
