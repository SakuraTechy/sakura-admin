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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.hutool.crypto.digest.DigestUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

/**
 * 基础设施动作风险分类。
 *
 * <p>SQL 分类使用词法扫描，不把字符串或注释中的关键字当成语句；无法可靠分类时提升为 destructive。
 * 自由命令始终是 host-privileged，只有部署侧配置的精确模板摘要才能降级。</p>
 */
@Component
public class AutomationInfrastructureRiskPolicy {

    private static final Set<String> READ_SQL = Set.of("SELECT", "WITH", "SHOW", "EXPLAIN", "DESCRIBE", "DESC");
    private static final Set<String> WRITE_SQL = Set.of("INSERT", "UPDATE", "MERGE", "REPLACE", "UPSERT");
    private static final Set<String> DESTRUCTIVE_SQL = Set
        .of("DELETE", "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE", "CALL", "EXEC", "EXECUTE", "RENAME", "COMMENT", "VACUUM", "ANALYZE");

    private final Map<String, String> approvedCommandTemplateDigests;

    public AutomationInfrastructureRiskPolicy(@Value("${automation.infrastructure.command-template-digests:}") String commandTemplateDigests) {
        this.approvedCommandTemplateDigests = parseTemplateDigests(commandTemplateDigests);
    }

    public Assessment assess(String actionType, Map<String, Object> rawStep) {
        Map<String, Object> step = rawStep == null ? Map.of() : rawStep;
        return switch (StringUtils.defaultString(actionType)) {
            case "database_sql" -> assessSql(step);
            case "database_native" -> assessNative(step);
            case "host_file_delete" -> new Assessment("destructive", true, false, null);
            case "server_command", "host_command" -> assessCommand(step);
            case "server_file_upload" -> new Assessment("write", true, false, null);
            default -> new Assessment("read", false, false, null);
        };
    }

    private Assessment assessSql(Map<String, Object> step) {
        String mode = text(step.getOrDefault("sql_mode", "query")).toLowerCase(Locale.ROOT);
        SqlClassification classification = classifySql(text(step.get("sql")));
        if ("query".equals(mode) && classification != SqlClassification.READ) {
            throw new BusinessException("INFRA_SQL_QUERY_MODE_VIOLATION：query 模式只允许单条可确认的只读语句");
        }
        if ("call"
            .equals(mode) || classification == SqlClassification.DESTRUCTIVE || classification == SqlClassification.UNKNOWN) {
            return new Assessment("destructive", true, false, null);
        }
        if (classification == SqlClassification.WRITE || "update".equals(mode)) {
            return new Assessment("write", true, false, null);
        }
        return new Assessment("read", false, true, null);
    }

    private Assessment assessNative(Map<String, Object> step) {
        return switch (text(step.get("mongo_operation")).toLowerCase(Locale.ROOT)) {
            case "find" -> new Assessment("read", false, true, null);
            case "insert", "update" -> new Assessment("write", true, false, null);
            case "delete" -> new Assessment("destructive", true, false, null);
            default -> new Assessment("destructive", true, false, null);
        };
    }

    private Assessment assessCommand(Map<String, Object> step) {
        String command = text(step.get("command"));
        String templateId = StringUtils.trimToNull(text(step.get("command_template_id")));
        if (templateId != null) {
            String expectedDigest = approvedCommandTemplateDigests.get(templateId);
            if (expectedDigest == null || !expectedDigest.equalsIgnoreCase(DigestUtil.sha256Hex(command))) {
                throw new BusinessException("INFRA_COMMAND_TEMPLATE_MISMATCH：命令不匹配部署侧批准模板");
            }
            return new Assessment("write", true, false, templateId);
        }
        return new Assessment("host-privileged", true, false, null);
    }

    private SqlClassification classifySql(String sql) {
        LexicalSql lexical = tokenize(sql);
        if (!lexical.valid() || lexical.statementCount() != 1 || lexical.tokens().isEmpty()) {
            return SqlClassification.UNKNOWN;
        }
        if (lexical.tokens().stream().anyMatch(DESTRUCTIVE_SQL::contains)) {
            return SqlClassification.DESTRUCTIVE;
        }
        if (lexical.tokens().stream().anyMatch(WRITE_SQL::contains)) {
            return SqlClassification.WRITE;
        }
        return READ_SQL.contains(lexical.tokens().get(0)) ? SqlClassification.READ : SqlClassification.UNKNOWN;
    }

    private LexicalSql tokenize(String sql) {
        String source = StringUtils.defaultString(sql);
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean lineComment = false;
        boolean blockComment = false;
        int statementCount = 0;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r')
                    lineComment = false;
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && !backtick && current == '-' && next == '-') {
                flushToken(tokens, token);
                lineComment = true;
                i++;
                continue;
            }
            if (!singleQuote && !doubleQuote && !backtick && current == '/' && next == '*') {
                flushToken(tokens, token);
                blockComment = true;
                i++;
                continue;
            }
            if (!doubleQuote && !backtick && current == '\'') {
                if (singleQuote && next == '\'') {
                    i++;
                } else {
                    singleQuote = !singleQuote;
                }
                flushToken(tokens, token);
                continue;
            }
            if (!singleQuote && !backtick && current == '"') {
                doubleQuote = !doubleQuote;
                flushToken(tokens, token);
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '`') {
                backtick = !backtick;
                flushToken(tokens, token);
                continue;
            }
            if (singleQuote || doubleQuote || backtick)
                continue;
            if (current == ';') {
                flushToken(tokens, token);
                statementCount++;
                continue;
            }
            if (Character.isLetterOrDigit(current) || current == '_')
                token.append(Character.toUpperCase(current));
            else
                flushToken(tokens, token);
        }
        flushToken(tokens, token);
        if (!tokens.isEmpty() && statementCount == 0)
            statementCount = 1;
        return new LexicalSql(tokens, statementCount, !singleQuote && !doubleQuote && !backtick && !blockComment);
    }

    private void flushToken(List<String> tokens, StringBuilder token) {
        if (!token.isEmpty()) {
            tokens.add(token.toString());
            token.setLength(0);
        }
    }

    private Map<String, String> parseTemplateDigests(String configured) {
        Map<String, String> result = new HashMap<>();
        for (String entry : StringUtils.defaultString(configured).split("[,;]")) {
            int separator = entry.indexOf(':');
            if (separator <= 0)
                continue;
            String id = entry.substring(0, separator).trim();
            String digest = entry.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
            if (!id.isBlank() && digest.matches("[0-9a-f]{64}"))
                result.put(id, digest);
        }
        return Map.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Assessment(String riskLevel, boolean approvalRequired, boolean readOnlyTransaction,
                             String commandTemplateId) {
    }

    private record LexicalSql(List<String> tokens, int statementCount, boolean valid) {
    }

    private enum SqlClassification {
        READ, WRITE, DESTRUCTIVE, UNKNOWN
    }
}
