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

package top.continew.admin.automation.support.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * AI 变量提取规则的正则校验器。
 *
 * <p>使用 Java 标准库进行正则编译和安全验证（RE2/J 当前不可用）。
 * 只接受受限的正则子集：简单字符类、单层捕获组、明确分隔符、\s/\S 等基本元字符。
 * 拒绝：回溯引用、前后向断言、命名组、递归、嵌套重复等高复杂度特性。</p>
 *
 * <p>校验内容包括：
 * <ul>
 * <li>正则语法是否可编译</li>
 * <li>是否包含不安全的正则特性</li>
 * <li>捕获组数量与目标组索引的有效性</li>
 * <li>使用样本文本进行实际匹配测试</li>
 * </ul>
 * </p>
 */
@Component
public class VariableExtractRuleValidator {

    // 拒绝的正则特性：回溯引用、断言、命名组、条件、递归等
    private static final Pattern UNSAFE_FEATURES = Pattern
        .compile("\\\\[0-9]|\\(\\?[=!<]|\\(\\?<[^>]+>|\\(\\?\\(|\\(\\?P|\\(\\?R|\\(\\?&|\\(\\*");

    // 检测嵌套量词（如 (a+)+、(x*)*），可能导致灾难性回溯
    private static final Pattern NESTED_QUANTIFIER = Pattern.compile("\\([^)]*[*+?][^)]*\\)[*+?]");

    /**
     * 校验正则模式及目标捕获组的有效性，并使用样本值进行实际匹配测试。
     *
     * @param pattern  正则表达式
     * @param group    目标捕获组索引（从 1 开始）
     * @param rawValue 样本文本（用于测试匹配）
     * @return 校验结果
     */
    public ValidationResult validatePattern(String pattern, int group, String rawValue) {
        if (StringUtils.isBlank(pattern)) {
            return ValidationResult.fail("正则表达式不能为空");
        }

        if (group < 1) {
            return ValidationResult.fail("捕获组索引必须 >= 1");
        }

        // 检查不安全的正则特性
        String unsafeReason = detectUnsafeFeatures(pattern);
        if (unsafeReason != null) {
            return ValidationResult.fail(unsafeReason);
        }

        // 尝试编译正则表达式
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return ValidationResult.fail("正则语法错误: " + e.getMessage());
        }

        // 验证捕获组数量
        int groupCount = countCapturingGroups(pattern);
        if (group > groupCount) {
            return ValidationResult.fail(String.format("捕获组索引 %d 超出范围，该正则只有 %d 个捕获组", group, groupCount));
        }

        // 使用样本值进行匹配测试
        if (StringUtils.isNotBlank(rawValue)) {
            MatchResult matchResult = testMatch(compiled, group, rawValue);
            if (!matchResult.matched()) {
                return ValidationResult.fail("样本文本匹配失败: " + matchResult.reason());
            }
            return ValidationResult.success(matchResult.extractedValue());
        }

        return ValidationResult.success(null);
    }

    /**
     * 检测不安全的正则特性。
     *
     * @param pattern 正则表达式
     * @return 不安全原因，若安全则返回 null
     */
    private String detectUnsafeFeatures(String pattern) {
        if (UNSAFE_FEATURES.matcher(pattern).find()) {
            return "正则包含不支持的特性：回溯引用(\\1)、断言(?=)、命名组(?<name>)、条件(?()等";
        }

        if (NESTED_QUANTIFIER.matcher(pattern).find()) {
            return "正则包含嵌套量词(如 (a+)+)，可能导致性能问题";
        }

        // 检测过长的正则（简单长度限制）
        if (pattern.length() > 500) {
            return "正则表达式过长（超过 500 字符）";
        }

        return null;
    }

    /**
     * 统计正则中的捕获组数量（不含非捕获组）。
     *
     * @param pattern 正则表达式
     * @return 捕获组数量
     */
    private int countCapturingGroups(String pattern) {
        int count = 0;
        boolean inCharClass = false;
        boolean escaped = false;

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '[') {
                inCharClass = true;
                continue;
            }

            if (c == ']') {
                inCharClass = false;
                continue;
            }

            if (inCharClass) {
                continue;
            }

            // 检测左括号，排除非捕获组 (?:...)
            if (c == '(') {
                if (i + 2 < pattern.length() && pattern.charAt(i + 1) == '?' && pattern.charAt(i + 2) == ':') {
                    // 非捕获组，跳过
                    continue;
                }
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '?') {
                    // 其他特殊组（如 (?=)、(?!)），不计数
                    continue;
                }
                count++;
            }
        }

        return count;
    }

    /**
     * 使用编译后的正则对样本文本进行匹配测试。
     *
     * @param compiled 编译后的正则
     * @param group    目标捕获组索引
     * @param rawValue 样本文本
     * @return 匹配结果
     */
    private MatchResult testMatch(Pattern compiled, int group, String rawValue) {
        try {
            Matcher matcher = compiled.matcher(rawValue);
            if (!matcher.find()) {
                return new MatchResult(false, "正则未能匹配到样本文本", null);
            }

            if (group > matcher.groupCount()) {
                return new MatchResult(false, String.format("捕获组索引 %d 超出实际匹配组数 %d", group, matcher.groupCount()), null);
            }

            String extractedValue = matcher.group(group);
            if (extractedValue == null) {
                return new MatchResult(false, String.format("捕获组 %d 匹配结果为 null", group), null);
            }

            return new MatchResult(true, null, extractedValue);
        } catch (Exception e) {
            return new MatchResult(false, "匹配执行异常: " + e.getMessage(), null);
        }
    }

    /**
     * 校验结果。
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String extractedValue;

        private ValidationResult(boolean valid, String errorMessage, String extractedValue) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.extractedValue = extractedValue;
        }

        public static ValidationResult success(String extractedValue) {
            return new ValidationResult(true, null, extractedValue);
        }

        public static ValidationResult fail(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getExtractedValue() {
            return extractedValue;
        }

        public List<String> getErrorMessages() {
            List<String> errors = new ArrayList<>();
            if (!valid && StringUtils.isNotBlank(errorMessage)) {
                errors.add(errorMessage);
            }
            return errors;
        }
    }

    /**
     * 匹配结果（内部使用）。
     */
    private record MatchResult(boolean matched, String reason, String extractedValue) {
    }
}
