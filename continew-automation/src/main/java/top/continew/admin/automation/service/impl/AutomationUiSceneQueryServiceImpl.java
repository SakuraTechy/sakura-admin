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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.mapper.AutomationUiSceneQueryMapper;
import top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper;
import top.continew.admin.automation.model.query.AutomationUiSceneDefinitionRow;
import top.continew.admin.automation.model.query.AutomationUiSceneInlineDefinitionRow;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.query.AutomationUiDefinitionProjectionStateRow;
import top.continew.admin.automation.model.query.AutomationUiDefinitionCaseReadRow;
import top.continew.admin.automation.model.query.AutomationUiDefinitionStepReadRow;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDefinitionResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionSummaryResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneGlobalRevisionResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneSummaryResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionCasePageResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionCaseResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionCaseNodeResp;
import top.continew.admin.automation.model.resp.AutomationUiDefinitionStepPageResp;
import top.continew.admin.automation.service.AutomationUiSceneQueryService;
import top.continew.admin.automation.service.AutomationUiDefinitionProjectionService;
import top.continew.admin.automation.support.AutomationUiDefinitionDisplayMasker;
import top.continew.admin.automation.support.AutomationUiExecutionScopeSupport;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver.AccessScope;
import top.continew.admin.automation.support.AutomationUiDefinitionProjectionUnavailableException;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

/** UI 自动化场景轻量查询服务实现。 */
@Service
@RequiredArgsConstructor
public class AutomationUiSceneQueryServiceImpl implements AutomationUiSceneQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final long MAX_OFFSET = 10_000;
    private static final int MAX_SUMMARY_IDS = 100;
    private static final Map<String, String> SUMMARY_SORT_FIELDS = Map
        .of("sceneDbId", "sceneDbId", "name", "name", "createTime", "createTime", "updateTime", "updateTime");

    private final AutomationUiSceneQueryMapper queryMapper;
    private final AutomationUiExecutionQueryMapper executionQueryMapper;
    private final AutomationUiSceneAccessScopeResolver accessScopeResolver;
    private final AutomationUiDefinitionDisplayMasker displayMasker;
    private ObjectMapper nodeObjectMapper = new ObjectMapper();

    @Autowired(required = false)
    private AutomationUiDefinitionProjectionService definitionProjectionService;

    @Autowired(required = false)
    void configureNodeObjectMapper(ObjectMapper objectMapper) {
        this.nodeObjectMapper = objectMapper;
    }

    @Value("${automation.ui-query.inline-max-bytes:1048576}")
    private long inlineMaxBytes;

    @Value("${automation.ui-query.inline-max-steps:1000}")
    private long inlineMaxSteps;

    @Value("${automation.ui-query.mask-policy-version:1}")
    private long maskPolicyVersion;

    @Override
    public PageResp<AutomationUiSceneSummaryResp> page(AutomationUiSceneQuery query, PageQuery pageQuery) {
        return pageInternal(query, pageQuery, null);
    }

    @Override
    public PageResp<AutomationUiSceneSummaryResp> page(AutomationUiSceneQuery query,
                                                       PageQuery pageQuery,
                                                       AutomationUiExecutionScopeReq executionScope) {
        if (executionScope == null) {
            return pageInternal(query, pageQuery, null);
        }
        return pageInternal(query, pageQuery, AutomationUiExecutionScopeSupport.normalize(executionScope));
    }

    private PageResp<AutomationUiSceneSummaryResp> pageInternal(AutomationUiSceneQuery query,
                                                                PageQuery pageQuery,
                                                                AutomationUiExecutionScopeReq executionScope) {
        AutomationUiSceneQuery safeQuery = query == null ? new AutomationUiSceneQuery() : query;
        PageQuery safePage = pageQuery == null ? new PageQuery() : pageQuery;
        if (executionScope == null) {
            requireBasicSummaryQuery(safeQuery);
        } else {
            normalizeScopedSummaryQuery(safeQuery);
        }
        int page = requirePositive(safePage.getPage(), "页码");
        int size = requirePositive(safePage.getSize(), "每页数量");
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("INVALID_PAGE_SIZE：每页数量不能超过 " + MAX_PAGE_SIZE);
        }
        long offset = Math.multiplyExact((long)page - 1, size);
        if (offset >= MAX_OFFSET) {
            throw new BadRequestException("OFFSET_LIMIT_EXCEEDED：offset 必须小于 " + MAX_OFFSET);
        }
        SummarySort sort = resolveSort(safePage.getSort());
        AccessScope scope = accessScopeResolver.currentScope();
        long total = executionScope == null
            ? queryMapper.countSummaries(safeQuery, scope.userId(), scope.admin())
            : queryMapper.countScopedSummaries(safeQuery, executionScope, scope.userId(), scope.admin());
        if (total == 0) {
            return new PageResp<>(List.of(), 0);
        }
        List<AutomationUiSceneSummaryResp> list = executionScope == null
            ? queryMapper.selectSummaryPage(safeQuery, scope.userId(), scope.admin(), offset, size, sort.field(), sort
                .ascending())
            : queryMapper.selectScopedSummaryPage(safeQuery, executionScope, scope.userId(), scope
                .admin(), offset, size, sort.field(), sort.ascending());
        if (executionScope != null && !list.isEmpty()) {
            attachScopedLatest(list, executionScope, scope);
        }
        return new PageResp<>(list, total);
    }

    @Override
    public List<AutomationUiSceneSummaryResp> summaries(Collection<Long> sceneDbIds) {
        LinkedHashSet<Long> normalizedIds = normalizeSceneDbIds(sceneDbIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        AccessScope scope = accessScopeResolver.currentScope();
        return queryMapper.selectSummaries(normalizedIds, scope.userId(), scope.admin());
    }

    @Override
    public List<AutomationUiSceneSummaryResp> summaries(Collection<Long> sceneDbIds,
                                                        AutomationUiExecutionScopeReq executionScope) {
        if (executionScope == null) {
            return summaries(sceneDbIds);
        }
        AutomationUiExecutionScopeReq normalizedScope = AutomationUiExecutionScopeSupport.normalize(executionScope);
        LinkedHashSet<Long> normalizedIds = normalizeSceneDbIds(sceneDbIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        AccessScope scope = accessScopeResolver.currentScope();
        List<AutomationUiSceneSummaryResp> summaries = queryMapper.selectSummaries(normalizedIds, scope.userId(), scope
            .admin());
        List<AutomationUiExecutionSummaryResp> latest = executionQueryMapper
            .selectScopedLatestBatch(normalizedIds, normalizedScope, scope.userId(), scope.admin());
        Map<Long, AutomationUiExecutionSummaryResp> latestByScene = new HashMap<>();
        latest.forEach(item -> latestByScene.put(item.getSceneDbId(), item));
        summaries.forEach(item -> item.setLatestExecution(latestByScene.get(item.getSceneDbId())));
        return summaries;
    }

    @Override
    public AutomationUiExecutionSummaryResp latestExecution(Long sceneDbId,
                                                            AutomationUiExecutionScopeReq executionScope) {
        if (sceneDbId == null || sceneDbId <= 0) {
            throw new BadRequestException("INVALID_SCENE_DB_ID：场景数据库 ID 必须为正整数");
        }
        AutomationUiExecutionScopeReq normalizedScope = AutomationUiExecutionScopeSupport.normalize(executionScope);
        AccessScope scope = accessScopeResolver.currentScope();
        if (executionQueryMapper.selectSceneAccess(sceneDbId, scope.userId(), scope.admin()) == null) {
            throw new BusinessException("AUTOMATION_SCENE_NOT_FOUND_OR_ACCESS_DENIED：场景不存在或无访问权限");
        }
        return executionQueryMapper.selectScopedLatest(sceneDbId, normalizedScope, scope.userId(), scope.admin());
    }

    @Override
    public List<AutomationUiSceneGlobalRevisionResp> revisions(Collection<Long> sceneDbIds) {
        LinkedHashSet<Long> normalizedIds = normalizeSceneDbIds(sceneDbIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        AccessScope scope = accessScopeResolver.currentScope();
        return queryMapper.selectRevisions(normalizedIds, scope.userId(), scope.admin());
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionView definition(Long sceneDbId) {
        if (sceneDbId == null || sceneDbId <= 0) {
            throw new BadRequestException("INVALID_SCENE_DB_ID：场景数据库 ID 必须为正整数");
        }
        AccessScope scope = accessScopeResolver.currentScope();
        // 首次查询只读取元数据和 JSON 度量；对象授权完成前不会读取 case_list 正文。
        AutomationUiSceneDefinitionRow row = queryMapper.selectDefinitionMetadata(sceneDbId, scope.userId(), scope
            .admin());
        if (row == null) {
            throw new BusinessException("AUTOMATION_SCENE_NOT_FOUND_OR_ACCESS_DENIED：场景不存在或无访问权限");
        }
        if (row.getDefinitionBytes() == null || row.getDefinitionStepCount() == null) {
            if (definitionProjectionService == null) {
                throw unavailable(true, sceneDbId, row.getDefinitionVersion(), "queued");
            }
            // 迁移期只对当前请求的单场景受控回填；周期对账仍禁止扫描全表 case_list。
            AutomationUiDefinitionProjectionService.DefinitionMetrics metrics = definitionProjectionService
                .ensureMetrics(sceneDbId, row.getDefinitionVersion() == null ? 0L : row.getDefinitionVersion());
            row.setDefinitionBytes(metrics.sizeBytes());
            row.setDefinitionStepCount((long)metrics.stepCount());
            if (metrics.projectionQueued()) {
                throw unavailable(true, sceneDbId, row.getDefinitionVersion(), "queued");
            }
        }
        long definitionBytes = row.getDefinitionBytes();
        long definitionStepCount = row.getDefinitionStepCount();
        if (definitionBytes >= inlineMaxBytes || definitionStepCount >= inlineMaxSteps) {
            AutomationUiDefinitionProjectionStateRow state = requireReadyProjection(sceneDbId, row, scope);
            String representationScopeDigest = scopeDigest(scope, row, maskPolicyVersion);
            AutomationUiSceneDefinitionResp.Projected response = new AutomationUiSceneDefinitionResp.Projected();
            copyMetadata(row, response);
            response.setDefinitionVersion(row.getDefinitionVersion() == null ? 0L : row.getDefinitionVersion());
            response.setMaskPolicyVersion(maskPolicyVersion);
            response.setRepresentationScopeDigest(representationScopeDigest);
            response.setProjectionId(state.getPublishedProjectionId());
            response.setCaseCount(state.getCaseCount());
            response.setStepCount(state.getStepCount());
            response.setProjectionStatus("ready");
            String etag = "W/\"scene-%d-definition-%d-mode-projected-projection-%d-mask-%d-scope-%s-source-%s\""
                .formatted(sceneDbId, response.getDefinitionVersion(), response
                    .getProjectionId(), maskPolicyVersion, representationScopeDigest, state.getSourceSha256());
            return new DefinitionView(response, etag);
        }
        Long definitionVersion = row.getDefinitionVersion() == null ? 0L : row.getDefinitionVersion();
        AutomationUiSceneInlineDefinitionRow inlineRow = queryMapper
            .selectInlineDefinition(sceneDbId, definitionVersion, scope.userId(), scope.admin());
        if (inlineRow == null) {
            throw new BusinessException("AUTOMATION_DEFINITION_CHANGED_RETRY：场景定义已变化，请重试");
        }
        String representationScopeDigest = scopeDigest(scope, row, maskPolicyVersion);
        AutomationUiSceneDefinitionResp.Inline response = new AutomationUiSceneDefinitionResp.Inline();
        copyMetadata(row, response);
        response.setDefinitionVersion(definitionVersion);
        response.setMaskPolicyVersion(maskPolicyVersion);
        response.setRepresentationScopeDigest(representationScopeDigest);
        response.setCaseList(displayMasker.mask(inlineRow.getCaseList()));
        String etag = "W/\"scene-%d-definition-%d-mode-inline-mask-%d-scope-%s\""
            .formatted(sceneDbId, definitionVersion, maskPolicyVersion, representationScopeDigest);
        return new DefinitionView(response, etag);
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionNodeView<AutomationUiDefinitionCasePageResp> definitionCases(Long sceneDbId,
                                                                                  int page,
                                                                                  int size,
                                                                                  String keyword) {
        PageBounds bounds = pageBounds(page, size, 50);
        String safeKeyword = normalizeCaseKeyword(keyword);
        DefinitionReadContext context = definitionReadContext(sceneDbId);
        long completeTotal = queryMapper.countProjectedCases(sceneDbId, context.projectionId(), context
            .definitionVersion(), null, context.scope().userId(), context.scope().admin());
        if (completeTotal != context.state().getCaseCount()) {
            throw unavailable(false);
        }
        long total = safeKeyword == null
            ? completeTotal
            : queryMapper.countProjectedCases(sceneDbId, context.projectionId(), context
                .definitionVersion(), safeKeyword, context.scope().userId(), context.scope().admin());
        List<AutomationUiDefinitionCaseReadRow> rows = queryMapper.selectProjectedCases(sceneDbId, context
            .projectionId(), context.definitionVersion(), safeKeyword, context.scope().userId(), context.scope()
                .admin(), bounds.offset(), bounds.size());
        AutomationUiDefinitionCasePageResp body = new AutomationUiDefinitionCasePageResp();
        body.setSceneDbId(sceneDbId);
        body.setDefinitionVersion(context.definitionVersion());
        body.setProjectionId(context.projectionId());
        body.setPage(bounds.page());
        body.setSize(bounds.size());
        body.setTotal(total);
        body.setItems(rows.stream().map(this::caseNode).toList());
        String keywordScope = safeKeyword == null ? "all" : sha256(safeKeyword);
        return nodeView(context, "cases:" + bounds.page() + ":" + bounds.size() + ":" + keywordScope, body);
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionNodeView<AutomationUiDefinitionCaseResp> definitionCase(Long sceneDbId, String caseId) {
        String safeCaseId = requireNodeId(caseId, "caseId");
        DefinitionReadContext context = definitionReadContext(sceneDbId);
        AutomationUiDefinitionCaseReadRow row = queryMapper.selectProjectedCase(sceneDbId, context
            .projectionId(), context.definitionVersion(), safeCaseId, context.scope().userId(), context.scope()
                .admin());
        if (row == null) {
            throw new BusinessException("AUTOMATION_DEFINITION_NODE_NOT_FOUND_OR_ACCESS_DENIED：定义节点不存在或无访问权限");
        }
        AutomationUiDefinitionCaseResp body = new AutomationUiDefinitionCaseResp();
        body.setSceneDbId(sceneDbId);
        body.setDefinitionVersion(context.definitionVersion());
        body.setProjectionId(context.projectionId());
        body.setCaseId(row.getCaseId());
        body.setStepCount(row.getStepCount());
        body.setCaseBody(verifiedJson(row.getCaseJson(), row.getNodeSha256()));
        return nodeView(context, "case:" + sha256(safeCaseId), body);
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionNodeView<AutomationUiDefinitionStepPageResp> definitionSteps(Long sceneDbId,
                                                                                  String caseId,
                                                                                  int page,
                                                                                  int size) {
        String safeCaseId = requireNodeId(caseId, "caseId");
        PageBounds bounds = pageBounds(page, size, 100);
        DefinitionReadContext context = definitionReadContext(sceneDbId);
        AutomationUiDefinitionCaseReadRow caseRow = queryMapper.selectProjectedCase(sceneDbId, context
            .projectionId(), context.definitionVersion(), safeCaseId, context.scope().userId(), context.scope()
                .admin());
        if (caseRow == null) {
            throw new BusinessException("AUTOMATION_DEFINITION_NODE_NOT_FOUND_OR_ACCESS_DENIED：定义节点不存在或无访问权限");
        }
        long total = queryMapper.countProjectedSteps(sceneDbId, context.projectionId(), context
            .definitionVersion(), safeCaseId, context.scope().userId(), context.scope().admin());
        if (total != caseRow.getStepCount()) {
            throw unavailable(false);
        }
        List<AutomationUiDefinitionStepReadRow> rows = queryMapper.selectProjectedSteps(sceneDbId, context
            .projectionId(), context.definitionVersion(), safeCaseId, context.scope().userId(), context.scope()
                .admin(), bounds.offset(), bounds.size());
        AutomationUiDefinitionStepPageResp body = new AutomationUiDefinitionStepPageResp();
        body.setSceneDbId(sceneDbId);
        body.setDefinitionVersion(context.definitionVersion());
        body.setProjectionId(context.projectionId());
        body.setCaseId(safeCaseId);
        body.setPage(bounds.page());
        body.setSize(bounds.size());
        body.setTotal(total);
        body.setItems(rows.stream().map(row -> verifiedStepJson(row.getStepJson(), row.getNodeSha256())).toList());
        return nodeView(context, "steps:" + sha256(safeCaseId) + ":" + bounds.page() + ":" + bounds.size(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionNodeView<JsonNode> definitionStep(Long sceneDbId, String caseId, String stepId) {
        String safeCaseId = requireNodeId(caseId, "caseId");
        String safeStepId = requireNodeId(stepId, "stepId");
        DefinitionReadContext context = definitionReadContext(sceneDbId);
        AutomationUiDefinitionStepReadRow row = queryMapper.selectProjectedStep(sceneDbId, context
            .projectionId(), context.definitionVersion(), safeCaseId, safeStepId, context.scope().userId(), context
                .scope()
                .admin());
        if (row == null) {
            throw new BusinessException("AUTOMATION_DEFINITION_NODE_NOT_FOUND_OR_ACCESS_DENIED：定义节点不存在或无访问权限");
        }
        return nodeView(context, "step:" + sha256(safeCaseId + "\u0000" + safeStepId), verifiedStepJson(row
            .getStepJson(), row.getNodeSha256()));
    }

    private DefinitionReadContext definitionReadContext(Long sceneDbId) {
        if (sceneDbId == null || sceneDbId <= 0) {
            throw new BadRequestException("INVALID_SCENE_DB_ID：场景数据库 ID 必须为正整数");
        }
        AccessScope scope = accessScopeResolver.currentScope();
        AutomationUiSceneDefinitionRow row = queryMapper.selectDefinitionMetadata(sceneDbId, scope.userId(), scope
            .admin());
        if (row == null) {
            throw new BusinessException("AUTOMATION_SCENE_NOT_FOUND_OR_ACCESS_DENIED：场景不存在或无访问权限");
        }
        if (row.getDefinitionBytes() == null || row.getDefinitionStepCount() == null) {
            if (definitionProjectionService == null) {
                throw unavailable(true, sceneDbId, row.getDefinitionVersion(), "queued");
            }
            AutomationUiDefinitionProjectionService.DefinitionMetrics metrics = definitionProjectionService
                .ensureMetrics(sceneDbId, row.getDefinitionVersion() == null ? 0L : row.getDefinitionVersion());
            row.setDefinitionBytes(metrics.sizeBytes());
            row.setDefinitionStepCount((long)metrics.stepCount());
            if (metrics.projectionQueued()) {
                throw unavailable(true, sceneDbId, row.getDefinitionVersion(), "queued");
            }
        }
        if (row.getDefinitionBytes() < inlineMaxBytes && row.getDefinitionStepCount() < inlineMaxSteps) {
            throw new BadRequestException("DEFINITION_NOT_PROJECTED：当前场景定义使用 inline 模式");
        }
        AutomationUiDefinitionProjectionStateRow state = requireReadyProjection(sceneDbId, row, scope);
        return new DefinitionReadContext(row, state, scope, row.getDefinitionVersion() == null
            ? 0L
            : row.getDefinitionVersion(), state.getPublishedProjectionId(), scopeDigest(scope, row, maskPolicyVersion));
    }

    private AutomationUiDefinitionProjectionStateRow requireReadyProjection(Long sceneDbId,
                                                                            AutomationUiSceneDefinitionRow row,
                                                                            AccessScope scope) {
        AutomationUiDefinitionProjectionStateRow state = queryMapper.selectProjectionState(sceneDbId, scope
            .userId(), scope.admin());
        if (state == null) {
            if (definitionProjectionService != null) {
                // 丢失写事件时同步确认当前版本已进入持久化队列，再向客户端返回 202。
                definitionProjectionService.ensureMetrics(sceneDbId, row.getDefinitionVersion() == null
                    ? 0L
                    : row.getDefinitionVersion());
            }
            throw unavailable(true, sceneDbId, row.getDefinitionVersion(), "queued");
        }
        if ("ready".equals(state.getStatus()) && state.getPublishedProjectionId() != null && Objects.equals(state
            .getDefinitionVersion(), row.getDefinitionVersion()) && state.getStepCount() != null && state.getStepCount()
                .longValue() == (row.getDefinitionStepCount() == null ? 0 : row.getDefinitionStepCount())) {
            return state;
        }
        if ("failed".equals(state.getStatus()) && !Boolean.TRUE.equals(state.getRetryable())) {
            throw unavailable(false, state.getErrorId(), sceneDbId, row.getDefinitionVersion(), state.getStatus());
        }
        throw unavailable(true, sceneDbId, row.getDefinitionVersion(), state.getStatus());
    }

    private <T> DefinitionNodeView<T> nodeView(DefinitionReadContext context, String identity, T body) {
        String representation = serializeJson(body);
        String etag = "W/\"scene-%d-definition-%d-projection-%d-node-%s-representation-%s-mask-%d-scope-%s\""
            .formatted(context.row().getSceneDbId(), context.definitionVersion(), context
                .projectionId(), sha256(identity), sha256(representation), maskPolicyVersion, context.scopeDigest());
        return new DefinitionNodeView<>(body, etag);
    }

    private JsonNode maskStepJson(String json) {
        try {
            StepDO step = nodeObjectMapper.readValue(json, StepDO.class);
            CaseDO wrapper = new CaseDO();
            wrapper.setStepList(List.of(step));
            StepDO masked = displayMasker.mask(List.of(wrapper)).get(0).getStepList().get(0);
            return nodeObjectMapper.valueToTree(masked);
        } catch (JsonProcessingException e) {
            throw unavailable(false);
        }
    }

    private JsonNode verifiedStepJson(String json, String expectedHash) {
        verifyNodeHash(json, expectedHash);
        return maskStepJson(json);
    }

    private JsonNode verifiedJson(String json, String expectedHash) {
        verifyNodeHash(json, expectedHash);
        return parseJson(json);
    }

    private void verifyNodeHash(String json, String expectedHash) {
        if (json == null || expectedHash == null || !MessageDigest.isEqual(sha256(json)
            .getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw unavailable(false);
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return nodeObjectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw unavailable(false);
        }
    }

    private String serializeJson(Object body) {
        try {
            return nodeObjectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("投影响应无法序列化", e);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private String requireNodeId(String id, String fieldName) {
        if (id == null || id.isBlank() || id.length() > 128 || id.codePoints().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("INVALID_DEFINITION_NODE_ID：" + fieldName + " 非法");
        }
        // 节点 ID 区分大小写且不得 trim；原值直接参与精确查询。
        return id;
    }

    private PageBounds pageBounds(int page, int size, int maxSize) {
        if (page <= 0 || size <= 0 || size > maxSize) {
            throw new BadRequestException("INVALID_PAGE_ARGUMENT：页码和每页数量非法");
        }
        long offset = Math.multiplyExact((long)page - 1, size);
        if (offset >= MAX_OFFSET) {
            throw new BadRequestException("OFFSET_LIMIT_EXCEEDED：offset 必须小于 " + MAX_OFFSET);
        }
        return new PageBounds(page, size, offset);
    }

    private AutomationUiDefinitionProjectionUnavailableException unavailable(boolean retryable) {
        return new AutomationUiDefinitionProjectionUnavailableException(retryable, "definition-projection-" + UUID
            .randomUUID());
    }

    private AutomationUiDefinitionProjectionUnavailableException unavailable(boolean retryable, String errorId) {
        String safeErrorId = errorId != null && errorId.matches("[a-zA-Z0-9-]{1,128}")
            ? errorId
            : "definition-projection-" + UUID.randomUUID();
        return new AutomationUiDefinitionProjectionUnavailableException(retryable, safeErrorId);
    }

    private AutomationUiDefinitionCaseNodeResp caseNode(AutomationUiDefinitionCaseReadRow row) {
        AutomationUiDefinitionCaseNodeResp item = new AutomationUiDefinitionCaseNodeResp();
        item.setCaseId(row.getCaseId());
        item.setCaseIndex(row.getCaseIndex());
        item.setCaseName(row.getCaseName());
        item.setStepCount(row.getStepCount());
        item.setCaseBody(verifiedJson(row.getCaseJson(), row.getNodeSha256()));
        return item;
    }

    private AutomationUiDefinitionProjectionUnavailableException unavailable(boolean retryable,
                                                                             Long sceneDbId,
                                                                             Long definitionVersion,
                                                                             String projectionStatus) {
        return new AutomationUiDefinitionProjectionUnavailableException(retryable, "definition-projection-" + UUID
            .randomUUID(), sceneDbId, definitionVersion, projectionStatus);
    }

    private AutomationUiDefinitionProjectionUnavailableException unavailable(boolean retryable,
                                                                             String errorId,
                                                                             Long sceneDbId,
                                                                             Long definitionVersion,
                                                                             String projectionStatus) {
        String safeErrorId = errorId != null && errorId.matches("[a-zA-Z0-9-]{1,128}")
            ? errorId
            : "definition-projection-" + UUID.randomUUID();
        return new AutomationUiDefinitionProjectionUnavailableException(retryable, safeErrorId, sceneDbId, definitionVersion, projectionStatus);
    }

    private LinkedHashSet<Long> normalizeSceneDbIds(Collection<Long> sceneDbIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (sceneDbIds == null) {
            return result;
        }
        for (Long sceneDbId : sceneDbIds) {
            if (sceneDbId == null || sceneDbId <= 0) {
                throw new BadRequestException("INVALID_SCENE_DB_ID：场景数据库 ID 必须为正整数");
            }
            result.add(sceneDbId);
            if (result.size() > MAX_SUMMARY_IDS) {
                throw new BadRequestException("SCENE_ID_LIMIT_EXCEEDED：场景数据库 ID 单次最多 " + MAX_SUMMARY_IDS + " 个");
            }
        }
        return result;
    }

    private int requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BadRequestException("INVALID_PAGE_ARGUMENT：" + fieldName + "必须为正整数");
        }
        return value;
    }

    private String normalizeCaseKeyword(String keyword) {
        String normalized = StrUtil.trim(keyword);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > 128 || normalized.codePoints()
            .anyMatch(Character::isISOControl)) {
            throw new BadRequestException("INVALID_CASE_KEYWORD：用例关键字长度必须在 1 到 128 个字符之间");
        }
        // LIKE 通配符只作为普通字符搜索，避免输入扩大扫描范围或改变结果语义。
        return normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void requireBasicSummaryQuery(AutomationUiSceneQuery query) {
        if (StrUtil.isNotBlank(query.getExecuteStatus()) || StrUtil.isNotBlank(query.getExecuteResult()) || StrUtil
            .isNotBlank(query.getExecuteResultType()) || StrUtil.isNotBlank(query.getTestPlanId()) || StrUtil
                .isNotBlank(query.getTestReportId()) || query.getBuildNumber() != null || Boolean.TRUE.equals(query
                    .getExecutionMatchedOnly())) {
            throw new BadRequestException("EXECUTION_SCOPE_REQUIRED：执行筛选必须使用显式 executionScope");
        }
    }

    private void normalizeScopedSummaryQuery(AutomationUiSceneQuery query) {
        if (StrUtil.isNotBlank(query.getExecuteResultType()) || StrUtil.isNotBlank(query.getTestPlanId()) || StrUtil
            .isNotBlank(query.getTestReportId()) || query.getBuildNumber() != null) {
            throw new BadRequestException("INVALID_EXECUTION_SCOPE：作用域字段必须通过 executionScope 提交");
        }
        if (StrUtil.isNotBlank(query.getExecuteStatus())) {
            query.setExecuteStatus(AutomationUiSceneStatusCodes.normalizeStatus(query.getExecuteStatus()));
        }
        if (StrUtil.isNotBlank(query.getExecuteResult())) {
            query.setExecuteResult(AutomationUiSceneStatusCodes.normalizeResult(query
                .getExecuteResult(), null, null, null, null));
        }
    }

    private void attachScopedLatest(List<AutomationUiSceneSummaryResp> summaries,
                                    AutomationUiExecutionScopeReq executionScope,
                                    AccessScope accessScope) {
        LinkedHashSet<Long> sceneDbIds = summaries.stream()
            .map(AutomationUiSceneSummaryResp::getSceneDbId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (sceneDbIds.isEmpty()) {
            return;
        }
        Map<Long, AutomationUiExecutionSummaryResp> latestByScene = new HashMap<>();
        executionQueryMapper.selectScopedLatestBatch(sceneDbIds, executionScope, accessScope.userId(), accessScope
            .admin()).forEach(item -> latestByScene.put(item.getSceneDbId(), item));
        summaries.forEach(item -> item.setLatestExecution(latestByScene.get(item.getSceneDbId())));
    }

    private SummarySort resolveSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return new SummarySort("createTime", false);
        }
        List<Sort.Order> orders = sort.stream().toList();
        if (orders.size() != 1) {
            throw new BadRequestException("INVALID_SORT_FIELD：场景摘要只允许一个排序字段");
        }
        Sort.Order order = orders.get(0);
        String field = SUMMARY_SORT_FIELDS.get(order.getProperty());
        if (field == null) {
            throw new BadRequestException("INVALID_SORT_FIELD：不支持的场景摘要排序字段");
        }
        return new SummarySort(field, order.isAscending());
    }

    private void copyMetadata(AutomationUiSceneDefinitionRow source, AutomationUiSceneDefinitionResp target) {
        target.setSceneDbId(source.getSceneDbId());
        target.setSceneKey(source.getSceneKey());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setProjectDbId(source.getProjectDbId());
        target.setProjectName(source.getProjectName());
        target.setVersionDbId(source.getVersionDbId());
        target.setVersionName(source.getVersionName());
        target.setModuleDbId(source.getModuleDbId());
        target.setModulePath(source.getModulePath());
        target.setLevel(source.getLevel());
        target.setStatus(source.getStatus());
        target.setTags(source.getTags());
    }

    private String scopeDigest(AccessScope scope, AutomationUiSceneDefinitionRow row, long policyVersion) {
        String permissions = scope.permissions().stream().sorted().reduce("", (left, right) -> left + "\n" + right);
        String roles = scope.roleCodes().stream().sorted().reduce("", (left, right) -> left + "\n" + right);
        String source = String.join("|", String.valueOf(scope.userId()), String.valueOf(scope.admin()), String
            .valueOf(row.getProjectDbId()), String.valueOf(row.getVersionDbId()), String
                .valueOf(policyVersion), permissions, roles);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private record SummarySort(String field, boolean ascending) {
    }

    private record PageBounds(int page, int size, long offset) {
    }

    private record DefinitionReadContext(AutomationUiSceneDefinitionRow row,
                                         AutomationUiDefinitionProjectionStateRow state, AccessScope scope,
                                         long definitionVersion, long projectionId, String scopeDigest) {
    }
}
