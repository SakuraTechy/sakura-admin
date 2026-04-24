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

package top.continew.admin.automation.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * Automation environment runtime status response.
 */
@Data
@Schema(description = "Automation environment runtime status response")
public class AutomationEnvironmentRuntimeStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Environment ID")
    private Long environmentId;

    @Schema(description = "Node name")
    private String nodeName;

    @Schema(description = "Realtime online status")
    private StatusTypeEnum onlineStatus;

    @Schema(description = "Realtime use status")
    private StatusTypeEnum useStatus;
}
