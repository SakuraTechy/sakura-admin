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

package top.continew.admin.test.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TestMetricServiceImplTest {

    @Test
    void shouldCalculateLegacyLaborEstimateFromSceneExecutions() {
        assertThat(TestMetricServiceImpl.standardLaborDays(58)).isEqualByComparingTo(new BigDecimal("0.83"));
        assertThat(TestMetricServiceImpl.standardLaborDays(0)).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void shouldCalculateLegacyExecutionRateFromDuration() {
        assertThat(TestMetricServiceImpl.executionRate(100, 7_200_000)).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(TestMetricServiceImpl.executionRate(100, 0)).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void shouldCalculateLegacyFailureRateWithVisibleZeroDenominator() {
        assertThat(TestMetricServiceImpl.percent(25, 100)).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(TestMetricServiceImpl.percent(0, 2)).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(TestMetricServiceImpl.percent(1, 0)).isEqualByComparingTo(new BigDecimal("0.00"));
    }
}
