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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import top.continew.starter.core.exception.BadRequestException;

class AutomationUiExecutionCursorCodecTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void springShouldCreateCodecWithConfiguredConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues
                .of("automation.ui-query.cursor-secret=" + SECRET, "automation.ui-query.cursor-ttl-seconds=900")
                .applyTo(context);
            context.register(AutomationUiExecutionCursorCodec.class);
            context.refresh();

            assertThat(context.getBean(AutomationUiExecutionCursorCodec.class)).isNotNull();
        }
    }

    @Test
    void signedCursorShouldRoundTripStableKeyAndScope() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T02:00:00Z"), ZoneOffset.UTC);
        AutomationUiExecutionCursorCodec codec = new AutomationUiExecutionCursorCodec(SECRET, 900, clock);
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 18, 10, 0, 0);

        String token = codec.encode(8L, "filter", "permission", createTime, 21L, false);
        var claim = codec.decode(token);

        assertThat(claim.sceneDbId()).isEqualTo(8L);
        assertThat(claim.createTime()).isEqualTo(createTime);
        assertThat(claim.executionDbId()).isEqualTo(21L);
        assertThat(claim.filterDigest()).isEqualTo("filter");
        assertThat(claim.permissionScopeDigest()).isEqualTo("permission");
        assertThat(claim.ascending()).isFalse();
    }

    @Test
    void tamperedOrExpiredCursorShouldFailClosed() {
        Clock issuedAt = Clock.fixed(Instant.parse("2026-08-18T02:00:00Z"), ZoneOffset.UTC);
        AutomationUiExecutionCursorCodec issuer = new AutomationUiExecutionCursorCodec(SECRET, 60, issuedAt);
        String token = issuer.encode(8L, "filter", "permission", LocalDateTime.of(2026, 8, 18, 10, 0), 21L, false);

        assertThatThrownBy(() -> issuer.decode("x" + token.substring(1))).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("INVALID_CURSOR");
        Clock expiredAt = Clock.fixed(Instant.parse("2026-08-18T02:01:01Z"), ZoneOffset.UTC);
        AutomationUiExecutionCursorCodec verifier = new AutomationUiExecutionCursorCodec(SECRET, 60, expiredAt);
        assertThatThrownBy(() -> verifier.decode(token)).isInstanceOf(BadRequestException.class)
            .hasMessageContaining("CURSOR_EXPIRED");
    }
}
