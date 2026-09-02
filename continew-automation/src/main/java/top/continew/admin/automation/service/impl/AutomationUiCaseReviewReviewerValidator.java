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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

/** Prevents disabled or deleted accounts from becoming unserviceable review assignments. */
@Component
@RequiredArgsConstructor
class AutomationUiCaseReviewReviewerValidator {

    private final JdbcTemplate jdbcTemplate;

    void requireActive(Collection<Long> reviewerIds) {
        Set<Long> requested = reviewerIds == null
            ? Set.of()
            : reviewerIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new BusinessException("REVIEW_REVIEWER_REQUIRED：至少选择一名评审人");
        }
        String placeholders = requested.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Long> active = jdbcTemplate
            .query("SELECT id FROM sys_user WHERE status = 1 AND id IN (" + placeholders + ")", (rs, rowNum) -> rs
                .getLong(1), requested.toArray());
        LinkedHashSet<Long> invalid = new LinkedHashSet<>(requested);
        invalid.removeAll(active);
        if (!invalid.isEmpty()) {
            throw new BusinessException("REVIEW_REVIEWER_INVALID：评审人不存在或已禁用：" + invalid);
        }
    }
}
