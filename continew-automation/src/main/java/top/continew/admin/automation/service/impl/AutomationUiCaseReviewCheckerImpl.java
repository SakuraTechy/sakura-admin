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

package top.continew.admin.automation.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationUiCaseReviewChecker;
import top.continew.admin.common.enums.StatusTypeEnum;

/** Deterministic, bounded checks over a frozen case definition. */
@Service
public class AutomationUiCaseReviewCheckerImpl implements AutomationUiCaseReviewChecker {

    private static final Set<String> RESILIENT_LOCATORS = Set.of("role", "text", "label", "testid", "test-id");

    @Override
    public List<Result> check(CaseDO caseDO, ExecutionFacts executionFacts) {
        List<StepDO> steps = caseDO.getStepList() == null ? List.of() : caseDO.getStepList();
        List<StepDO> enabled = steps.stream()
            .filter(step -> step != null && !StatusTypeEnum.DISABLE.equals(step.getStatus()))
            .toList();
        List<Result> results = new ArrayList<>();
        results.add(simple("CASE_HAS_ENABLED_STEPS", enabled.isEmpty() ? "FAIL" : "PASS", "BLOCKER", enabled.isEmpty()
            ? "用例没有可执行步骤"
            : "用例包含可执行步骤", null));
        boolean hasAssertion = enabled.stream().anyMatch(this::isAssertion);
        results.add(simple("CASE_HAS_ASSERTION", hasAssertion ? "PASS" : "WARNING", "MAJOR", hasAssertion
            ? "用例包含结果断言"
            : "用例没有明确的结果断言", null));

        List<StepDO> missingRaw = enabled.stream()
            .filter(this::isRecorded)
            .filter(step -> StringUtils.isBlank(config(step, "playwright_step")))
            .toList();
        results
            .add(stepsResult("PLAYWRIGHT_STEP_PRESERVED", missingRaw, "BLOCKER", "录制步骤原始事实已完整保留", "录制步骤缺少 playwright_step"));

        List<StepDO> missingLocator = enabled.stream()
            .filter(this::requiresLocator)
            .filter(step -> StringUtils.isBlank(config(step, "locator_meta")))
            .toList();
        results
            .add(stepsResult("LOCATOR_META_PRESERVED", missingLocator, "BLOCKER", "定位元数据已完整保留", "目标步骤缺少 locator_meta"));

        List<StepDO> customSteps = enabled.stream().filter(this::isCustom).toList();
        List<StepDO> invalidCustom = customSteps.stream()
            .filter(step -> !"pw-custom".equalsIgnoreCase(StringUtils.defaultString(step
                .getOperationName())) || StringUtils.isBlank(config(step, "playwright_step")))
            .toList();
        if (!invalidCustom.isEmpty()) {
            results.add(result("CUSTOM_ACTION_PRESERVED", "FAIL", "BLOCKER", "自定义动作未按 pw-custom 完整保留", invalidCustom));
        } else if (!customSteps.isEmpty()) {
            results.add(result("CUSTOM_ACTION_PRESERVED", "WARNING", "MAJOR", "自定义动作事实已保留，仍需人工确认兼容性", customSteps));
        } else {
            results.add(simple("CUSTOM_ACTION_PRESERVED", "PASS", "MAJOR", "没有发现自定义动作", null));
        }

        List<StepDO> inlineScreenshots = enabled.stream().filter(this::hasInlineScreenshot).toList();
        results.add(stepsResult("SCREENSHOT_NOT_INLINE", inlineScreenshots, "BLOCKER", "定义中没有内联截图", "步骤定义包含内联截图数据"));

        List<StepDO> maskedLeaks = enabled.stream().filter(this::maskedValueExposed).toList();
        results.add(stepsResult("MASKED_VALUE_NOT_EXPOSED", maskedLeaks, "BLOCKER", "敏感输入使用受控展示标记", "敏感输入缺少受控展示标记"));

        List<StepDO> fragileLocators = enabled.stream().filter(this::hasOnlyStructuralLocator).toList();
        results.add(stepsWarning("LOCATOR_RESILIENCE", fragileLocators, "MAJOR", "定位器包含可维护候选", "步骤仅使用 CSS/XPath 结构定位"));

        List<StepDO> fixedWaits = enabled.stream().filter(this::isLongFixedWait).toList();
        results.add(stepsWarning("FIXED_WAIT_RISK", fixedWaits, "MINOR", "没有发现长时间固定等待", "步骤使用了较长的固定等待"));

        List<StepDO> hardcoded = enabled.stream().filter(this::hasHardcodedEnvironmentValue).toList();
        if (caseDO.getExecutionConfig() != null && isAbsoluteEnvironmentValue(caseDO.getExecutionConfig()
            .getStartUrl())) {
            hardcoded = new ArrayList<>(hardcoded);
            hardcoded.add(null);
        }
        results.add(stepsWarning("ENVIRONMENT_HARDCODED", hardcoded, "MINOR", "没有发现明显的环境硬编码", "定义包含 URL、账号或绝对路径硬编码"));

        results.add(simple("CURRENT_REVISION_VALIDATED", executionFacts.exactSuccess()
            ? "PASS"
            : "WARNING", "MAJOR", executionFacts.exactSuccess() ? "当前评审版本已有成功执行证据" : "当前评审版本暂无成功执行证据", Map
                .of("sampleSize", executionFacts.sampleSize(), "lastResult", StringUtils.defaultString(executionFacts
                    .lastResult(), "none"))));
        boolean flaky = executionFacts.sampleSize() >= 2 && executionFacts.failedOrRetried() > 0;
        results.add(simple("RECENT_FLAKY_RISK", flaky ? "WARNING" : "PASS", "MAJOR", flaky
            ? "近期执行存在失败或重试趋势"
            : "近期样本未发现失败或重试趋势", Map.of("sampleSize", executionFacts.sampleSize(), "failedOrRetried", executionFacts
                .failedOrRetried())));
        return results;
    }

    private Result stepsResult(String code, List<StepDO> affected, String severity, String pass, String fail) {
        return result(code, affected.isEmpty() ? "PASS" : "FAIL", severity, affected.isEmpty() ? pass : fail, affected);
    }

    private Result stepsWarning(String code, List<StepDO> affected, String severity, String pass, String warning) {
        return result(code, affected.isEmpty() ? "PASS" : "WARNING", severity, affected.isEmpty()
            ? pass
            : warning, affected);
    }

    private Result result(String code, String result, String severity, String message, List<StepDO> affected) {
        List<Map<String, Object>> anchors = new ArrayList<>();
        if (affected != null) {
            affected.stream()
                .filter(java.util.Objects::nonNull)
                .limit(20)
                .forEach(step -> anchors.add(Map.of("nodeType", "STEP", "stepId", StringUtils.defaultString(step
                    .getId()), "stepName", StringUtils.defaultString(step.getName()))));
        }
        return new Result(code, result, severity, message + (anchors.isEmpty()
            ? ""
            : "（" + anchors.size() + " 项）"), anchors, Map.of("affectedCount", anchors.size()));
    }

    private Result simple(String code, String result, String severity, String message, Map<String, Object> evidence) {
        return new Result(code, result, severity, message, List.of(), evidence == null ? Map.of() : evidence);
    }

    private boolean isAssertion(StepDO step) {
        String text = (StringUtils.defaultString(step.getOperationType()) + " " + StringUtils.defaultString(step
            .getOperationName()) + " " + StringUtils.defaultString(step.getType())).toLowerCase(Locale.ROOT);
        return text.contains("assert") || text.contains("expect") || text.contains("断言") || text.contains("校验");
    }

    private boolean isRecorded(StepDO step) {
        return "sakura-playwright"
            .equalsIgnoreCase(config(step, "source")) || config(step, "original_playwright_step") != null;
    }

    private boolean requiresLocator(StepDO step) {
        String action = StringUtils.defaultString(step.getOperationName()).toLowerCase(Locale.ROOT);
        return isRecorded(step) && !Set.of("goto", "navigate", "wait", "reload", "pw-custom").contains(action);
    }

    private boolean isCustom(StepDO step) {
        String action = StringUtils.defaultString(step.getOperationName()).toLowerCase(Locale.ROOT);
        return action.contains("custom") || "unknown".equals(action);
    }

    private boolean hasInlineScreenshot(StepDO step) {
        if (step.getConfigList() == null)
            return false;
        return step.getConfigList().stream().filter(java.util.Objects::nonNull).anyMatch(config -> {
            String key = StringUtils.defaultString(config.getParamsName()).toLowerCase(Locale.ROOT);
            String value = StringUtils.defaultString(config.getParamsValue()).toLowerCase(Locale.ROOT);
            return key.contains("screenshot_base64") || key.contains("screenshot_data") || value
                .contains("data:image/");
        });
    }

    private boolean maskedValueExposed(StepDO step) {
        return "1".equals(config(step, "value_masked")) && StringUtils.isNotBlank(step.getOperationValue()) && !"******"
            .equals(step.getOperationValue()) && StringUtils.isBlank(config(step, "value"));
    }

    private boolean hasOnlyStructuralLocator(StepDO step) {
        String raw = config(step, "locator_meta");
        if (StringUtils.isBlank(raw))
            return false;
        String normalized = raw.toLowerCase(Locale.ROOT);
        boolean resilient = RESILIENT_LOCATORS.stream().anyMatch(normalized::contains);
        return !resilient && (normalized.contains("css") || normalized.contains("xpath"));
    }

    private boolean isLongFixedWait(StepDO step) {
        String action = StringUtils.defaultString(step.getOperationName()).toLowerCase(Locale.ROOT);
        if (!action.contains("wait") && !action.contains("sleep"))
            return false;
        String value = StringUtils.firstNonBlank(step
            .getOperationValue(), config(step, "timeout"), config(step, "value"));
        try {
            return Long.parseLong(StringUtils.defaultString(value).replaceAll("[^0-9]", "")) > 2000;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean hasHardcodedEnvironmentValue(StepDO step) {
        if (isAbsoluteEnvironmentValue(step.getOperationValue()))
            return true;
        if (step.getConfigList() == null)
            return false;
        return step.getConfigList()
            .stream()
            .filter(java.util.Objects::nonNull)
            .anyMatch(config -> isAbsoluteEnvironmentValue(config.getParamsValue()));
    }

    private boolean isAbsoluteEnvironmentValue(String value) {
        String text = StringUtils.defaultString(value).trim();
        return text.matches("(?i)^https?://.+") || text.matches("^[A-Za-z]:\\\\.+") || text.startsWith("/home/");
    }

    private String config(StepDO step, String name) {
        if (step.getConfigList() == null)
            return null;
        return step.getConfigList()
            .stream()
            .filter(java.util.Objects::nonNull)
            .filter(item -> name.equals(item.getParamsName()))
            .map(StepDO.Config::getParamsValue)
            .findFirst()
            .orElse(null);
    }
}
