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

package top.continew.admin.test.model.resp;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TestMetricResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long versionId;
    private Long testPlanCount;
    private Long testReportCount;
    private Long timedTaskCount;
    private Long sceneCount;
    private Long passedSceneCount;
    private BigDecimal automationPassRate;
    private ModuleMetric moduleMetric;
    private SceneMetric sceneMetric;
    private ExecutionMetric executionMetric;

    @Data
    public static class ModuleMetric implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long totalCount;
        private Long weekAddedCount;
        private Long monthAddedCount;
        private Long yearAddedCount;
    }

    @Data
    public static class SceneMetric implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long totalCount;
        private Long p0Count;
        private Long p1Count;
        private Long p2Count;
        private Long p3Count;
        private Long weekAddedCount;
        private Long monthAddedCount;
        private Long yearAddedCount;
        private Long executedCount;
        private Long passedCount;
        private Long failedCount;
        private Long skippedCount;
    }

    @Data
    public static class ExecutionMetric implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long totalReportCount;
        private Long weekRunCount;
        private Long monthRunCount;
        private Long yearRunCount;
        private Long totalRunSceneCount;
        private Long discoveredDefectCount;
        private BigDecimal savedManHours;
        private BigDecimal automationCoverageRate;
        private BigDecimal automationExecuteRate;
        private BigDecimal automationPassRate;
        private BigDecimal defectRate;
    }
}
