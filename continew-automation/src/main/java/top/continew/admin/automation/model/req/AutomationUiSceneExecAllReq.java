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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.continew.admin.automation.model.enums.AutomationUiExecutionEngineEnum;
import top.continew.admin.common.enums.StatusTypeEnum;

/**
 * 执行查询范围内全部 UI 自动化场景请求参数。
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Data
@Schema(description = "执行全部 UI 自动化场景请求参数")
public class AutomationUiSceneExecAllReq {

    @Schema(description = "所属项目 ID")
    @NotNull(message = "所属项目 ID 不能为空")
    private Long projectId;

    @Schema(description = "所属版本 ID")
    @NotNull(message = "所属版本 ID 不能为空")
    private Long versionId;

    @Schema(description = "所属模块 ID")
    private Long moduleId;

    @Schema(description = "场景等级")
    private String level;

    @Schema(description = "执行状态")
    private String executeStatus;

    @Schema(description = "执行结果")
    private String executeResult;

    @Schema(description = "场景状态")
    private StatusTypeEnum status;

    @Schema(description = "产品环境 ID")
    @NotNull(message = "产品环境 ID 不能为空")
    private Long projectEnvironmentId;

    @Schema(description = "自动化环境 ID")
    @NotNull(message = "自动化环境 ID 不能为空")
    private Long automationEnvironmentId;

    @Schema(description = "执行引擎，默认保持现有 Jenkins 链路")
    private AutomationUiExecutionEngineEnum engine = AutomationUiExecutionEngineEnum.JENKINS;

    @Schema(description = "执行人")
    private String executeName;

    @Schema(description = "执行邮箱")
    private String executeEmail;

    @Schema(description = "测试计划 ID")
    private String testPlanId;

    @Schema(description = "测试报告 ID")
    private String testReportId;
}
