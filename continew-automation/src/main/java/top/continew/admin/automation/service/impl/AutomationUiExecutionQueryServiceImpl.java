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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper;
import top.continew.admin.automation.model.query.AutomationUiExecutionAccessRow;
import top.continew.admin.automation.model.query.AutomationUiExecutionCaseHistoryQuery;
import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.req.AutomationUiPageReq;
import top.continew.admin.automation.model.resp.AutomationUiExecutionArtifactResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseHistoryResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionPageResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionSummaryResp;
import top.continew.admin.automation.service.AutomationUiExecutionQueryService;
import top.continew.admin.automation.support.AutomationUiExecutionScopeSupport;
import top.continew.admin.automation.support.AutomationUiExecutionCursorCodec;
import top.continew.admin.automation.support.AutomationUiExecutionCursorCodec.CursorClaim;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver.AccessScope;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

/** UI 自动化执行事实分层查询服务实现。 */
@Service
@RequiredArgsConstructor
public class AutomationUiExecutionQueryServiceImpl implements AutomationUiExecutionQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final long MAX_OFFSET = 10_000;

    private final AutomationUiExecutionQueryMapper queryMapper;
    private final AutomationUiSceneAccessScopeResolver accessScopeResolver;
    private final AutomationUiExecutionCursorCodec cursorCodec;

    @Override
    public AutomationUiExecutionPageResp page(AutomationUiExecutionQuery query, AutomationUiPageReq pageQuery) {
        if (query == null || query.getSceneDbId() == null || query.getSceneDbId() <= 0) {
            throw new BadRequestException("INVALID_SCENE_DB_ID：场景数据库 ID 必须为正整数");
        }
        AutomationUiExecutionScopeReq requestedScope = new AutomationUiExecutionScopeReq();
        requestedScope.setRecordSource(query.getRecordSource());
        requestedScope.setTestPlanId(query.getTestPlanId());
        requestedScope.setTestReportId(query.getTestReportId());
        requestedScope.setBuildNumber(query.getBuildNumber());
        AutomationUiExecutionScopeReq scope = AutomationUiExecutionScopeSupport.normalize(requestedScope);
        query.setRecordSource(scope.getRecordSource());
        query.setStatus(AutomationUiExecutionScopeSupport.validateStatus(query.getStatus()));
        query.setResult(AutomationUiExecutionScopeSupport.validateResult(query.getResult()));
        if (query.getCursor() != null && !query.getCursor().isBlank()) {
            return cursorPage(query, scope, pageQuery);
        }
        PageBounds bounds = pageBounds(pageQuery, "createTime");
        AccessScope accessScope = accessScopeResolver.currentScope();
        AutomationUiExecutionAccessRow access = queryMapper.selectExecutionPageCount(query, scope, accessScope
            .userId(), accessScope.admin());
        requireAccess(access);
        long total = total(access);
        List<AutomationUiExecutionSummaryResp> list = total == 0
            ? List.of()
            : queryMapper.selectExecutionPage(query, scope, accessScope.userId(), accessScope.admin(), bounds
                .offset(), bounds.size(), bounds.ascending());
        AutomationUiExecutionPageResp.Offset response = new AutomationUiExecutionPageResp.Offset();
        response.setList(list);
        response.setTotal(total);
        response.setPage(bounds.page());
        response.setSize(bounds.size());
        response.setGlobalExecutionRevision(access.getGlobalExecutionRevision());
        return response;
    }

    private AutomationUiExecutionPageResp cursorPage(AutomationUiExecutionQuery query,
                                                     AutomationUiExecutionScopeReq scope,
                                                     AutomationUiPageReq pageQuery) {
        AutomationUiPageReq safePage = pageQuery == null ? new AutomationUiPageReq() : pageQuery;
        int page = requirePositive(safePage.getPage(), "page");
        int size = requirePageSize(safePage.getSize());
        if (page != 1) {
            throw new BadRequestException("INVALID_CURSOR_PAGE：cursor 模式不接受 page 翻页");
        }
        RequestedSort requestedSort = requestedSort(safePage.getRequestedSort());
        if (requestedSort != null && !"createTime".equals(requestedSort.field())) {
            throw new BadRequestException("INVALID_SORT_FIELD：执行历史只允许按 createTime 排序");
        }
        String cursor = query.getCursor();
        CursorClaim claim = "start".equals(cursor) ? null : cursorCodec.decode(cursor);
        boolean ascending = claim == null ? requestedSort != null && requestedSort.ascending() : claim.ascending();
        if (claim != null && requestedSort != null && requestedSort.ascending() != claim.ascending()) {
            throw new BadRequestException("INVALID_CURSOR_SCOPE：游标与排序方向不一致");
        }
        AccessScope accessScope = accessScopeResolver.currentScope();
        AutomationUiExecutionAccessRow access = queryMapper.selectSceneAccess(query.getSceneDbId(), accessScope
            .userId(), accessScope.admin());
        requireAccess(access);
        String filterDigest = cursorFilterDigest(query, scope, ascending);
        String permissionScopeDigest = cursorPermissionScopeDigest(query.getSceneDbId(), accessScope);
        validateCursorClaim(claim, query.getSceneDbId(), filterDigest, permissionScopeDigest);
        List<AutomationUiExecutionSummaryResp> candidates = queryMapper.selectExecutionCursor(query, scope, accessScope
            .userId(), accessScope.admin(), claim == null ? null : claim.createTime(), claim == null
                ? null
                : claim.executionDbId(), size + 1, ascending);
        boolean hasMore = candidates.size() > size;
        List<AutomationUiExecutionSummaryResp> list = hasMore
            ? new ArrayList<>(candidates.subList(0, size))
            : new ArrayList<>(candidates);
        String nextCursor = null;
        if (hasMore && !list.isEmpty()) {
            AutomationUiExecutionSummaryResp anchor = list.get(list.size() - 1);
            nextCursor = cursorCodec.encode(query.getSceneDbId(), filterDigest, permissionScopeDigest, anchor
                .getCreateTime(), anchor.getExecutionDbId(), ascending);
        }
        AutomationUiExecutionPageResp.Cursor response = new AutomationUiExecutionPageResp.Cursor();
        response.setList(list);
        response.setNextCursor(nextCursor);
        response.setHasMore(hasMore);
        response.setGlobalExecutionRevision(access.getGlobalExecutionRevision());
        return response;
    }

    @Override
    public AutomationUiExecutionDetailResp detail(Long executionDbId) {
        requirePositive(executionDbId, "executionDbId");
        AccessScope scope = accessScopeResolver.currentScope();
        AutomationUiExecutionDetailResp detail = queryMapper.selectExecutionDetail(executionDbId, scope.userId(), scope
            .admin());
        if (detail == null) {
            throw hiddenResource();
        }
        return detail;
    }

    @Override
    public PageResp<AutomationUiExecutionCaseResp> cases(Long executionDbId, AutomationUiPageReq pageQuery) {
        requirePositive(executionDbId, "executionDbId");
        PageBounds bounds = pageBounds(pageQuery, null);
        AccessScope scope = accessScopeResolver.currentScope();
        AutomationUiExecutionAccessRow access = queryMapper.selectCasePageCount(executionDbId, scope.userId(), scope
            .admin());
        requireAccess(access);
        long total = total(access);
        List<AutomationUiExecutionCaseResp> list = total == 0
            ? List.of()
            : queryMapper.selectCasePage(executionDbId, scope.userId(), scope.admin(), bounds.offset(), bounds.size());
        return new PageResp<>(list, total);
    }

    @Override
    public PageResp<AutomationUiExecutionCaseHistoryResp> caseHistory(AutomationUiExecutionCaseHistoryQuery query,
                                                                      AutomationUiPageReq pageQuery) {
        if (query == null || query.getSceneDbId() == null || query.getSceneDbId() <= 0 || query
            .getCaseId() == null || query.getCaseId().isBlank()) {
            throw new BadRequestException("INVALID_CASE_HISTORY_SCOPE：场景数据库 ID 和用例 ID 不能为空");
        }
        AutomationUiExecutionScopeReq requestedScope = new AutomationUiExecutionScopeReq();
        requestedScope.setRecordSource(query.getRecordSource());
        requestedScope.setTestPlanId(query.getTestPlanId());
        requestedScope.setTestReportId(query.getTestReportId());
        requestedScope.setBuildNumber(query.getBuildNumber());
        AutomationUiExecutionScopeReq scope = AutomationUiExecutionScopeSupport.normalize(requestedScope);
        query.setRecordSource(scope.getRecordSource());
        PageBounds bounds = pageBounds(pageQuery, null);
        AccessScope accessScope = accessScopeResolver.currentScope();
        AutomationUiExecutionAccessRow access = queryMapper.selectCaseHistoryPageCount(query, scope, accessScope
            .userId(), accessScope.admin());
        requireAccess(access);
        long total = total(access);
        List<AutomationUiExecutionCaseHistoryResp> list = total == 0
            ? List.of()
            : queryMapper.selectCaseHistoryPage(query, scope, accessScope.userId(), accessScope.admin(), bounds
                .offset(), bounds.size());
        return new PageResp<>(list, total);
    }

    @Override
    public PageResp<AutomationUiExecutionStepResp> steps(Long caseExecutionDbId, AutomationUiPageReq pageQuery) {
        requirePositive(caseExecutionDbId, "caseExecutionDbId");
        PageBounds bounds = pageBounds(pageQuery, null);
        AccessScope scope = accessScopeResolver.currentScope();
        AutomationUiExecutionAccessRow access = queryMapper.selectStepPageCount(caseExecutionDbId, scope.userId(), scope
            .admin());
        requireAccess(access);
        long total = total(access);
        List<AutomationUiExecutionStepResp> list = total == 0
            ? List.of()
            : queryMapper.selectStepPage(caseExecutionDbId, scope.userId(), scope.admin(), bounds.offset(), bounds
                .size());
        return new PageResp<>(list, total);
    }

    @Override
    public AutomationUiExecutionStepDetailResp stepDetail(Long stepExecutionDbId) {
        requirePositive(stepExecutionDbId, "stepExecutionDbId");
        AccessScope scope = accessScopeResolver.currentScope();
        AutomationUiExecutionStepDetailResp detail = queryMapper.selectStepDetail(stepExecutionDbId, scope
            .userId(), scope.admin());
        if (detail == null) {
            throw hiddenResource();
        }
        return detail;
    }

    @Override
    public PageResp<AutomationUiExecutionArtifactResp> artifacts(Long executionDbId, AutomationUiPageReq pageQuery) {
        requirePositive(executionDbId, "executionDbId");
        PageBounds bounds = pageBounds(pageQuery, null);
        AccessScope scope = accessScopeResolver.currentScope();
        AutomationUiExecutionAccessRow access = queryMapper.selectArtifactPageCount(executionDbId, scope.userId(), scope
            .admin());
        requireAccess(access);
        long total = total(access);
        List<AutomationUiExecutionArtifactResp> list = total == 0
            ? List.of()
            : queryMapper.selectArtifactPage(executionDbId, scope.userId(), scope.admin(), bounds.offset(), bounds
                .size());
        return new PageResp<>(list, total);
    }

    private PageBounds pageBounds(AutomationUiPageReq pageQuery, String allowedSortField) {
        AutomationUiPageReq safePage = pageQuery == null ? new AutomationUiPageReq() : pageQuery;
        int page = requirePositive(safePage.getPage(), "page");
        int size = requirePageSize(safePage.getSize());
        long offset = Math.multiplyExact((long)page - 1, size);
        if (offset >= MAX_OFFSET) {
            throw new BadRequestException("OFFSET_LIMIT_EXCEEDED：offset 必须小于 " + MAX_OFFSET);
        }
        RequestedSort sort = requestedSort(safePage.getRequestedSort());
        if (allowedSortField == null) {
            if (sort != null) {
                throw new BadRequestException("INVALID_SORT_FIELD：该子资源使用固定稳定排序");
            }
            return new PageBounds(page, size, offset, true);
        }
        if (sort == null) {
            return new PageBounds(page, size, offset, false);
        }
        if (!allowedSortField.equals(sort.field())) {
            throw new BadRequestException("INVALID_SORT_FIELD：执行历史只允许按 createTime 排序");
        }
        return new PageBounds(page, size, offset, sort.ascending());
    }

    private RequestedSort requestedSort(String[] rawSort) {
        if (rawSort == null || rawSort.length == 0) {
            return null;
        }
        String[] parts = rawSort.length == 1 ? rawSort[0].split(",", -1) : rawSort;
        if (parts.length != 2 || parts[0] == null || parts[0].isBlank() || parts[1] == null) {
            throw new BadRequestException("INVALID_SORT_FIELD：排序参数必须为字段和方向");
        }
        String direction = parts[1].trim();
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new BadRequestException("INVALID_SORT_DIRECTION：排序方向只允许 asc 或 desc");
        }
        return new RequestedSort(parts[0].trim(), "asc".equalsIgnoreCase(direction));
    }

    private int requirePageSize(Integer value) {
        int size = requirePositive(value, "size");
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("INVALID_PAGE_SIZE：每页数量不能超过 " + MAX_PAGE_SIZE);
        }
        return size;
    }

    private String cursorFilterDigest(AutomationUiExecutionQuery query,
                                      AutomationUiExecutionScopeReq scope,
                                      boolean ascending) {
        return cursorCodec.digest(List.of(String.valueOf(query.getSceneDbId()), scope.getRecordSource(), String
            .valueOf(scope.getTestPlanId()), String.valueOf(scope.getTestReportId()), String.valueOf(scope
                .getBuildNumber()), String.valueOf(query.getStatus()), String.valueOf(query.getResult()), ascending
                    ? "asc"
                    : "desc"));
    }

    private String cursorPermissionScopeDigest(Long sceneDbId, AccessScope scope) {
        List<String> parts = new ArrayList<>();
        parts.add(String.valueOf(sceneDbId));
        parts.add(String.valueOf(scope.userId()));
        parts.add(String.valueOf(scope.admin()));
        scope.permissions().stream().sorted().forEach(permission -> parts.add("permission:" + permission));
        scope.roleCodes().stream().sorted().forEach(roleCode -> parts.add("role:" + roleCode));
        return cursorCodec.digest(parts);
    }

    private void validateCursorClaim(CursorClaim claim,
                                     Long sceneDbId,
                                     String filterDigest,
                                     String permissionScopeDigest) {
        if (claim == null) {
            return;
        }
        if (!Objects.equals(sceneDbId, claim.sceneDbId()) || !Objects.equals(filterDigest, claim
            .filterDigest()) || !Objects.equals(permissionScopeDigest, claim.permissionScopeDigest())) {
            throw new BadRequestException("INVALID_CURSOR_SCOPE：游标不属于当前查询或权限范围");
        }
    }

    private int requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BadRequestException("INVALID_PAGE_ARGUMENT：" + fieldName + " 必须为正整数");
        }
        return value;
    }

    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BadRequestException("INVALID_RESOURCE_ID：" + fieldName + " 必须为正整数");
        }
    }

    private void requireAccess(AutomationUiExecutionAccessRow access) {
        if (access == null) {
            throw hiddenResource();
        }
    }

    private long total(AutomationUiExecutionAccessRow access) {
        return access.getTotal() == null ? 0 : access.getTotal();
    }

    private BusinessException hiddenResource() {
        return new BusinessException("AUTOMATION_RESOURCE_NOT_FOUND_OR_ACCESS_DENIED：资源不存在或无访问权限");
    }

    private record PageBounds(int page, int size, long offset, boolean ascending) {
    }

    private record RequestedSort(String field, boolean ascending) {
    }
}
