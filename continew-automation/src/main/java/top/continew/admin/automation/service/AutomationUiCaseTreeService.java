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

package top.continew.admin.automation.service;

import top.continew.admin.automation.model.req.AutomationUiTreeCopyReq;
import top.continew.admin.automation.model.req.AutomationUiTreeDeleteReq;
import top.continew.admin.automation.model.req.AutomationUiTreeMoveReq;
import top.continew.admin.automation.model.resp.AutomationUiTreeMutationResp;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

/** 场景用例树的唯一结构写入口。 */
public interface AutomationUiCaseTreeService {
    AutomationUiTreeMutationResp addCase(Long sceneDbId, CaseDO caseDO);

    AutomationUiTreeMutationResp updateCase(Long sceneDbId, CaseDO caseDO);

    AutomationUiTreeMutationResp addStep(Long sceneDbId, StepDO stepDO);

    AutomationUiTreeMutationResp updateStep(Long sceneDbId, StepDO stepDO);

    /** 兼容旧接口的删除入口，内部仍走版本校验和统一树变更。 */
    AutomationUiTreeMutationResp deleteLegacyCase(Long sceneDbId, CaseDO caseDO);

    AutomationUiTreeMutationResp deleteLegacyStep(Long sceneDbId, StepDO stepDO);

    /** 兼容旧接口的拖拽入口，内部转换为统一树落点语义。 */
    AutomationUiTreeMutationResp moveLegacyCase(Long sceneDbId, CaseDO caseDO);

    AutomationUiTreeMutationResp moveLegacyStep(Long sceneDbId, StepDO stepDO);

    AutomationUiTreeMutationResp copy(Long sceneDbId, AutomationUiTreeCopyReq req);

    AutomationUiTreeMutationResp move(Long sceneDbId, AutomationUiTreeMoveReq req);

    AutomationUiTreeMutationResp delete(Long sceneDbId, AutomationUiTreeDeleteReq req);
}
