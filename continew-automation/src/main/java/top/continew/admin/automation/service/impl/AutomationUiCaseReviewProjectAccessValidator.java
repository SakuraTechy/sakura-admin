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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.starter.core.exception.BusinessException;

/** Applies the same project membership boundary used by project-level quality metrics. */
@Component
@RequiredArgsConstructor
class AutomationUiCaseReviewProjectAccessValidator {

    private final ProjectConfigMapper projectConfigMapper;

    ProjectConfigDO requireAccess(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("REVIEW_PROJECT_REQUIRED：项目不能为空");
        }
        ProjectConfigDO project = projectConfigMapper.selectById(projectId);
        if (project == null || !StatusTypeEnum.NORMAL.equals(project.getDelFlag())) {
            throw new BusinessException("REVIEW_PROJECT_NOT_FOUND：项目不存在或已删除");
        }
        if (UserContextHolder.getUserId() == null) {
            throw new BusinessException("REVIEW_USER_REQUIRED：无法识别当前用户");
        }
        if (canAccess(project)) {
            return project;
        }
        throw new BusinessException("REVIEW_PROJECT_FORBIDDEN：无权访问当前项目的用例评审");
    }

    boolean canAccess(Long projectId) {
        if (projectId == null) {
            return false;
        }
        ProjectConfigDO project = projectConfigMapper.selectById(projectId);
        return project != null && StatusTypeEnum.NORMAL.equals(project.getDelFlag()) && canAccess(project);
    }

    private boolean canAccess(ProjectConfigDO project) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return false;
        }
        UserContext context = UserContextHolder.getContext();
        return (context != null && context.isAdmin()) || Objects.equals(userId, project
            .getCreateUser()) || memberIds(project).contains(userId);
    }

    void requireAssignableReviewers(ProjectConfigDO project, Collection<Long> reviewerIds) {
        Set<Long> requested = reviewerIds == null
            ? Set.of()
            : reviewerIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> eligible = eligibleReviewerIds(project);
        LinkedHashSet<Long> invalid = new LinkedHashSet<>(requested);
        invalid.removeAll(eligible);
        if (!invalid.isEmpty()) {
            throw new BusinessException("REVIEW_REVIEWER_PROJECT_FORBIDDEN：评审人不是当前项目成员：" + invalid);
        }
    }

    Set<Long> eligibleReviewerIds(ProjectConfigDO project) {
        LinkedHashSet<Long> eligible = new LinkedHashSet<>(memberIds(project));
        if (project != null && project.getCreateUser() != null) {
            eligible.add(project.getCreateUser());
        }
        return eligible;
    }

    private Set<Long> memberIds(ProjectConfigDO project) {
        List<String> members = project.getMember();
        if (members == null) {
            return Set.of();
        }
        return members.stream().map(this::parseId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Long parseId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
