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
import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

/** 为管理端生成定义脱敏副本，不修改 Runner 使用的原始定义。 */
@Component
public class AutomationUiDefinitionDisplayMasker {

    private static final String MASK = "******";

    public List<CaseDO> mask(List<CaseDO> sourceCases) {
        if (sourceCases == null || sourceCases.isEmpty()) {
            return List.of();
        }
        List<CaseDO> result = new ArrayList<>(sourceCases.size());
        for (CaseDO sourceCase : sourceCases) {
            if (sourceCase == null) {
                result.add(null);
                continue;
            }
            CaseDO caseCopy = BeanUtil.copyProperties(sourceCase, CaseDO.class);
            caseCopy.setStepList(copySteps(sourceCase.getStepList()));
            result.add(caseCopy);
        }
        return result;
    }

    private List<StepDO> copySteps(List<StepDO> sourceSteps) {
        if (sourceSteps == null) {
            return null;
        }
        List<StepDO> result = new ArrayList<>(sourceSteps.size());
        for (StepDO sourceStep : sourceSteps) {
            if (sourceStep == null) {
                result.add(null);
                continue;
            }
            StepDO stepCopy = BeanUtil.copyProperties(sourceStep, StepDO.class);
            List<StepDO.Config> configs = copyConfigs(sourceStep.getConfigList());
            stepCopy.setConfigList(configs);
            if (isMasked(configs)) {
                stepCopy.setOperationValue(MASK);
                maskConfigs(configs);
            }
            result.add(stepCopy);
        }
        return result;
    }

    private List<StepDO.Config> copyConfigs(List<StepDO.Config> sourceConfigs) {
        if (sourceConfigs == null) {
            return null;
        }
        return sourceConfigs.stream()
            .map(config -> config == null ? null : BeanUtil.copyProperties(config, StepDO.Config.class))
            .toList();
    }

    private boolean isMasked(List<StepDO.Config> configs) {
        return configs != null && configs.stream()
            .anyMatch(config -> config != null && "value_masked".equals(config.getParamsName()) && ("1".equals(config
                .getParamsValue()) || "true".equalsIgnoreCase(config.getParamsValue())));
    }

    private void maskConfigs(List<StepDO.Config> configs) {
        for (StepDO.Config config : configs) {
            if (config == null) {
                continue;
            }
            String name = config.getParamsName();
            if ("value".equals(name) || "operationValue".equals(name)) {
                config.setParamsValue(MASK);
            } else if ("playwright_step".equals(name) || "original_playwright_step".equals(name)) {
                config.setParamsValue(maskPlaywrightStep(config.getParamsValue()));
            }
        }
    }

    private String maskPlaywrightStep(String raw) {
        if (!JSONUtil.isTypeJSONObject(raw)) {
            return MASK;
        }
        JSONObject step = JSONUtil.parseObj(raw);
        step.set("value", MASK);
        return step.toString();
    }
}
