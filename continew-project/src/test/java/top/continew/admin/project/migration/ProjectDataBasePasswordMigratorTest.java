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

package top.continew.admin.project.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.continew.starter.security.crypto.autoconfigure.CryptoProperties;
import top.continew.starter.security.crypto.encryptor.AesEncryptor;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDataBasePasswordMigratorTest {

    private static final String PASSWORD = "abcdefghijklmnop";

    private JdbcTemplate jdbcTemplate;
    private ProjectDataBasePasswordMigrator migrator;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        CryptoProperties properties = new CryptoProperties();
        properties.setPassword(PASSWORD);
        migrator = new ProjectDataBasePasswordMigrator(jdbcTemplate, properties, new ObjectMapper());
    }

    @Test
    void shouldEncryptPlaintextAndSkipValidCiphertext() throws Exception {
        String ciphertext = new AesEncryptor().encrypt("already-encrypted", PASSWORD, null);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(1L, 2L);
        when(resultSet.getString("pass_word")).thenReturn("plain-secret", ciphertext);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0), mapper.mapRow(resultSet, 1));
        });

        int migratedCount = migrator.migrate();

        assertThat(migratedCount).isEqualTo(1);
        verify(jdbcTemplate)
            .update(eq("UPDATE project_data_base_config SET pass_word = ? WHERE id = ?"), eq(new AesEncryptor()
                .encrypt("plain-secret", PASSWORD, null)), eq(1L));
    }

    @Test
    void shouldRejectCiphertextThatCannotBeDecryptedWithCurrentKey() throws Exception {
        String ciphertext = new AesEncryptor().encrypt("secret", "1234567890abcdef", null);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(9L);
        when(resultSet.getString("pass_word")).thenReturn(ciphertext);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> List.of(invocation
            .<RowMapper<?>>getArgument(1)
            .mapRow(resultSet, 0)));

        assertThatThrownBy(migrator::migrate).isInstanceOf(IllegalStateException.class)
            .hasMessage("数据库配置密码迁移失败，记录 ID: 9")
            .hasMessageNotContaining("secret")
            .hasMessageNotContaining(ciphertext);
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    @Test
    void shouldRemoveCredentialsFromEnvironmentSnapshot() throws Exception {
        ResultSet environmentResultSet = mock(ResultSet.class);
        when(environmentResultSet.getLong("id")).thenReturn(7L);
        when(environmentResultSet.getString("data_base_config"))
            .thenReturn("{\"id\":7,\"password\":\"secret\",\"ip\":\"127.0.0.1\",\"configList\":[{\"token\":\"value\"}]}");
        when(jdbcTemplate.query(eq("SELECT id, pass_word FROM project_data_base_config"), any(RowMapper.class)))
            .thenReturn(List.of());
        when(jdbcTemplate
            .query(eq("SELECT id, data_base_config FROM project_environment_config"), any(RowMapper.class)))
            .thenAnswer(invocation -> List.of(invocation.<RowMapper<?>>getArgument(1).mapRow(environmentResultSet, 0)));

        migrator.run(mock(ApplicationArguments.class));

        verify(jdbcTemplate)
            .update(eq("UPDATE project_environment_config SET data_base_config = ? WHERE id = ?"), eq("{\"id\":7,\"ip\":\"127.0.0.1\"}"), eq(7L));
    }
}
