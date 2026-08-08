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

import java.util.Optional;
import java.util.Set;

import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorCapabilityReq;

/**
 * UI 自动化操作能力目录服务。
 *
 * @author Codex
 */
public interface AutomationOperationCatalogService {

    default AutomationOperationCatalog getCatalog(Long sceneId,
                                                  Long projectEnvironmentId,
                                                  String principalScope,
                                                  String sessionId,
                                                  boolean canAddStep,
                                                  boolean canExecuteInfrastructure,
                                                  Set<String> agentTypes,
                                                  Set<String> agentFeatures) {
        return getCatalog(sceneId, projectEnvironmentId, null, principalScope, sessionId, canAddStep, canExecuteInfrastructure, agentTypes, agentFeatures);
    }

    AutomationOperationCatalog getCatalog(Long sceneId,
                                          Long projectEnvironmentId,
                                          String executorInstanceId,
                                          String principalScope,
                                          String sessionId,
                                          boolean canAddStep,
                                          boolean canExecuteInfrastructure,
                                          Set<String> agentTypes,
                                          Set<String> agentFeatures);

    Optional<AutomationOperationCatalog.OperationMethod> findMethod(String methodCodeOrAction);

    Optional<OperationDescriptor> findOperation(String methodCodeOrAction);

    void registerCapabilities(String executorType, String principalScope, AutomationExecutorCapabilityReq req);

    record OperationDescriptor(String typeCode, String typeLabel, AutomationOperationCatalog.OperationMethod method) {
    }
}
