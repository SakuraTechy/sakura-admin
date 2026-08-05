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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TestMetricAggregationServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTransactionTemplate() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void shouldReplaceWholeDayOnEveryRetry() {
        TestMetricAggregationServiceImpl service = new TestMetricAggregationServiceImpl(jdbcTemplate, transactionTemplate);
        LocalDate date = LocalDate.of(2026, 8, 1);

        service.aggregateDay(date);
        service.aggregateDay(date);

        long dailyDeletes = mockingDetails(jdbcTemplate).getInvocations()
            .stream()
            .filter(invocation -> invocation.getMethod().getName().equals("update"))
            .filter(invocation -> String.valueOf((Object)invocation.getArgument(0))
                .startsWith("DELETE FROM test_metric_daily"))
            .count();
        long dailyInserts = mockingDetails(jdbcTemplate).getInvocations()
            .stream()
            .filter(invocation -> invocation.getMethod().getName().equals("update"))
            .filter(invocation -> String.valueOf((Object)invocation.getArgument(0))
                .startsWith("INSERT INTO test_metric_daily"))
            .count();
        assertThat(dailyDeletes).isEqualTo(2);
        assertThat(dailyInserts).isEqualTo(2);
        verify(transactionTemplate, times(2)).executeWithoutResult(any());
    }

    @Test
    void shouldUseOneTransactionForDimensionsAndOneForEachDay() {
        TestMetricAggregationServiceImpl service = new TestMetricAggregationServiceImpl(jdbcTemplate, transactionTemplate);

        service.backfill(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        verify(transactionTemplate, times(4)).executeWithoutResult(any());
        long dimensionBackfills = mockingDetails(jdbcTemplate).getInvocations()
            .stream()
            .filter(invocation -> invocation.getMethod().getName().equals("update"))
            .filter(invocation -> String.valueOf((Object)invocation.getArgument(0)).startsWith("UPDATE test_plan"))
            .count();
        assertThat(dimensionBackfills).isEqualTo(1);
    }
}
