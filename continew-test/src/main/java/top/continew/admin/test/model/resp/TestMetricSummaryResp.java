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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "测试度量概览")
public class TestMetricSummaryResp {

    private Long projectId;
    private Long versionId;
    private LocalDate startDate;
    private LocalDate endDate;
    private long runCount;
    private long sceneExecutionCount;
    private long eligibleSceneCount;
    private long executedSceneCount;
    private long passCount;
    private long failCount;
    private long skipCount;
    private long cancelCount;
    private long infraFailCount;
    private long caseTotal;
    private long casePass;
    private long caseFail;
    private long caseSkip;
    private long stepTotal;
    private long stepPass;
    private long stepFail;
    private long stepSkip;
    private long averageDurationMs;
    private long exactDimensionCount;
    private long inferredDimensionCount;
    private long missingDimensionCount;
    private RateMetric passRate;
    private RateMetric executionCoverage;

    @Data
    public static class RateMetric {
        private long numerator;
        private long denominator;
        private BigDecimal rate;
        private BigDecimal previousRate;
        private BigDecimal changePoints;
    }
}
