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

package top.continew.admin.automation.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.starter.core.exception.BusinessException;

class AutomationUiCaseReviewReviewerValidatorTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void allReviewersMustExistAndBeEnabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AutomationUiCaseReviewReviewerValidator validator = new AutomationUiCaseReviewReviewerValidator(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(10L, 11L));

        assertThatCode(() -> validator.requireActive(List.of(10L, 11L))).doesNotThrowAnyException();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(10L));
        assertThatThrownBy(() -> validator.requireActive(List.of(10L, 11L))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_REVIEWER_INVALID")
            .hasMessageContaining("11");
    }
}
