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

package top.continew.admin.test.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.admin.test.service.TestMetricAggregationService;

import java.time.LocalDate;

/**
 * 每日重算最近窗口，兼顾迟到回调和失败重试。
 */
@Component
@RequiredArgsConstructor
public class TestMetricAggregationJob {

    private static final String EXECUTOR_NAME = "AggregateTestMetrics";
    private static final String BACKFILL_EXECUTOR_NAME = "BackfillTestMetrics";

    private final TestMetricAggregationService aggregationService;

    @Value("${test.metric.aggregation.recompute-days:3}")
    private int recomputeDays;

    @Value("${test.metric.aggregation.backfill-days:30}")
    private int backfillDays;

    @JobExecutor(name = EXECUTOR_NAME)
    public void aggregate() {
        LocalDate today = LocalDate.now();
        int days = Math.max(1, Math.min(recomputeDays, 30));
        for (int offset = 0; offset < days; offset++) {
            aggregationService.aggregateDay(today.minusDays(offset));
        }
        SnailJobLog.REMOTE.info("测试度量聚合完成，重算天数={}", days);
    }

    @JobExecutor(name = BACKFILL_EXECUTOR_NAME)
    public void backfill() {
        LocalDate today = LocalDate.now();
        int days = Math.max(1, Math.min(backfillDays, 730));
        aggregationService.backfill(today.minusDays(days - 1L), today);
        SnailJobLog.REMOTE.info("测试度量回填完成，回填天数={}", days);
    }
}
