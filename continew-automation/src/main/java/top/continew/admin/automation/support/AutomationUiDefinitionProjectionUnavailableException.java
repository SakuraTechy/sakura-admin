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

/** 投影未就绪的受控信号；不得携带 case_list 或原始节点 ID。 */
public class AutomationUiDefinitionProjectionUnavailableException extends RuntimeException {

    private final boolean retryable;
    private final String errorId;
    private final Long sceneDbId;
    private final Long definitionVersion;
    private final String projectionStatus;

    public AutomationUiDefinitionProjectionUnavailableException(boolean retryable, String errorId) {
        this(retryable, errorId, null, null, retryable ? "queued" : "failed");
    }

    public AutomationUiDefinitionProjectionUnavailableException(boolean retryable,
                                                                String errorId,
                                                                Long sceneDbId,
                                                                Long definitionVersion,
                                                                String projectionStatus) {
        super(retryable ? "DEFINITION_PROJECTION_PENDING" : "DEFINITION_PROJECTION_UNAVAILABLE");
        this.retryable = retryable;
        this.errorId = errorId;
        this.sceneDbId = sceneDbId;
        this.definitionVersion = definitionVersion;
        this.projectionStatus = projectionStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getErrorId() {
        return errorId;
    }

    public Long getSceneDbId() {
        return sceneDbId;
    }

    public Long getDefinitionVersion() {
        return definitionVersion;
    }

    public String getProjectionStatus() {
        return projectionStatus;
    }
}
