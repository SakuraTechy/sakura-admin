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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationUiDefinitionProjectionService;
import top.continew.starter.core.exception.BusinessException;

/** 场景定义只读投影实现。case_list 始终是唯一主数据，节点表只允许由本服务发布。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationUiDefinitionProjectionServiceImpl implements AutomationUiDefinitionProjectionService {

    private static final int MAX_NODE_ID_LENGTH = 128;
    private static final int MAX_BATCH_LIMIT = 500;
    private static final int MAX_RETRY_COUNT = 5;
    private static final TypeReference<List<CaseDO>> CASE_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${automation.ui-query.inline-max-bytes:1048576}")
    private long inlineMaxBytes;

    @Value("${automation.ui-query.inline-max-steps:1000}")
    private long inlineMaxSteps;

    @Value("${automation.ui-query.projection-lease-seconds:120}")
    private long leaseSeconds;

    @Value("${automation.ui-query.projection-state-retention-days:7}")
    private int stateRetentionDays;

    @Override
    public void recordDefinitionWrite(Long sceneId, Long definitionVersion, List<CaseDO> caseList) {
        if (sceneId == null || definitionVersion == null) {
            throw new IllegalArgumentException("场景 ID 和定义版本不能为空");
        }
        byte[] compactDefinition = serialize(caseList == null ? List.of() : caseList);
        int stepCount = countSteps(caseList);
        int updated = jdbcTemplate
            .update("UPDATE automation_ui_scene" + " SET definition_size_bytes = ?, definition_step_count = ?" + " WHERE id = ? AND definition_version = ?", compactDefinition.length, stepCount, sceneId, definitionVersion);
        if (updated != 1) {
            throw new BusinessException("DEFINITION_METRICS_VERSION_MISMATCH：场景定义版本已变化");
        }
        if (compactDefinition.length < inlineMaxBytes && stepCount < inlineMaxSteps) {
            return;
        }
        long stateId = nextId(this);
        // source_sha256 从已入库 JSON 计算，避免 MySQL JSON 规范化与 Java 序列化顺序差异导致误判。
        int queued = jdbcTemplate
            .update("INSERT INTO automation_ui_scene_definition_read_state" + " (id, scene_id, definition_version, source_sha256, status, case_count, step_count," + " retry_count, create_time, update_time)" + " SELECT ?, s.id, s.definition_version, SHA2(CAST(s.case_list AS CHAR), 256), 'queued', ?, ?," + " 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)" + " FROM automation_ui_scene s WHERE s.id = ? AND s.definition_version = ?" + " ON DUPLICATE KEY UPDATE" + " status = IF(source_sha256 = VALUES(source_sha256), status, 'failed')," + " last_error = IF(source_sha256 = VALUES(source_sha256), last_error, 'definition-version-content-conflict')," + " source_sha256 = IF(source_sha256 = VALUES(source_sha256), source_sha256, VALUES(source_sha256))," + " update_time = CURRENT_TIMESTAMP(3)", stateId, caseList == null
                ? 0
                : caseList.size(), stepCount, sceneId, definitionVersion);
        if (queued < 0 || queued > 2) {
            throw new BusinessException("DEFINITION_PROJECTION_QUEUE_FAILED：定义投影入队失败");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DefinitionMetrics ensureMetrics(Long sceneId, Long definitionVersion) {
        List<String> definitions = jdbcTemplate
            .query("SELECT CAST(case_list AS CHAR) FROM automation_ui_scene" + " WHERE id=? AND definition_version=? AND del_flag=3 FOR UPDATE", (rs,
                                                                                                                                                  rowNum) -> rs
                                                                                                                                                      .getString(1), sceneId, definitionVersion);
        if (definitions.size() != 1) {
            throw new BusinessException("AUTOMATION_DEFINITION_CHANGED_RETRY：场景定义已变化，请重试");
        }
        List<CaseDO> cases = parse(definitions.get(0));
        byte[] compact = serialize(cases);
        int steps = countSteps(cases);
        recordDefinitionWrite(sceneId, definitionVersion, cases);
        return new DefinitionMetrics(compact.length, cases
            .size(), steps, compact.length >= inlineMaxBytes || steps >= inlineMaxSteps);
    }

    @Override
    public boolean buildNext(String leaseOwner) {
        String owner = requireLeaseOwner(leaseOwner);
        BuildClaim claim = claim(owner);
        if (claim == null) {
            return false;
        }
        try {
            SourceDefinition source = loadSource(claim);
            ProjectionNodes nodes = buildNodes(claim, source.caseList());
            persistAndPublish(claim, source, nodes);
        } catch (InvalidDefinitionException e) {
            markTerminalFailure(claim, e.errorId());
            log.warn("场景定义投影校验失败：sceneId={}, version={}, projectionId={}, errorId={}", claim.sceneId(), claim
                .definitionVersion(), claim.projectionId(), e.errorId());
        } catch (RuntimeException e) {
            markRecoverableFailure(claim);
            log.error("场景定义投影构建异常：sceneId={}, version={}, projectionId={}", claim.sceneId(), claim
                .definitionVersion(), claim.projectionId(), e);
        }
        return true;
    }

    @Override
    public int reconcile(int limit) {
        int safeLimit = requireLimit(limit);
        List<MetricRow> rows = jdbcTemplate
            .query("SELECT s.id, s.definition_version, s.definition_step_count FROM automation_ui_scene s" + " LEFT JOIN automation_ui_scene_definition_read_state rs" + " ON rs.scene_id = s.id AND rs.definition_version = s.definition_version" + " AND rs.status IN ('queued','building','ready')" + " WHERE s.del_flag = 3" + " AND s.definition_size_bytes IS NOT NULL AND s.definition_step_count IS NOT NULL" + " AND (s.definition_size_bytes >= ? OR s.definition_step_count >= ?)" + " AND rs.id IS NULL ORDER BY s.id LIMIT ?", (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             rowNum) -> new MetricRow(rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 .getLong(1), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     .getLong(2), rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         .getInt(3)), inlineMaxBytes, inlineMaxSteps, safeLimit);
        int queued = 0;
        for (MetricRow row : rows) {
            long stateId = nextId(row);
            int inserted = jdbcTemplate
                .update("INSERT IGNORE INTO automation_ui_scene_definition_read_state" + " (id, scene_id, definition_version, source_sha256, status, case_count, step_count," + " retry_count, create_time, update_time)" + " SELECT ?, id, definition_version, SHA2(CAST(case_list AS CHAR), 256), 'queued'," + " JSON_LENGTH(COALESCE(case_list, JSON_ARRAY())), ?, 0," + " CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3) FROM automation_ui_scene" + " WHERE id = ? AND definition_version = ?", stateId, row
                    .stepCount(), row.sceneId(), row.definitionVersion());
            if (inserted == 0) {
                // 当前版本 stale 可重新排队；若同一版本正文哈希已变化，则说明版本不变量被破坏并终止失败。
                inserted = jdbcTemplate
                    .update("UPDATE automation_ui_scene_definition_read_state rs" + " JOIN automation_ui_scene s ON s.id=rs.scene_id AND s.definition_version=rs.definition_version" + " SET rs.status=IF(rs.source_sha256=SHA2(CAST(s.case_list AS CHAR), 256),'queued','failed')," + " rs.next_retry_at=NULL, rs.last_error=IF(rs.source_sha256=SHA2(CAST(s.case_list AS CHAR), 256)," + " NULL,'definition-version-content-conflict'), rs.update_time=CURRENT_TIMESTAMP(3)" + " WHERE rs.scene_id=? AND rs.definition_version=? AND rs.status='stale'", row
                        .sceneId(), row.definitionVersion());
            }
            queued += inserted;
        }
        return queued;
    }

    @Override
    public Long backfillMetricsBatch(Long afterSceneId, int limit) {
        long cursor = afterSceneId == null ? 0 : afterSceneId;
        int safeLimit = requireLimit(limit);
        List<BackfillRow> rows = jdbcTemplate
            .query("SELECT id, definition_version, CAST(case_list AS CHAR)" + " FROM automation_ui_scene WHERE id > ?" + " AND (definition_size_bytes IS NULL OR definition_step_count IS NULL)" + " ORDER BY id LIMIT ?", (rs,
                                                                                                                                                                                                                            rowNum) -> new BackfillRow(rs
                                                                                                                                                                                                                                .getLong(1), rs
                                                                                                                                                                                                                                    .getLong(2), rs
                                                                                                                                                                                                                                        .getString(3)), cursor, safeLimit);
        for (BackfillRow row : rows) {
            List<CaseDO> cases = parse(row.caseJson());
            recordDefinitionWrite(row.sceneId(), row.definitionVersion(), cases);
        }
        return rows.isEmpty() ? null : rows.get(rows.size() - 1).sceneId();
    }

    @Override
    public int cleanupOrphanedProjections(int limit) {
        int safeLimit = requireLimit(limit);
        int retired = retireOldStates(safeLimit);
        List<Long> projectionIds = jdbcTemplate
            .queryForList("SELECT orphan.projection_id FROM (" + " SELECT projection_id, MAX(create_time) create_time FROM automation_ui_scene_definition_case_read" + " GROUP BY projection_id UNION ALL SELECT projection_id, MAX(create_time)" + " FROM automation_ui_scene_definition_step_read GROUP BY projection_id) orphan" + " LEFT JOIN automation_ui_scene_definition_read_state rs" + " ON rs.published_projection_id = orphan.projection_id OR rs.building_projection_id = orphan.projection_id" + " WHERE rs.id IS NULL GROUP BY orphan.projection_id" + " HAVING MAX(orphan.create_time) < CURRENT_TIMESTAMP(3) - INTERVAL 24 HOUR" + " ORDER BY MAX(orphan.create_time) LIMIT ?", Long.class, safeLimit);
        for (Long projectionId : projectionIds) {
            // 固定 step -> case 顺序；只删除未被 state 引用且超过宽限期的投影。
            jdbcTemplate
                .update("DELETE step FROM automation_ui_scene_definition_step_read step" + " LEFT JOIN automation_ui_scene_definition_read_state rs" + " ON rs.published_projection_id = step.projection_id OR rs.building_projection_id = step.projection_id" + " WHERE step.projection_id = ? AND rs.id IS NULL", projectionId);
            jdbcTemplate
                .update("DELETE node FROM automation_ui_scene_definition_case_read node" + " LEFT JOIN automation_ui_scene_definition_read_state rs" + " ON rs.published_projection_id = node.projection_id OR rs.building_projection_id = node.projection_id" + " WHERE node.projection_id = ? AND rs.id IS NULL", projectionId);
        }
        return retired + projectionIds.size();
    }

    private int retireOldStates(int limit) {
        int retentionDays = Math.max(1, stateRetentionDays);
        List<Long> stateIds = jdbcTemplate
            .queryForList("SELECT rs.id FROM automation_ui_scene_definition_read_state rs" + " LEFT JOIN automation_ui_scene s ON s.id=rs.scene_id AND s.del_flag=3" + " WHERE (s.id IS NULL OR rs.definition_version <> COALESCE(s.definition_version, 0))" + " AND rs.status <> 'building' AND (rs.lease_until IS NULL OR rs.lease_until < CURRENT_TIMESTAMP(3))" + " AND rs.update_time < TIMESTAMPADD(DAY, ?, CURRENT_TIMESTAMP(3))" + " ORDER BY rs.id LIMIT ?", Long.class, -retentionDays, limit);
        int retired = 0;
        for (Long stateId : stateIds) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Boolean deleted = transaction.execute(status -> {
                List<RetiredState> rows = jdbcTemplate
                    .query("SELECT published_projection_id, building_projection_id" + " FROM automation_ui_scene_definition_read_state WHERE id=?" + " AND status <> 'building' AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP(3))" + " FOR UPDATE", (rs,
                                                                                                                                                                                                                                                                   rowNum) -> new RetiredState(nullableLong(rs, "published_projection_id"), nullableLong(rs, "building_projection_id")), stateId);
                if (rows.size() != 1 || rows.get(0).buildingProjectionId() != null) {
                    return false;
                }
                Long projectionId = rows.get(0).publishedProjectionId();
                if (projectionId != null) {
                    Integer recentNodes = jdbcTemplate
                        .queryForObject("SELECT (SELECT COUNT(*) FROM automation_ui_scene_definition_case_read" + " WHERE projection_id=? AND create_time >= CURRENT_TIMESTAMP(3) - INTERVAL 24 HOUR)" + " + (SELECT COUNT(*) FROM automation_ui_scene_definition_step_read" + " WHERE projection_id=? AND create_time >= CURRENT_TIMESTAMP(3) - INTERVAL 24 HOUR)", Integer.class, projectionId, projectionId);
                    if (recentNodes == null || recentNodes > 0) {
                        return false;
                    }
                    jdbcTemplate
                        .update("UPDATE automation_ui_scene_definition_read_state" + " SET published_projection_id=NULL, update_time=update_time WHERE id=?" + " AND published_projection_id=?", stateId, projectionId);
                    jdbcTemplate
                        .update("DELETE step FROM automation_ui_scene_definition_step_read step" + " WHERE step.projection_id=? AND NOT EXISTS (SELECT 1" + " FROM automation_ui_scene_definition_read_state ref WHERE" + " ref.published_projection_id=step.projection_id OR ref.building_projection_id=step.projection_id)", projectionId);
                    jdbcTemplate
                        .update("DELETE node FROM automation_ui_scene_definition_case_read node" + " WHERE node.projection_id=? AND NOT EXISTS (SELECT 1" + " FROM automation_ui_scene_definition_read_state ref WHERE" + " ref.published_projection_id=node.projection_id OR ref.building_projection_id=node.projection_id)", projectionId);
                }
                return jdbcTemplate
                    .update("DELETE FROM automation_ui_scene_definition_read_state" + " WHERE id=? AND published_projection_id IS NULL AND building_projection_id IS NULL", stateId) == 1;
            });
            if (Boolean.TRUE.equals(deleted)) {
                retired++;
            }
        }
        return retired;
    }

    private BuildClaim claim(String owner) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            List<BuildClaim> candidates = jdbcTemplate
                .query("SELECT id, scene_id, definition_version, source_sha256," + " status, building_projection_id, build_token, retry_count" + " FROM automation_ui_scene_definition_read_state" + " WHERE (status = 'queued' AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3)))" + " OR (status = 'failed' AND next_retry_at IS NOT NULL AND next_retry_at <= CURRENT_TIMESTAMP(3))" + " OR (status = 'building' AND lease_until < CURRENT_TIMESTAMP(3))" + " ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", this::mapClaim);
            if (candidates.isEmpty()) {
                return null;
            }
            BuildClaim current = candidates.get(0);
            boolean takeover = "building".equals(current.status()) && current.projectionId() != null && current
                .buildToken() != null;
            long projectionId = takeover ? current.projectionId() : nextId(current);
            String buildToken = takeover ? current.buildToken() : UUID.randomUUID().toString();
            int updated = jdbcTemplate
                .update("UPDATE automation_ui_scene_definition_read_state" + " SET status='building', building_projection_id=?, build_token=?, lease_owner=?," + " lease_until=TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(3))," + " retry_count=retry_count + ?, next_retry_at=NULL, last_error=NULL, update_time=CURRENT_TIMESTAMP(3)" + " WHERE id=? AND ((status IN ('queued','failed')) OR" + " (status='building' AND lease_until < CURRENT_TIMESTAMP(3)))", projectionId, buildToken, owner, Math
                    .max(30, leaseSeconds), takeover ? 0 : 1, current.stateId());
            if (updated != 1) {
                return null;
            }
            if (takeover) {
                // 续租沿用同一 token/projection，但先清理未发布半成品，避免 case_read_id 指向不存在的新行。
                jdbcTemplate
                    .update("DELETE FROM automation_ui_scene_definition_step_read WHERE projection_id=?", projectionId);
                jdbcTemplate
                    .update("DELETE FROM automation_ui_scene_definition_case_read WHERE projection_id=?", projectionId);
            }
            return new BuildClaim(current.stateId(), current.sceneId(), current.definitionVersion(), current
                .sourceSha256(), "building", projectionId, buildToken, owner, current.retryCount() + (takeover
                    ? 0
                    : 1));
        });
    }

    private SourceDefinition loadSource(BuildClaim claim) {
        List<SourceDefinition> rows = jdbcTemplate
            .query("SELECT CAST(case_list AS CHAR)," + " SHA2(CAST(case_list AS CHAR), 256) FROM automation_ui_scene" + " WHERE id=? AND definition_version=? AND del_flag=3", (rs,
                                                                                                                                                                                rowNum) -> new SourceDefinition(rs
                                                                                                                                                                                    .getString(1), rs
                                                                                                                                                                                        .getString(2), parse(rs
                                                                                                                                                                                            .getString(1))), claim
                                                                                                                                                                                                .sceneId(), claim
                                                                                                                                                                                                    .definitionVersion());
        if (rows.isEmpty() || !Objects.equals(claim.sourceSha256(), rows.get(0).sourceSha256())) {
            throw invalid("source-changed");
        }
        return rows.get(0);
    }

    private ProjectionNodes buildNodes(BuildClaim claim, List<CaseDO> cases) {
        validateDefinition(cases);
        List<CaseNode> caseNodes = new ArrayList<>();
        List<StepNode> stepNodes = new ArrayList<>();
        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            CaseDO caseDO = cases.get(caseIndex);
            List<StepDO> steps = caseDO.getStepList() == null ? List.of() : caseDO.getStepList();
            long caseReadId = nextId(caseDO);
            ObjectNode caseJson = objectMapper.valueToTree(caseDO);
            caseJson.remove("stepList");
            String serializedCase = serializeString(caseJson);
            caseNodes.add(new CaseNode(caseReadId, claim.projectionId(), claim.sceneId(), claim
                .definitionVersion(), caseDO.getId(), caseIndex, caseDO.getName(), steps
                    .size(), serializedCase, sha256(serializedCase)));
            for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
                StepDO step = steps.get(stepIndex);
                // 完整 StepDO 必须原样进入节点 JSON，configList 中的 playwright_step/locator_meta 不能丢失。
                String serializedStep = serializeString(step);
                stepNodes.add(new StepNode(nextId(step), claim.projectionId(), caseReadId, claim.sceneId(), claim
                    .definitionVersion(), caseDO.getId(), step
                        .getId(), stepIndex, serializedStep, sha256(serializedStep)));
            }
        }
        return new ProjectionNodes(caseNodes, stepNodes);
    }

    /** 包级可见仅用于验证投影不变量；失败信息不包含原始业务 ID。 */
    void validateDefinition(List<CaseDO> cases) {
        if (cases == null) {
            throw invalid("null-case-list");
        }
        Set<String> caseIds = new HashSet<>();
        for (CaseDO caseDO : cases) {
            if (caseDO == null) {
                throw invalid("null-case");
            }
            validateNodeId(caseDO.getId(), "case-id");
            if (!caseIds.add(caseDO.getId())) {
                throw invalid("duplicate-case-id");
            }
            Set<String> stepIds = new HashSet<>();
            List<StepDO> steps = caseDO.getStepList() == null ? List.of() : caseDO.getStepList();
            for (StepDO step : steps) {
                if (step == null) {
                    throw invalid("null-step");
                }
                validateNodeId(step.getId(), "step-id");
                if (!stepIds.add(step.getId())) {
                    throw invalid("duplicate-step-id");
                }
                if (!Objects.equals(step.getPid(), caseDO.getId())) {
                    throw invalid("step-parent-mismatch");
                }
            }
        }
    }

    private void persistAndPublish(BuildClaim claim, SourceDefinition source, ProjectionNodes nodes) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            List<Long> leaseRows = jdbcTemplate
                .queryForList("SELECT id FROM automation_ui_scene_definition_read_state WHERE id=? AND status='building'" + " AND building_projection_id=? AND build_token=? AND lease_owner=?" + " AND lease_until >= CURRENT_TIMESTAMP(3) FOR UPDATE", Long.class, claim
                    .stateId(), claim.projectionId(), claim.buildToken(), claim.leaseOwner());
            if (leaseRows.size() != 1) {
                throw new IllegalStateException("投影构建租约已失效");
            }
            for (CaseNode node : nodes.cases()) {
                jdbcTemplate
                    .update("INSERT INTO automation_ui_scene_definition_case_read" + " (id, projection_id, scene_id, definition_version, case_id, case_key, case_index, case_name," + " step_count, case_json, node_sha256, create_time)" + " VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, CAST(? AS JSON)," + " SHA2(CAST(CAST(? AS JSON) AS CHAR), 256), CURRENT_TIMESTAMP(3))", node
                        .id(), node.projectionId(), node.sceneId(), node.definitionVersion(), node.caseId(), node
                            .caseIndex(), node.caseName(), node.stepCount(), node.caseJson(), node.caseJson());
            }
            for (StepNode node : nodes.steps()) {
                jdbcTemplate
                    .update("INSERT INTO automation_ui_scene_definition_step_read" + " (id, projection_id, case_read_id, scene_id, definition_version, case_id, step_id, step_index," + " step_json, node_sha256, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON)," + " SHA2(CAST(CAST(? AS JSON) AS CHAR), 256), CURRENT_TIMESTAMP(3))", node
                        .id(), node.projectionId(), node.caseReadId(), node.sceneId(), node.definitionVersion(), node
                            .caseId(), node.stepId(), node.stepIndex(), node.stepJson(), node.stepJson());
            }
            List<PublishCheck> checks = jdbcTemplate
                .query("SELECT s.definition_version," + " SHA2(CAST(s.case_list AS CHAR), 256)," + " (SELECT COUNT(*) FROM automation_ui_scene_definition_case_read c WHERE c.projection_id=?)," + " (SELECT COUNT(*) FROM automation_ui_scene_definition_step_read st WHERE st.projection_id=?)" + " FROM automation_ui_scene s WHERE s.id=? FOR UPDATE", (rs,
                                                                                                                                                                                                                                                                                                                                                            rowNum) -> new PublishCheck(rs
                                                                                                                                                                                                                                                                                                                                                                .getLong(1), rs
                                                                                                                                                                                                                                                                                                                                                                    .getString(2), rs
                                                                                                                                                                                                                                                                                                                                                                        .getInt(3), rs
                                                                                                                                                                                                                                                                                                                                                                            .getInt(4)), claim
                                                                                                                                                                                                                                                                                                                                                                                .projectionId(), claim
                                                                                                                                                                                                                                                                                                                                                                                    .projectionId(), claim
                                                                                                                                                                                                                                                                                                                                                                                        .sceneId());
            if (checks.isEmpty() || checks.get(0).definitionVersion() != claim.definitionVersion() || !Objects
                .equals(source.sourceSha256(), checks.get(0).sourceSha256()) || checks.get(0).caseCount() != nodes
                    .cases()
                    .size() || checks.get(0).stepCount() != nodes.steps().size()) {
                jdbcTemplate
                    .update("UPDATE automation_ui_scene_definition_read_state SET status='stale'," + " building_projection_id=NULL, build_token=NULL, lease_owner=NULL, lease_until=NULL," + " last_error='publish-integrity-mismatch', update_time=CURRENT_TIMESTAMP(3) WHERE id=?", claim
                        .stateId());
                return;
            }
            int published = jdbcTemplate
                .update("UPDATE automation_ui_scene_definition_read_state" + " SET status='ready', published_projection_id=building_projection_id," + " building_projection_id=NULL, case_count=?, step_count=?, build_token=NULL, lease_owner=NULL," + " lease_until=NULL, next_retry_at=NULL, last_error=NULL, published_at=CURRENT_TIMESTAMP(3)," + " update_time=CURRENT_TIMESTAMP(3) WHERE id=? AND status='building'" + " AND building_projection_id=? AND build_token=?", nodes
                    .cases()
                    .size(), nodes.steps().size(), claim.stateId(), claim.projectionId(), claim.buildToken());
            if (published != 1) {
                throw new IllegalStateException("投影发布条件已变化");
            }
        });
    }

    private void markTerminalFailure(BuildClaim claim, String errorId) {
        jdbcTemplate
            .update("UPDATE automation_ui_scene_definition_read_state SET status='failed'," + " building_projection_id=NULL, build_token=NULL, lease_owner=NULL, lease_until=NULL, next_retry_at=NULL," + " last_error=?, update_time=CURRENT_TIMESTAMP(3) WHERE id=? AND building_projection_id=? AND build_token=?", errorId, claim
                .stateId(), claim.projectionId(), claim.buildToken());
    }

    private void markRecoverableFailure(BuildClaim claim) {
        boolean retry = claim.retryCount() < MAX_RETRY_COUNT;
        jdbcTemplate.update("UPDATE automation_ui_scene_definition_read_state SET status='" + (retry
            ? "queued"
            : "failed") + "'," + " building_projection_id=NULL, build_token=NULL, lease_owner=NULL, lease_until=NULL," + " next_retry_at=" + (retry
                ? "TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP(3))"
                : "NULL") + "," + " last_error=?, update_time=CURRENT_TIMESTAMP(3) WHERE id=? AND building_projection_id=? AND build_token=?", "projection-build-error-" + UUID
                    .randomUUID(), claim.stateId(), claim.projectionId(), claim.buildToken());
    }

    private BuildClaim mapClaim(ResultSet rs, int rowNum) throws SQLException {
        return new BuildClaim(rs.getLong("id"), rs.getLong("scene_id"), rs.getLong("definition_version"), rs
            .getString("source_sha256"), rs.getString("status"), nullableLong(rs, "building_projection_id"), rs
                .getString("build_token"), null, rs.getInt("retry_count"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private List<CaseDO> parse(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, CASE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw invalid("invalid-json");
        }
    }

    private byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("场景定义无法序列化", e);
        }
    }

    private String serializeString(Object value) {
        return new String(serialize(value), StandardCharsets.UTF_8);
    }

    private int countSteps(List<CaseDO> cases) {
        if (cases == null) {
            return 0;
        }
        long count = cases.stream()
            .filter(Objects::nonNull)
            .map(CaseDO::getStepList)
            .filter(Objects::nonNull)
            .mapToLong(List::size)
            .sum();
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("场景步骤数超过数据库上限");
        }
        return (int)count;
    }

    private void validateNodeId(String id, String kind) {
        if (id == null || id.isBlank() || id.length() > MAX_NODE_ID_LENGTH || id.codePoints()
            .anyMatch(Character::isISOControl)) {
            throw invalid(kind);
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

    private InvalidDefinitionException invalid(String category) {
        return new InvalidDefinitionException("definition-projection-" + category + "-" + UUID.randomUUID());
    }

    private String requireLeaseOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 128 || owner.codePoints()
            .anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("投影构建 leaseOwner 非法");
        }
        return owner;
    }

    private int requireLimit(int limit) {
        if (limit <= 0 || limit > MAX_BATCH_LIMIT) {
            throw new IllegalArgumentException("批大小必须在 1 到 " + MAX_BATCH_LIMIT + " 之间");
        }
        return limit;
    }

    private long nextId(Object entity) {
        return identifierGenerator.nextId(entity).longValue();
    }

    private record BuildClaim(long stateId, long sceneId, long definitionVersion, String sourceSha256, String status,
                              Long projectionId, String buildToken, String leaseOwner, int retryCount) {
    }

    private record SourceDefinition(String rawJson, String sourceSha256, List<CaseDO> caseList) {
    }

    private record MetricRow(long sceneId, long definitionVersion, int stepCount) {
    }

    private record BackfillRow(long sceneId, long definitionVersion, String caseJson) {
    }

    private record CaseNode(long id, long projectionId, long sceneId, long definitionVersion, String caseId,
                            int caseIndex, String caseName, int stepCount, String caseJson, String nodeSha256) {
    }

    private record StepNode(long id, long projectionId, long caseReadId, long sceneId, long definitionVersion,
                            String caseId, String stepId, int stepIndex, String stepJson, String nodeSha256) {
    }

    private record ProjectionNodes(List<CaseNode> cases, List<StepNode> steps) {
    }

    private record PublishCheck(long definitionVersion, String sourceSha256, int caseCount, int stepCount) {
    }

    private record RetiredState(Long publishedProjectionId, Long buildingProjectionId) {
    }

    private static final class InvalidDefinitionException extends RuntimeException {
        private final String errorId;

        private InvalidDefinitionException(String errorId) {
            super(errorId);
            this.errorId = errorId;
        }

        private String errorId() {
            return errorId;
        }
    }
}
