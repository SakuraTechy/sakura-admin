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

package top.continew.admin.automation.model.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import top.continew.admin.automation.model.enums.AutomationUiExecutionEngineEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Request payload for executing UI automation scenes.
 */
@Data
@Schema(description = "Request payload for executing UI automation scenes")
public class AutomationUiSceneExecReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "Scene IDs must not be empty")
    @Schema(description = "Scene ID list", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> sceneIds;

    @NotNull(message = "Project environment ID must not be null")
    @Schema(description = "Project environment ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectEnvironmentId;

    @NotNull(message = "Automation environment ID must not be null")
    @Schema(description = "Automation environment ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long automationEnvironmentId;

    @Schema(description = "Execution engine. Default keeps the existing Jenkins flow")
    private AutomationUiExecutionEngineEnum engine = AutomationUiExecutionEngineEnum.JENKINS;

    @Schema(description = "Executor name")
    private String executeName;

    @Schema(description = "Executor email")
    private String executeEmail;

    @Schema(description = "Test plan ID")
    private String testPlanId;

    @Schema(description = "Test report ID")
    private String testReportId;

    @Size(max = 1000)
    @Schema(description = "评审管理员放行原因；仅项目开启强制门禁且当前版本未批准时使用")
    private String reviewGateBypassReason;

    /** 仅服务间调用可设置，HTTP 请求无法绑定该内部授权。 */
    @JsonIgnore
    private boolean reviewGateBypassAuthorized;
}
