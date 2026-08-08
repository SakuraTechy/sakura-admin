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

import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskCreateReq;
import top.continew.admin.automation.model.req.infrastructure.AutomationInfrastructureTaskDispositionReq;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureStatementResp;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureTaskResp;
import top.continew.admin.automation.model.resp.infrastructure.AutomationInfrastructureTargetResp;

import java.util.List;

/** 基础设施步骤异步任务控制面。 */
public interface AutomationInfrastructureTaskService {
    List<AutomationInfrastructureTargetResp> listTargets(Long projectId, String kind);

    AutomationInfrastructureTaskResp create(AutomationInfrastructureTaskCreateReq req);

    AutomationInfrastructureTaskResp get(String taskId, Long afterSequence);

    AutomationInfrastructureTaskResp get(String taskId, Long afterSequence, String executionCapability);

    AutomationInfrastructureStatementResp getStatement(String taskId);

    AutomationInfrastructureTaskResp cancel(String taskId);

    AutomationInfrastructureTaskResp cancel(String taskId, String executionCapability);

    AutomationInfrastructureTaskResp disposeUnknownOutcome(String taskId,
                                                           AutomationInfrastructureTaskDispositionReq req,
                                                           String executionCapability);

    ArtifactDownload downloadArtifact(String taskId, String executionCapability);

    record ArtifactDownload(String fileName, String contentType, byte[] bytes, String sha256) {
    }

}
