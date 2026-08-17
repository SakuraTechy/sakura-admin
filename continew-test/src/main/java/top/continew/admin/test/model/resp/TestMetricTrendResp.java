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
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "测试度量趋势")
public class TestMetricTrendResp {

    private List<TrendPoint> points = new ArrayList<>();

    @Data
    public static class TrendPoint {
        private LocalDate date;
        private long runCount;
        private long sceneExecutionCount;
        private long executedSceneCount;
        private long passCount;
        private long failCount;
        private long skipCount;
        private long cancelCount;
        private long infraFailCount;
        private long otherCount;
        private long durationTotalMs;
        private long durationSampleCount;
        private BigDecimal passRate;
    }
}
