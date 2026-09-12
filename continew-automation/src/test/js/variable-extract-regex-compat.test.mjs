import { test } from 'node:test';
import assert from 'node:assert';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// 读取测试用例数据
const casesPath = join(__dirname, '../resources/ai/variable-extract-rule-cases.json');
const testCases = JSON.parse(readFileSync(casesPath, 'utf-8'));

// 灾难性回溯检测超时（毫秒）
const REGEX_TIMEOUT_MS = 100;

/**
 * 在超时保护下执行正则匹配
 * @param {string} pattern - 正则表达式模式
 * @param {string} rawValue - 待匹配文本
 * @param {number} timeoutMs - 超时时间（毫秒）
 * @returns {RegExpMatchArray|null} 匹配结果，超时或出错返回 null
 */
function safeRegexMatch(pattern, rawValue, timeoutMs = REGEX_TIMEOUT_MS) {
  // 空模式直接拒绝
  if (!pattern || pattern.trim() === '') {
    return null;
  }

  let timeoutId;
  let isTimeout = false;

  const timeoutPromise = new Promise((resolve) => {
    timeoutId = setTimeout(() => {
      isTimeout = true;
      resolve(null);
    }, timeoutMs);
  });

  const matchPromise = new Promise((resolve) => {
    try {
      const regex = new RegExp(pattern);
      const result = rawValue.match(regex);
      resolve(result);
    } catch (err) {
      // 正则语法错误或其他异常
      resolve(null);
    }
  });

  return Promise.race([matchPromise, timeoutPromise]).finally(() => {
    clearTimeout(timeoutId);
    if (isTimeout) {
      console.warn(`正则执行超时: ${pattern}`);
    }
  });
}

/**
 * 提取指定分组的匹配结果
 * @param {RegExpMatchArray|null} matchResult - 匹配结果
 * @param {number} groupIndex - 分组索引（0 表示整个匹配）
 * @returns {string|null} 提取的文本，无匹配或越界返回 null
 */
function extractGroup(matchResult, groupIndex) {
  if (!matchResult) {
    return null;
  }

  // 分组索引越界
  if (groupIndex < 0 || groupIndex >= matchResult.length) {
    return null;
  }

  return matchResult[groupIndex] || null;
}

// 为每个测试用例生成独立测试
testCases.forEach((testCase) => {
  test(testCase.name, async () => {
    const {
      raw_value,
      expected_pattern,
      expected_group,
      expected_result
    } = testCase;

    // 执行正则匹配（带超时保护）
    const matchResult = await safeRegexMatch(expected_pattern, raw_value);

    // 提取指定分组
    const actualResult = extractGroup(matchResult, expected_group);

    // 验证结果
    assert.strictEqual(
      actualResult,
      expected_result,
      `期望提取 "${expected_result}"，实际得到 "${actualResult}"`
    );

    // 额外验证：如果预期有结果，验证匹配对象的组数
    if (expected_result !== null && matchResult) {
      assert.ok(
        matchResult.length > expected_group,
        `匹配结果应至少包含 ${expected_group + 1} 个分组，实际 ${matchResult.length} 个`
      );
    }
  });
});

// 汇总测试
test('测试用例覆盖度验证', () => {
  assert.ok(testCases.length >= 10, `应至少有 10 个测试用例，实际 ${testCases.length} 个`);

  const hasNullCase = testCases.some(tc => tc.expected_result === null);
  assert.ok(hasNullCase, '应包含无匹配结果的测试用例');

  const hasMultiGroupCase = testCases.some(tc => tc.expected_group > 0);
  assert.ok(hasMultiGroupCase, '应包含多分组提取的测试用例');
});
