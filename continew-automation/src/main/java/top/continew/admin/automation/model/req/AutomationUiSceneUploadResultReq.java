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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Upload scene execution result request.
 */
@Data
@Schema(description = "Upload scene execution result request")
public class AutomationUiSceneUploadResultReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Scene primary ID")
    private Long id;

    @Schema(description = "Project name")
    private String projectName;

    @Schema(description = "Version name")
    private String versionName;

    @Schema(description = "Scene ID")
    private String sceneId;

    @Schema(description = "Test plan ID")
    private String testPlanId;

    @Schema(description = "Test report ID")
    private String testReportId;

    @Schema(description = "Scene status")
    private String status;

    @Valid
    @NotNull(message = "Statistic analysis must not be null")
    @Schema(description = "Statistic analysis", requiredMode = Schema.RequiredMode.REQUIRED)
    private StatisticAnalysis statisticAnalysis;

    @Data
    @Schema(description = "Statistic analysis")
    public static class StatisticAnalysis implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Valid
        @NotNull(message = "UI statistic analysis must not be null")
        @Schema(description = "UI statistic analysis", requiredMode = Schema.RequiredMode.REQUIRED)
        private UiStatistic ui;
    }

    @Data
    @Schema(description = "UI statistic analysis")
    public static class UiStatistic implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "Build number")
        private Integer buildNumber;

        @Schema(description = "Console URL")
        private String consoleUrl;

        @Schema(description = "Test report URL")
        private String testReportUrl;

        @Schema(description = "Executor name")
        private String executeName;

        @Schema(description = "Execute status")
        private String executeStatus;

        @Schema(description = "Execute result")
        private String executeResult;

        @Schema(description = "Duration in milliseconds")
        private String duration;

        @Schema(description = "Duration start time")
        private String durationStartTime;

        @Schema(description = "Duration end time")
        private String durationEndTime;

        @Schema(description = "Scene total")
        private Integer sceneTotal;

        @Schema(description = "Scene pass")
        private Integer scenePass;

        @Schema(description = "Scene fail")
        private Integer sceneFail;

        @Schema(description = "Scene skip")
        private Integer sceneSkip;

        @Schema(description = "Scene pass rate")
        private String scenePassRate;

        @Schema(description = "Case total")
        private Integer caseTotal;

        @Schema(description = "Case pass")
        private Integer casePass;

        @Schema(description = "Case fail")
        private Integer caseFail;

        @Schema(description = "Case skip")
        private Integer caseSkip;

        @Schema(description = "Case pass rate")
        private String casePassRate;

        @Schema(description = "Step total")
        private Integer stepTotal;

        @Schema(description = "Step pass")
        private Integer stepPass;

        @Schema(description = "Step fail")
        private Integer stepFail;

        @Schema(description = "Step skip")
        private Integer stepSkip;

        @Schema(description = "Step pass rate")
        private String stepPassRate;
    }
}
