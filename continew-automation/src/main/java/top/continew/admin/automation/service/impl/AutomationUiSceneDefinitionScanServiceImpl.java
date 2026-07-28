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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.resp.AutomationUiSceneDefinitionScanResp;
import top.continew.admin.automation.service.AutomationUiSceneDefinitionScanService;

/** 扫描重复 ID、空节点、pid/order 失配及危险 UI 字段；全程不写数据库。 */
@Service
@RequiredArgsConstructor
public class AutomationUiSceneDefinitionScanServiceImpl implements AutomationUiSceneDefinitionScanService {
    private final AutomationUiSceneMapper sceneMapper;

    @Override
    public AutomationUiSceneDefinitionScanResp scan() {
        AutomationUiSceneDefinitionScanResp result = new AutomationUiSceneDefinitionScanResp();
        List<AutomationUiSceneDO> scenes = sceneMapper.selectList(null);
        result.setSceneCount(scenes == null ? 0 : scenes.size());
        if (scenes == null)
            return result;
        for (AutomationUiSceneDO scene : scenes)
            scanScene(scene, result);
        result.setIssueCount(result.getIssues().size());
        return result;
    }

    private void scanScene(AutomationUiSceneDO scene, AutomationUiSceneDefinitionScanResp result) {
        String base = "scene.caseList";
        List<CaseDO> cases = scene.getCaseList();
        if (cases == null) {
            issue(result, scene, "CASE_LIST_NULL", base, "caseList 为 null");
            return;
        }
        Set<String> caseIds = new HashSet<>();
        for (int i = 0; i < cases.size(); i++) {
            CaseDO item = cases.get(i);
            String path = base + "[" + i + "]";
            if (item == null) {
                issue(result, scene, "CASE_NULL", path, "用例节点为 null");
                continue;
            }
            if (item.getId() == null || item.getId().isBlank())
                issue(result, scene, "CASE_ID_EMPTY", path, "用例 ID 为空");
            else if (!caseIds.add(item.getId()))
                issue(result, scene, "CASE_ID_DUPLICATE", path, "用例 ID 重复");
            if (item.getOrder() == null || item.getOrder() != i + 1)
                issue(result, scene, "CASE_ORDER_INVALID", path, "order 与列表位置不一致");
            scanSteps(scene, item, path, result);
            if (item.getDragNode() != null || item.getDropNode() != null || item.getDropPosition() != null)
                issue(result, scene, "UI_TEMP_FIELD", path, "包含拖拽临时字段");
        }
    }

    private void scanSteps(AutomationUiSceneDO scene,
                           CaseDO parent,
                           String path,
                           AutomationUiSceneDefinitionScanResp result) {
        List<StepDO> steps = parent.getStepList();
        if (steps == null) {
            issue(result, scene, "STEP_LIST_NULL", path + ".stepList", "stepList 为 null");
            return;
        }
        Set<String> stepIds = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            StepDO step = steps.get(i);
            String stepPath = path + ".stepList[" + i + "]";
            if (step == null) {
                issue(result, scene, "STEP_NULL", stepPath, "步骤节点为 null");
                continue;
            }
            if (step.getId() == null || step.getId().isBlank())
                issue(result, scene, "STEP_ID_EMPTY", stepPath, "步骤 ID 为空");
            else if (!stepIds.add(step.getId()))
                issue(result, scene, "STEP_ID_DUPLICATE", stepPath, "同一用例内步骤 ID 重复");
            if (step.getOrder() == null || step.getOrder() != i + 1)
                issue(result, scene, "STEP_ORDER_INVALID", stepPath, "order 与列表位置不一致");
            if (!java.util.Objects.equals(parent.getId(), step.getPid()))
                issue(result, scene, "STEP_PID_MISMATCH", stepPath, "pid 与所属用例不一致");
            if (step.getDragNode() != null || step.getDropNode() != null || step.getDropPosition() != null)
                issue(result, scene, "UI_TEMP_FIELD", stepPath, "包含拖拽临时字段");
            if (step.getConfigList() != null && step.getConfigList()
                .stream()
                .anyMatch(config -> config != null && ("screenshot".equals(config
                    .getParamsName()) || "screenshot_base64".equals(config.getParamsName())) && config
                        .getParamsValue() != null && config.getParamsValue().length() > 1024))
                issue(result, scene, "SCREENSHOT_INLINE", stepPath, "疑似内联截图数据");
        }
    }

    private void issue(AutomationUiSceneDefinitionScanResp result,
                       AutomationUiSceneDO scene,
                       String code,
                       String path,
                       String detail) {
        result.getIssues()
            .add(new AutomationUiSceneDefinitionScanResp.SceneIssue(scene.getId(), scene
                .getSceneId(), code, path, detail));
    }
}
