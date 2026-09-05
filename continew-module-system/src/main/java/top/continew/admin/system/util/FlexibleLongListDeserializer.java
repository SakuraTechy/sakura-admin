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

package top.continew.admin.system.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 灵活的 Long 列表反序列化器
 * <p>
 * 处理前端发送的混合类型数组（字符串、整数、长整数），统一转换为 Long 类型。
 * 支持 JSON 数组中的多种数值类型，确保类型安全。
 * 主要用于解决 JavaScript 大整数精度丢失问题：当菜单 ID 超过 Number.MAX_SAFE_INTEGER 时，
 * 前端必须将 ID 作为字符串发送，此反序列化器将字符串正确转换为 Long。
 * </p>
 *
 * @author Charles7c
 * @since 2026/9/5 10:00
 */
public class FlexibleLongListDeserializer extends JsonDeserializer<List<Long>> {

    @Override
    public List<Long> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        List<Long> result = new ArrayList<>();

        if (!node.isArray()) {
            throw new IllegalArgumentException("Expected an array for menuIds, but got: " + node.getNodeType());
        }

        for (JsonNode element : node) {
            if (element.isNull()) {
                // Skip null values
                continue;
            }

            Long value = convertToLong(element);
            if (value != null) {
                result.add(value);
            }
        }

        return result;
    }

    /**
     * 将 JsonNode 转换为 Long 类型
     *
     * @param node JSON 节点
     * @return Long 值，如果无法转换则返回 null
     */
    private Long convertToLong(JsonNode node) {
        try {
            if (node.isNumber()) {
                // Handle numeric types (Integer, Long, etc.)
                return node.asLong();
            } else if (node.isTextual()) {
                // Handle string types - parse to Long
                // 这是关键：支持字符串格式的大整数，避免 JavaScript 精度丢失
                String text = node.asText().trim();
                if (text.isEmpty()) {
                    return null;
                }
                return Long.parseLong(text);
            } else {
                throw new IllegalArgumentException("Cannot convert node type " + node
                    .getNodeType() + " with value '" + node + "' to Long");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format for menuId: '" + node
                .asText() + "'. Expected a valid Long value.", e);
        }
    }
}
