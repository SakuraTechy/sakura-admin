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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 基础设施结果契约边界：保留 v2 有序结构，同时限制预览大小并清除敏感文本。 */
@Component
@RequiredArgsConstructor
public class AutomationInfrastructureResultSanitizer {

    private static final int MAX_PREVIEW_BYTES = 64 * 1024;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_OUTPUT_LENGTH = 64 * 1024;
    private final ObjectMapper objectMapper;

    public Map<String, Object> sanitize(Object source) {
        if (!(source instanceof Map<?, ?> raw))
            return Map.of();
        int schemaVersion = intValue(raw.get("schemaVersion"), raw.containsKey("results") ? 2 : 1);
        if (schemaVersion == 2)
            return sanitizeV2(raw);
        if (schemaVersion == 1)
            return sanitizeV1(raw);
        Map<String, Object> unsupported = new LinkedHashMap<>();
        unsupported.put("schemaVersion", schemaVersion);
        putText(unsupported, raw, "kind");
        unsupported.put("results", List.of());
        unsupported.put("warnings", List.of("RESULT_SCHEMA_UNSUPPORTED：不支持基础设施结果 schema v" + schemaVersion));
        unsupported.put("truncated", true);
        unsupported.put("artifact", Map.of("available", false));
        return unsupported;
    }

    public String serializePreview(Map<String, Object> result) {
        if (result == null || result.isEmpty())
            return null;
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.length() <= MAX_PREVIEW_BYTES)
                return json;
            Map<String, Object> compact = compact(result);
            json = objectMapper.writeValueAsString(compact);
            if (json.length() <= MAX_PREVIEW_BYTES)
                return json;
            return objectMapper.writeValueAsString(Map.of("schemaVersion", intValue(result
                .get("schemaVersion"), 2), "kind", text(result.get("kind")), "truncated", true, "warnings", List
                    .of("RESULT_PREVIEW_TRUNCATED：结果预览超过 64 KB，请读取受鉴权附件")));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> sanitizeV2(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 2);
        putText(result, raw, "kind");
        putNumber(result, raw, "exitCode");
        putNumber(result, raw, "durationMs");
        result.put("results", sanitizeV2Results(raw.get("results")));
        result.put("warnings", safeStringList(raw.get("warnings"), 100));
        result.put("stdout", output(raw.get("stdout")));
        result.put("stderr", output(raw.get("stderr")));
        result.put("truncated", booleanValue(raw.get("truncated"), false));
        result.put("artifact", sanitizeArtifact(raw.get("artifact")));
        return result;
    }

    private List<Map<String, Object>> sanitizeV2Results(Object source) {
        if (!(source instanceof List<?> rawResults))
            return List.of();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : rawResults.stream().limit(20).toList()) {
            if (!(item instanceof Map<?, ?> raw))
                continue;
            String type = text(raw.get("type"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            switch (type) {
                case "ROW_SET" -> {
                    result.put("columns", sanitizeColumns(raw.get("columns")));
                    result.put("rows", sanitizeOrderedRows(raw.get("rows")));
                    putNumber(result, raw, "rowCount");
                    result.put("truncated", booleanValue(raw.get("truncated"), false));
                }
                case "UPDATE_COUNT" -> putNumber(result, raw, "affectedRows");
                case "OUT_PARAMETERS" -> result.put("parameters", sanitizeOutParameters(raw.get("parameters")));
                case "NATIVE_RESULT" -> {
                    putText(result, raw, "operation");
                    result.put("value", safeValue(raw.get("value"), 0));
                    putNumber(result, raw, "rowCount");
                    result.put("truncated", booleanValue(raw.get("truncated"), false));
                }
                default -> {
                    result.put("type", "UNSUPPORTED_RESULT");
                    result.put("originalType", limit(type));
                }
            }
            results.add(result);
        }
        return results;
    }

    private List<Map<String, Object>> sanitizeColumns(Object source) {
        if (!(source instanceof List<?> rawColumns))
            return List.of();
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Object item : rawColumns.stream().limit(200).toList()) {
            if (!(item instanceof Map<?, ?> raw))
                continue;
            Map<String, Object> column = new LinkedHashMap<>();
            putText(column, raw, "name");
            putText(column, raw, "label");
            putNumber(column, raw, "jdbcType");
            putText(column, raw, "typeName");
            column.put("nullable", booleanValue(raw.get("nullable"), true));
            columns.add(column);
        }
        return columns;
    }

    private List<List<Object>> sanitizeOrderedRows(Object source) {
        if (!(source instanceof List<?> rawRows))
            return List.of();
        List<List<Object>> rows = new ArrayList<>();
        for (Object item : rawRows.stream().limit(200).toList()) {
            if (!(item instanceof List<?> rawRow))
                continue;
            rows.add(rawRow.stream().limit(200).map(value -> safeValue(value, 0)).toList());
        }
        return rows;
    }

    private List<Map<String, Object>> sanitizeOutParameters(Object source) {
        if (!(source instanceof List<?> rawParameters))
            return List.of();
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (Object item : rawParameters.stream().limit(200).toList()) {
            if (!(item instanceof Map<?, ?> raw))
                continue;
            Map<String, Object> parameter = new LinkedHashMap<>();
            putText(parameter, raw, "name");
            putNumber(parameter, raw, "position");
            putNumber(parameter, raw, "jdbcType");
            putText(parameter, raw, "typeName");
            parameter.put("value", safeValue(raw.get("value"), 0));
            parameters.add(parameter);
        }
        return parameters;
    }

    private Map<String, Object> sanitizeArtifact(Object source) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        if (!(source instanceof Map<?, ?> raw)) {
            artifact.put("available", false);
            return artifact;
        }
        artifact.put("available", booleanValue(raw.get("available"), false));
        for (String key : List.of("fileId", "fileName", "contentType", "sha256", "expiresAt"))
            putText(artifact, raw, key);
        putNumber(artifact, raw, "sizeBytes");
        return artifact;
    }

    private Map<String, Object> sanitizeV1(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        for (String key : List.of("kind"))
            putText(result, raw, key);
        for (String key : List.of("exitCode", "affectedRows", "rowCount", "durationMs"))
            putNumber(result, raw, key);
        result.put("truncated", booleanValue(raw.get("truncated"), false));
        result.put("stdout", output(raw.get("stdout")));
        result.put("stderr", output(raw.get("stderr")));
        result.put("resultSets", sanitizeV1ResultSets(raw.get("resultSets")));
        result.put("artifact", sanitizeArtifact(raw.get("artifact")));
        return result;
    }

    private List<Map<String, Object>> sanitizeV1ResultSets(Object source) {
        if (!(source instanceof List<?> rawSets))
            return List.of();
        List<Map<String, Object>> sets = new ArrayList<>();
        for (Object item : rawSets.stream().limit(10).toList()) {
            if (!(item instanceof Map<?, ?> raw))
                continue;
            Map<String, Object> set = new LinkedHashMap<>();
            set.put("columns", safeStringList(raw.get("columns"), 200));
            List<Map<String, Object>> rows = new ArrayList<>();
            if (raw.get("rows") instanceof List<?> rawRows) {
                for (Object row : rawRows.stream().limit(200).toList()) {
                    if (!(row instanceof Map<?, ?> rawRow))
                        continue;
                    Map<String, Object> safeRow = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawRow.entrySet().stream().limit(200).toList()) {
                        safeRow.put(limit(text(entry.getKey())), safeValue(entry.getValue(), 0));
                    }
                    rows.add(safeRow);
                }
            }
            set.put("rows", rows);
            sets.add(set);
        }
        return sets;
    }

    private Object safeValue(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean)
            return value;
        if (depth >= 3)
            return limit(text(value));
        if (value instanceof List<?> list)
            return list.stream().limit(200).map(item -> safeValue(item, depth + 1)).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet().stream().limit(50).toList()) {
                result.put(limit(text(entry.getKey())), safeValue(entry.getValue(), depth + 1));
            }
            return result;
        }
        return limit(text(value));
    }

    private List<String> safeStringList(Object source, int maxItems) {
        if (!(source instanceof List<?> list))
            return List.of();
        return list.stream().limit(maxItems).map(this::text).map(this::limit).toList();
    }

    private Map<String, Object> compact(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("truncated", true);
        result.put("stdout", compactOutput(source.get("stdout")));
        result.put("stderr", compactOutput(source.get("stderr")));
        if (source.get("results") instanceof List<?> rawResults) {
            List<Map<String, Object>> compactResults = new ArrayList<>();
            for (Object item : rawResults) {
                if (!(item instanceof Map<?, ?> raw))
                    continue;
                Map<String, Object> compactResult = new LinkedHashMap<>();
                String type = text(raw.get("type"));
                compactResult.put("type", type);
                if ("ROW_SET".equals(type)) {
                    compactResult.put("columns", raw.containsKey("columns") ? raw.get("columns") : List.of());
                    compactResult.put("rows", List.of());
                    compactResult.put("rowCount", raw.containsKey("rowCount") ? raw.get("rowCount") : 0L);
                    compactResult.put("truncated", true);
                } else if ("UPDATE_COUNT".equals(type)) {
                    compactResult.put("affectedRows", raw.containsKey("affectedRows") ? raw.get("affectedRows") : 0L);
                } else if ("OUT_PARAMETERS".equals(type)) {
                    compactResult.put("parameters", raw.containsKey("parameters") ? raw.get("parameters") : List.of());
                } else {
                    compactResult.put("value", "<preview omitted>");
                }
                compactResults.add(compactResult);
            }
            result.put("results", compactResults);
        }
        List<String> warnings = new ArrayList<>(safeStringList(source.get("warnings"), 100));
        warnings.add("RESULT_PREVIEW_TRUNCATED：结果预览超过 64 KB，请读取受鉴权附件");
        result.put("warnings", warnings);
        return result;
    }

    private String compactOutput(Object value) {
        String output = output(value);
        return output.length() <= 4096 ? output : output.substring(0, 4096) + "…[truncated]";
    }

    private String output(Object value) {
        String sanitized = sanitize(text(value));
        return sanitized.length() <= MAX_OUTPUT_LENGTH
            ? sanitized
            : sanitized.substring(0, MAX_OUTPUT_LENGTH) + "…[truncated]";
    }

    private void putText(Map<String, Object> target, Map<?, ?> source, String key) {
        if (source.containsKey(key) && source.get(key) != null)
            target.put(key, limit(text(source.get(key))));
    }

    private void putNumber(Map<String, Object> target, Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number)
            target.put(key, value);
        else if (value != null) {
            try {
                target.put(key, Long.parseLong(text(value)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number)
            return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool)
            return bool;
        return value == null ? fallback : Boolean.parseBoolean(text(value));
    }

    private String sanitize(String value) {
        return value.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1***")
            .replaceAll("(?i)(password|passwd|pwd|token|secret|api[_-]?key)\\s*([=:])\\s*[^\\s,;]+", "$1$2***");
    }

    private String limit(String value) {
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH) + "…[truncated]";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
