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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.starter.core.exception.BusinessException;

class AutomationUiCaseReviewProjectAccessValidatorTest {

    private final ProjectConfigMapper projectConfigMapper = mock(ProjectConfigMapper.class);
    private final AutomationUiCaseReviewProjectAccessValidator validator = new AutomationUiCaseReviewProjectAccessValidator(projectConfigMapper);

    @AfterEach
    void clearContext() {
        UserContextHolder.clearContext();
    }

    @Test
    void projectMemberCanAccessButUnlistedUserCannot() {
        when(projectConfigMapper.selectById(1L)).thenReturn(project(7L, List.of("42")));

        setUser(42L);
        assertThatCode(() -> validator.requireAccess(1L)).doesNotThrowAnyException();
        assertThat(validator.canAccess(1L)).isTrue();

        setUser(43L);
        assertThat(validator.canAccess(1L)).isFalse();
        assertThatThrownBy(() -> validator.requireAccess(1L)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_PROJECT_FORBIDDEN");
    }

    @Test
    void reviewerMustBelongToProjectOrOwnIt() {
        ProjectConfigDO project = project(7L, List.of("42"));

        assertThatCode(() -> validator.requireAssignableReviewers(project, List.of(7L, 42L)))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.requireAssignableReviewers(project, List.of(43L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_REVIEWER_PROJECT_FORBIDDEN")
            .hasMessageContaining("43");
    }

    private ProjectConfigDO project(Long creator, List<String> members) {
        ProjectConfigDO project = new ProjectConfigDO();
        project.setId(1L);
        project.setCreateUser(creator);
        project.setMember(members);
        project.setDelFlag(StatusTypeEnum.NORMAL);
        return project;
    }

    private void setUser(Long userId) {
        UserContext context = new UserContext();
        context.setId(userId);
        context.setRoleCodes(Set.of());
        UserContextHolder.setContext(context, false);
    }
}
