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

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.starter.core.exception.BusinessException;

/**
 * Shell 输出绑定的声明性契约。正则与替换内容中的空白有业务含义，不能使用通用 trim/非空过滤。
 */
public record AutomationServerShellResultConfig(String replaceRegex, String replaceValue, String variableName) {

    public static final int MAX_REGEX_LENGTH = 512;
    public static final int MAX_VALUE_LENGTH = 4096;
    private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]{0,127}$");
    private static final List<String> RESERVED_PREFIXES = List.of("system.", "secret.", "execution.");

    public static AutomationServerShellResultConfig from(Map<String, Object> values) {
        if (values.get("regex") != null && !String.valueOf(values.get("regex")).isEmpty()) {
            throw invalid("旧 Shell 正则提取配置不能静默转换，请保留旧步骤");
        }
        String regex = text(values, "replace_regex");
        String replacement = text(values, "replace_value");
        String variable = text(values, "variable_name").trim();
        if (regex.length() > MAX_REGEX_LENGTH || replacement.length() > MAX_VALUE_LENGTH) {
            throw invalid("替换正则最多 512 字符，替换内容最多 4096 字符");
        }
        if (!variable.isEmpty() && (!VARIABLE_NAME.matcher(variable).matches() || RESERVED_PREFIXES.stream()
            .anyMatch(variable::startsWith))) {
            throw new BusinessException("VARIABLE_NAME_INVALID：全局变量名不合法或使用了保留前缀");
        }
        if (regex.contains("${") || regex.contains("{{") || replacement.contains("${") || replacement.contains("{{")) {
            throw invalid("替换字段不支持平台变量插值或命名组引用，请使用 $1 等数字捕获组");
        }
        if (!regex.isEmpty() && variable.isEmpty()) {
            throw invalid("配置结果替换后，请填写全局变量名");
        }
        if (regex.isEmpty() && !replacement.isEmpty()) {
            throw invalid("请先填写替换正则");
        }
        if (!regex.isEmpty()) {
            try {
                validateReplacement(replacement, Pattern.compile(regex).matcher("").groupCount());
            } catch (PatternSyntaxException | StackOverflowError e) {
                throw invalid("替换正则不合法或过于复杂");
            }
        }
        return new AutomationServerShellResultConfig(regex, replacement, variable);
    }

    public boolean savesVariable() {
        return !variableName.isEmpty();
    }

    /** 旧提取规则可能只存在于执行快照；读取、保存和覆盖编辑必须检查同一份原定义。 */
    public static boolean hasLegacyExtraction(StepDO step, ObjectMapper mapper) {
        if (step == null || step.getConfigList() == null) {
            return false;
        }
        boolean shell = "exe-shell".equals(step.getOperationValue()) || "server_command".equals(step
            .getOperationValue());
        boolean extraction = false;
        for (StepDO.Config config : step.getConfigList()) {
            if (config == null || config.getParamsValue() == null) {
                continue;
            }
            String name = config.getParamsName();
            String value = config.getParamsValue();
            shell |= "method_code".equals(name) && "server.shell".equals(value);
            shell |= "action_type".equals(name) && "server_command".equals(value);
            extraction |= "regex".equals(name) && !value.isEmpty();
            if (("method_config".equals(name) || "playwright_step".equals(name)) && !value.isBlank()) {
                try {
                    JsonNode raw = mapper.readTree(value);
                    if (raw != null && raw.isObject()) {
                        boolean rawShell = "server_command".equals(raw.path("action_type").asText());
                        shell |= rawShell;
                        // 其他操作的原快照不阻止用户切换为 Shell 方法。
                        if ("method_config".equals(name) || rawShell) {
                            extraction |= raw.hasNonNull("regex") && !raw.path("regex").asText().isEmpty();
                        }
                    }
                } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                    // 非法 JSON 仍由原保存/提取校验报告，本检查不猜测或改写历史内容。
                }
            }
        }
        return shell && extraction;
    }

    public void normalize(Map<String, Object> values) {
        if (replaceRegex.isEmpty()) {
            values.remove("replace_regex");
            values.remove("replace_value");
        } else {
            values.put("replace_regex", replaceRegex);
            // 空字符串表示删除匹配内容，必须进入 method_config 和完整 playwright_step。
            values.put("replace_value", replaceValue);
        }
        if (variableName.isEmpty()) {
            values.remove("variable_name");
        } else {
            values.put("variable_name", variableName);
        }
    }

    private static String text(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw invalid(field + " 必须是字符串");
        }
        return text;
    }

    /** 提前检查 Java replacement，未匹配时也不能让错误捕获组绕过保存校验。 */
    private static void validateReplacement(String replacement, int groupCount) {
        for (int index = 0; index < replacement.length(); index++) {
            char current = replacement.charAt(index);
            if (current == '\\') {
                if (++index == replacement.length()) {
                    throw invalid("替换内容末尾不能是未转义的反斜杠");
                }
            } else if (current == '$') {
                if (++index == replacement.length() || !isDigit(replacement.charAt(index))) {
                    throw invalid("替换内容中的 $ 必须引用数字捕获组或使用反斜杠转义");
                }
                int group = replacement.charAt(index) - '0';
                if (group > groupCount) {
                    throw invalid("替换内容引用了不存在的捕获组");
                }
                while (index + 1 < replacement.length() && isDigit(replacement.charAt(index + 1))) {
                    int next = group * 10 + replacement.charAt(index + 1) - '0';
                    if (next > groupCount) {
                        break;
                    }
                    group = next;
                    index++;
                }
            }
        }
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("METHOD_CONFIG_INVALID：" + message);
    }
}
