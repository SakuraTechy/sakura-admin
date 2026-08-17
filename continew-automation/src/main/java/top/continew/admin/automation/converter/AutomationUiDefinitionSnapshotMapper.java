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

package top.continew.admin.automation.converter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.lang3.StringUtils;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.common.enums.StatusTypeEnum;

/** 将持久化用例树映射为可散列的规范化执行快照。 */
public final class AutomationUiDefinitionSnapshotMapper {

    private static final TypeReference<List<CaseDO>> CASE_LIST_TYPE = new TypeReference<>() {
    };
    private static final SimpleModule SNAPSHOT_ENUM_MODULE = createSnapshotEnumModule();

    private AutomationUiDefinitionSnapshotMapper() {
    }

    public static Snapshot map(List<CaseDO> caseList) {
        // 先转为 JSON 数据树，避免 CaseDO/StepDO 对象绕过递归字段过滤。
        Object source = JSONUtil.parse(JSONUtil.toJsonStr(caseList == null ? List.of() : caseList));
        Object normalized = normalize("case_list", source);
        String definitionJson = JSONUtil.toJsonStr(normalized == null ? List.of() : normalized);
        return new Snapshot(definitionJson, DigestUtil.sha256Hex(definitionJson));
    }

    public static List<CaseDO> readCases(ObjectMapper objectMapper, String definitionJson) throws Exception {
        // revision 由 Hutool 按枚举名写入；应用 BaseEnum 读取器只认数值，必须在快照边界同时兼容两种格式。
        ObjectMapper snapshotReader = objectMapper.copy();
        snapshotReader.registerModule(SNAPSHOT_ENUM_MODULE);
        return snapshotReader.readValue(definitionJson, CASE_LIST_TYPE);
    }

    private static SimpleModule createSnapshotEnumModule() {
        SimpleModule module = new SimpleModule("automation-ui-definition-snapshot");
        module.addDeserializer(StatusTypeEnum.class, new JsonDeserializer<>() {
            @Override
            public StatusTypeEnum deserialize(JsonParser parser,
                                              DeserializationContext context) throws java.io.IOException {
                String raw = StringUtils.trimToEmpty(parser.getText());
                for (StatusTypeEnum status : StatusTypeEnum.values()) {
                    if (status.name().equalsIgnoreCase(raw) || String.valueOf(status.getValue()).equals(raw) || status
                        .getDescription()
                        .equals(raw)) {
                        return status;
                    }
                }
                return (StatusTypeEnum)context
                    .handleWeirdStringValue(StatusTypeEnum.class, raw, "无法识别的自动化定义状态：%s", raw);
            }
        });
        return module;
    }

    private static Object normalize(String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((childKey, childValue) -> {
                String childName = String.valueOf(childKey);
                if (isTransientDefinitionField(childName)) {
                    return;
                }
                Object normalized = normalize(childName, childValue);
                if (normalized != null) {
                    sorted.put(childName, normalized);
                }
            });
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalizedItems = new ArrayList<>();
            for (Object item : collection) {
                Object normalized = normalize(key, item);
                if (normalized != null) {
                    normalizedItems.add(normalized);
                }
            }
            return normalizedItems;
        }
        if (value instanceof CharSequence text) {
            String normalizedKey = StringUtils.defaultString(key).toLowerCase().replace('-', '_');
            if (normalizedKey.contains("base64") || "screenshot".equals(normalizedKey) || String.valueOf(text)
                .regionMatches(true, 0, "data:image/", 0, 11)) {
                // 截图正文不能进入 revision；playwright_step、locator_meta 和文件化截图引用必须保留。
                return null;
            }
        }
        return value;
    }

    private static boolean isTransientDefinitionField(String key) {
        String normalized = StringUtils.defaultString(key)
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .toLowerCase()
            .replace('-', '_');
        return switch (normalized) {
            case "expected_definition_version", "copy_id", "copy_pid", "drag_node", "drop_node", "drop_position",
                "step_msg", "step", "item_order" -> true;
            default -> false;
        };
    }

    public record Snapshot(String definitionJson, String contentHash) {
    }
}
