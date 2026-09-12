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

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.support.ai.VariableExtractRuleValidator.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VariableExtractRuleValidator 单元测试
 */
class VariableExtractRuleValidatorTest {

    private final VariableExtractRuleValidator validator = new VariableExtractRuleValidator();

    @Test
    void testValidPattern_InitialPassword() {
        String pattern = "初始密码\\s*[:：]\\s*(\\S+)";
        String sample = "初始密码：Admin@123";
        ValidationResult result = validator.validatePattern(pattern, 1, sample);

        assertTrue(result.isValid());
        assertEquals("Admin@123", result.getExtractedValue());
    }

    @Test
    void testValidPattern_SimpleCapture() {
        String pattern = "用户名：([a-zA-Z0-9]+)";
        String sample = "用户名：testuser";
        ValidationResult result = validator.validatePattern(pattern, 1, sample);

        assertTrue(result.isValid());
        assertEquals("testuser", result.getExtractedValue());
    }

    @Test
    void testValidPattern_MultipleGroups() {
        String pattern = "(\\w+)\\s*=\\s*([\\d.]+)";
        String sample = "price = 99.99";
        ValidationResult result = validator.validatePattern(pattern, 2, sample);

        assertTrue(result.isValid());
        assertEquals("99.99", result.getExtractedValue());
    }

    @Test
    void testEmptyPattern() {
        ValidationResult result = validator.validatePattern("", 1, "test");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("不能为空"));
    }

    @Test
    void testInvalidGroupIndex() {
        ValidationResult result = validator.validatePattern("(\\d+)", 0, "123");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("必须 >= 1"));
    }

    @Test
    void testGroupIndexOutOfRange() {
        String pattern = "(\\d+)";
        ValidationResult result = validator.validatePattern(pattern, 2, "123");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("超出范围"));
    }

    @Test
    void testUnsafeFeature_Backreference() {
        String pattern = "(\\w+)\\1";
        ValidationResult result = validator.validatePattern(pattern, 1, "testtest");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("回溯引用"));
    }

    @Test
    void testUnsafeFeature_Lookahead() {
        String pattern = "(?=\\w+)test";
        ValidationResult result = validator.validatePattern(pattern, 1, "test");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("不支持的特性"));
    }

    @Test
    void testUnsafeFeature_NamedGroup() {
        String pattern = "(?<name>\\w+)";
        ValidationResult result = validator.validatePattern(pattern, 1, "test");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("命名组"));
    }

    @Test
    void testUnsafeFeature_NestedQuantifier() {
        String pattern = "(a+)+";
        ValidationResult result = validator.validatePattern(pattern, 1, "aaa");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("嵌套量词"));
    }

    @Test
    void testPatternTooLong() {
        String pattern = "a".repeat(501);
        ValidationResult result = validator.validatePattern(pattern, 1, "a");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("过长"));
    }

    @Test
    void testInvalidSyntax() {
        String pattern = "[unclosed";
        ValidationResult result = validator.validatePattern(pattern, 1, "test");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("语法错误"));
    }

    @Test
    void testSampleNoMatch() {
        String pattern = "(\\d+)";
        String sample = "no numbers here";
        ValidationResult result = validator.validatePattern(pattern, 1, sample);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("未能匹配"));
    }

    @Test
    void testValidPatternNoSample() {
        String pattern = "(\\d+)";
        ValidationResult result = validator.validatePattern(pattern, 1, null);

        assertTrue(result.isValid());
        assertNull(result.getExtractedValue());
    }

    @Test
    void testNonCapturingGroupNotCounted() {
        String pattern = "(?:\\w+)-(\\d+)";
        String sample = "test-123";
        ValidationResult result = validator.validatePattern(pattern, 1, sample);

        assertTrue(result.isValid());
        assertEquals("123", result.getExtractedValue());
    }

    @Test
    void testChineseColon() {
        String pattern = "密码[:：](\\S+)";
        String sample = "密码：pass123";
        ValidationResult result = validator.validatePattern(pattern, 1, sample);

        assertTrue(result.isValid());
        assertEquals("pass123", result.getExtractedValue());
    }
}
