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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

/**
 * 在提交执行 Agent 前，将冻结步骤中实际声明的运行时变量替换为本次值。
 *
 * <p>绑定值只存在于该次请求和发给 Agent 的内存载荷中，绝不能写入任务表、任务日志或异常日志。
 * 仅允许替换原始步骤文本中出现的 {@code ${name}}，避免调用方借运行时参数向未声明字段注入数据。</p>
 */
@Component
@RequiredArgsConstructor
public class AutomationInfrastructureRuntimeBindingResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]{0,127})}");

    private final ObjectMapper objectMapper;

    /**
     * 返回深拷贝后的替换结果。未提供任一已引用变量、或提供未被当前步骤引用的变量时均拒绝执行。
     */
    public Map<String, Object> resolve(Map<String, Object> frozenStep, Map<String, Object> runtimeBindings) {
        Map<String, Object> copy = frozenStep == null
            ? new LinkedHashMap<>()
            : objectMapper.convertValue(frozenStep, MAP_TYPE);
        Set<String> references = references(copy);
        Map<String, Object> bindings = runtimeBindings == null ? Map.of() : runtimeBindings;
        for (String key : bindings.keySet()) {
            if (key == null || !references.contains(key)) {
                throw new BusinessException("runtimeBindings 包含当前步骤未引用的变量");
            }
        }
        for (String reference : references) {
            if (!bindings.containsKey(reference)) {
                throw new BusinessException("当前基础设施步骤缺少运行时变量：" + reference);
            }
        }
        return asMap(resolveValue(copy, bindings));
    }

    /** 返回步骤文本中真正出现的变量名，不扫描 Map key，避免改变字段结构。 */
    public Set<String> references(Map<String, Object> step) {
        Set<String> result = new LinkedHashSet<>();
        collectReferences(step, result);
        return result;
    }

    /** 路由字段不可变量化，否则会让单步在执行时跳转到不同目标。 */
    public void rejectVariablesInRoutingFields(Map<String, Object> step) {
        if (containsVariable(step == null ? null : step.get("action_type"))) {
            throw new BusinessException("action_type 不允许使用运行时变量");
        }
        Object targetRef = step == null ? null : step.get("target_ref");
        if (containsVariable(targetRef)) {
            throw new BusinessException("target_ref 不允许使用运行时变量");
        }
    }

    private Object resolveValue(Object source, Map<String, Object> bindings) {
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), resolveValue(entry.getValue(), bindings));
            }
            return result;
        }
        if (source instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(resolveValue(item, bindings));
            }
            return result;
        }
        if (!(source instanceof String text)) {
            return source;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        if (matcher.start() == 0 && matcher.end() == text.length()) {
            return copyBinding(bindings.get(matcher.group(1)));
        }
        StringBuffer result = new StringBuffer();
        do {
            Object value = bindings.get(matcher.group(1));
            if (value instanceof Map<?, ?> || value instanceof List<?>) {
                throw new BusinessException("嵌入文本的运行时变量只能使用标量值：" + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        } while (matcher.find());
        matcher.appendTail(result);
        return result.toString();
    }

    private Object copyBinding(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return objectMapper.convertValue(value, Object.class);
        }
        return value;
    }

    private void collectReferences(Object source, Set<String> result) {
        if (source instanceof Map<?, ?> map) {
            map.values().forEach(value -> collectReferences(value, result));
            return;
        }
        if (source instanceof List<?> list) {
            list.forEach(value -> collectReferences(value, result));
            return;
        }
        if (!(source instanceof String text)) {
            return;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
    }

    private boolean containsVariable(Object source) {
        Set<String> references = new LinkedHashSet<>();
        collectReferences(source, references);
        return !references.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new BusinessException("基础设施步骤必须是对象");
        }
        return (Map<String, Object>)map;
    }
}
