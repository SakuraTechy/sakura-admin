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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.security.crypto.autoconfigure.CryptoProperties;
import top.continew.starter.security.crypto.encryptor.AesEncryptor;
import top.continew.starter.security.crypto.encryptor.IEncryptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 历史数据库配置凭据迁移。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ProjectDataBasePasswordMigrator implements ApplicationRunner {

    private static final String SELECT_SQL = "SELECT id, pass_word FROM project_data_base_config";
    private static final String UPDATE_SQL = "UPDATE project_data_base_config SET pass_word = ? WHERE id = ?";
    private static final String SELECT_ENVIRONMENT_SQL = "SELECT id, data_base_config FROM project_environment_config";
    private static final String UPDATE_ENVIRONMENT_SQL = "UPDATE project_environment_config SET data_base_config = ? WHERE id = ?";
    private static final Pattern AES_CIPHERTEXT_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{32})+$");
    private static final Set<String> SENSITIVE_SNAPSHOT_FIELDS = Set
        .of("configlist", "connectionstring", "jdbcurl", "mongouri", "password", "pass_word", "username", "user_name", "url");

    private final JdbcTemplate jdbcTemplate;
    private final CryptoProperties cryptoProperties;
    private final ObjectMapper objectMapper;
    private final IEncryptor encryptor = new AesEncryptor();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        int passwordMigratedCount = this.migratePasswords();
        int environmentSanitizedCount = this.migrateEnvironmentSnapshots();
        if (passwordMigratedCount > 0 || environmentSanitizedCount > 0) {
            log.info("数据库配置凭据迁移完成，密码加密 {} 条，环境快照脱敏 {} 条", passwordMigratedCount, environmentSanitizedCount);
        }
    }

    int migrate() {
        return this.migratePasswords();
    }

    private int migratePasswords() {
        List<PasswordRow> rows = jdbcTemplate.query(SELECT_SQL, (resultSet, rowNum) -> new PasswordRow(resultSet
            .getLong("id"), resultSet.getString("pass_word")));
        List<PasswordUpdate> updates = rows.stream().map(this::prepareUpdate).filter(update -> update != null).toList();
        updates.forEach(update -> jdbcTemplate.update(UPDATE_SQL, update.ciphertext(), update.id()));
        return updates.size();
    }

    private int migrateEnvironmentSnapshots() {
        List<EnvironmentRow> rows = jdbcTemplate.query(SELECT_ENVIRONMENT_SQL, (resultSet,
                                                                                rowNum) -> new EnvironmentRow(resultSet
                                                                                    .getLong("id"), resultSet
                                                                                        .getString("data_base_config")));
        List<EnvironmentUpdate> updates = rows.stream()
            .map(this::prepareEnvironmentUpdate)
            .filter(update -> update != null)
            .toList();
        updates.forEach(update -> jdbcTemplate.update(UPDATE_ENVIRONMENT_SQL, update.json(), update.id()));
        return updates.size();
    }

    private EnvironmentUpdate prepareEnvironmentUpdate(EnvironmentRow row) {
        if (row.json() == null || row.json().isBlank()) {
            return null;
        }
        try {
            JsonNode snapshot = objectMapper.readTree(row.json());
            if (!removeSensitiveFields(snapshot)) {
                return null;
            }
            return new EnvironmentUpdate(row.id(), objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            throw new IllegalStateException("环境数据库配置脱敏迁移失败，记录 ID: " + row.id(), e);
        }
    }

    private boolean removeSensitiveFields(JsonNode node) {
        boolean changed = false;
        if (node instanceof ObjectNode objectNode) {
            List<String> fields = new java.util.ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                if (SENSITIVE_SNAPSHOT_FIELDS.contains(field.toLowerCase(Locale.ROOT))) {
                    objectNode.remove(field);
                    changed = true;
                } else {
                    changed |= removeSensitiveFields(objectNode.get(field));
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                changed |= removeSensitiveFields(child);
            }
        }
        return changed;
    }

    private PasswordUpdate prepareUpdate(PasswordRow row) {
        String storedPassword = row.password();
        if (storedPassword == null || storedPassword.isBlank()) {
            return null;
        }
        if (AES_CIPHERTEXT_PATTERN.matcher(storedPassword).matches()) {
            this.verifyCiphertext(row.id(), storedPassword);
            return null;
        }
        return new PasswordUpdate(row.id(), this.encrypt(row.id(), storedPassword));
    }

    private void verifyCiphertext(long id, String ciphertext) {
        try {
            String plaintext = encryptor.decrypt(ciphertext, cryptoProperties.getPassword(), cryptoProperties
                .getPrivateKey());
            String roundTrip = encryptor.encrypt(plaintext, cryptoProperties.getPassword(), cryptoProperties
                .getPublicKey());
            if (!ciphertext.toLowerCase(Locale.ROOT).equals(roundTrip.toLowerCase(Locale.ROOT))) {
                throw migrationFailure(id, null);
            }
        } catch (Exception e) {
            throw migrationFailure(id, e);
        }
    }

    private String encrypt(long id, String plaintext) {
        try {
            return encryptor.encrypt(plaintext, cryptoProperties.getPassword(), cryptoProperties.getPublicKey());
        } catch (Exception e) {
            throw migrationFailure(id, e);
        }
    }

    private IllegalStateException migrationFailure(long id, Exception cause) {
        // 不记录凭据内容；失败时终止启动，避免历史明文或不可解密密文进入业务查询。
        return new IllegalStateException("数据库配置密码迁移失败，记录 ID: " + id, cause);
    }

    private record PasswordRow(long id, String password) {
    }

    private record PasswordUpdate(long id, String ciphertext) {
    }

    private record EnvironmentRow(long id, String json) {
    }

    private record EnvironmentUpdate(long id, String json) {
    }
}
