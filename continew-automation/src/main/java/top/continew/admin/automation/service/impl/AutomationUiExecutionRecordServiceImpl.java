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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.AutomationUiDefinitionSnapshotMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.support.AutomationExecutionCapability;
import top.continew.starter.core.exception.BusinessException;

/**
 * UI 自动化规范化执行记录服务实现。
 */
@Service
@RequiredArgsConstructor
public class AutomationUiExecutionRecordServiceImpl implements AutomationUiExecutionRecordService {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PLATFORM_DATE_TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_HISTORY_RECORDS = 100;
    private static final int MAX_EXECUTION_SUMMARY_BYTES = 64 * 1024;
    private static final int MAX_CASE_SUMMARY_BYTES = 256 * 1024;
    private static final int MAX_STEP_DIAGNOSTICS_BYTES = 64 * 1024;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final List<String> TERMINAL_EXECUTION_STATUSES = List
        .of("completed", "passed", "failed", "cancelled", "interrupted", "blocked", "skipped");

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveExternalExecutionRecord(AutomationUiSceneDO scene, Map<String, Object> record) {
        saveRecordInternal(scene, record, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecord(AutomationUiSceneDO scene, Map<String, Object> record, String changedCaseId) {
        saveRecordInternal(scene, record, changedCaseId);
    }

    private void saveRecordInternal(AutomationUiSceneDO scene, Map<String, Object> record, String changedCaseId) {
        if (scene == null || scene.getId() == null || record == null || record.isEmpty()) {
            return;
        }
        String rawExecutionKey = resolveRawExecutionKey(record);
        String executionKey = stableExecutionKey(scene.getId(), rawExecutionKey);
        Long executionId = findExecutionId(executionKey);
        // 已存在的 execution 必须继续使用创建时绑定的 revision；场景后续编辑只影响新 execution。
        Long definitionRevisionId = executionId == null
            ? ensureDefinitionRevision(scene)
            : findBoundDefinitionRevision(executionId);
        if (definitionRevisionId == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：执行记录未绑定不可变定义 revision");
        }
        if (executionId == null) {
            executionId = nextId(record);
            try {
                insertExecution(executionId, executionKey, scene, record, definitionRevisionId);
            } catch (DuplicateKeyException ignored) {
                executionId = findExecutionId(executionKey);
                definitionRevisionId = executionId == null ? null : findBoundDefinitionRevision(executionId);
            }
        }
        if (executionId == null || definitionRevisionId == null) {
            throw new IllegalStateException("创建 UI 自动化执行事实失败，executionKey=" + executionKey);
        }
        updateExecution(executionId, scene, record, definitionRevisionId);
        persistCases(executionId, definitionRevisionId, record, changedCaseId);
        upsertSceneState(scene, record, executionId);
        removeReplacedPlaceholder(scene.getId(), nullableLong(record.get("testReportId")), executionId, value(record
            .get("recordType")));
    }

    @Override
    public Map<String, Object> findBatch(Long sceneId, String batchId) {
        if (sceneId == null || StringUtils.isBlank(batchId)) {
            return null;
        }
        Long executionId = queryLong("SELECT id FROM automation_ui_execution WHERE scene_id = ? AND batch_id = ?" + " ORDER BY create_time DESC LIMIT 1", sceneId, batchId);
        if (executionId == null) {
            Integer buildNumber = nullableInteger(batchId);
            if (buildNumber != null) {
                executionId = queryLong("SELECT id FROM automation_ui_execution WHERE scene_id = ? AND build_number = ?" + " ORDER BY create_time DESC LIMIT 1", sceneId, buildNumber);
            }
        }
        return executionId == null ? null : loadRecord(executionId);
    }

    @Override
    public FrozenExecutionCase findFrozenCase(Long sceneId, String batchId, String caseId) {
        if (sceneId == null || StringUtils.isAnyBlank(batchId, caseId)) {
            return null;
        }
        List<FrozenExecutionRef> refs = jdbcTemplate
            .query("SELECT definition_revision_id, project_environment_id, execution_config" + " FROM automation_ui_execution WHERE scene_id = ? AND batch_id = ?" + " ORDER BY create_time DESC LIMIT 1", (rs,
                                                                                                                                                                                                            rowNum) -> new FrozenExecutionRef(rs
                                                                                                                                                                                                                .getLong("definition_revision_id"), nullableLong(rs
                                                                                                                                                                                                                    .getObject("project_environment_id")), rs
                                                                                                                                                                                                                        .getString("execution_config")), sceneId, batchId);
        if (refs.isEmpty()) {
            return null;
        }
        FrozenExecutionRef ref = refs.get(0);
        CaseDO frozenCase = loadFrozenCase(ref.definitionRevisionId(), caseId);
        if (frozenCase == null) {
            return null;
        }
        Map<String, Object> executionConfig = parseMap(ref.executionConfigJson());
        Map<String, Object> effectiveConfig = asMap(asMap(executionConfig.get("cases")).get(caseId));
        return new FrozenExecutionCase(frozenCase, ref.definitionRevisionId(), ref
            .projectEnvironmentId(), effectiveConfig);
    }

    @Override
    public boolean matchesExecutionCapability(Long sceneId, String batchId, String executionCapability) {
        if (sceneId == null || StringUtils.isBlank(batchId) || StringUtils.isBlank(executionCapability)) {
            return false;
        }
        List<CapabilityRow> rows = jdbcTemplate
            .query("SELECT execution_capability_digest, execution_capability_expires_at FROM automation_ui_execution" + " WHERE scene_id = ? AND batch_id = ? ORDER BY create_time DESC LIMIT 1", (rs,
                                                                                                                                                                                                   rowNum) -> new CapabilityRow(rs
                                                                                                                                                                                                       .getString("execution_capability_digest"), rs
                                                                                                                                                                                                           .getTimestamp("execution_capability_expires_at") == null
                                                                                                                                                                                                               ? null
                                                                                                                                                                                                               : rs.getTimestamp("execution_capability_expires_at")
                                                                                                                                                                                                                   .toLocalDateTime()), sceneId, batchId);
        if (rows.isEmpty()) {
            return false;
        }
        CapabilityRow row = rows.get(0);
        return AutomationExecutionCapability.matches(executionCapability, row.digest(), row.expiresAt());
    }

    @Override
    public Map<String, Object> findReportRecord(Long sceneId, String testReportId) {
        Long reportId = nullableLong(testReportId);
        if (sceneId == null || reportId == null) {
            return null;
        }
        Long executionId = queryLong("SELECT id FROM automation_ui_execution WHERE scene_id = ? AND test_report_id = ?" + " ORDER BY create_time DESC LIMIT 1", sceneId, reportId);
        return executionId == null ? null : loadRecord(executionId);
    }

    @Override
    public List<Object> listRecords(Long sceneId, boolean testRecord, int limit) {
        if (sceneId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(MAX_HISTORY_RECORDS, limit));
        String planPredicate = testRecord ? "test_plan_id IS NOT NULL" : "test_plan_id IS NULL";
        List<Long> ids = jdbcTemplate
            .query("SELECT id FROM automation_ui_execution WHERE scene_id = ? AND " + planPredicate + " ORDER BY create_time DESC LIMIT " + safeLimit, (rs,
                                                                                                                                                        rowNum) -> rs
                                                                                                                                                            .getLong(1), sceneId);
        List<Object> result = new ArrayList<>(ids.size());
        ids.forEach(id -> result.add(loadRecord(id)));
        return result;
    }

    @Override
    public Map<Long, Map<String, Object>> findReportRecords(Collection<Long> sceneIds, String testReportId) {
        Long reportId = nullableLong(testReportId);
        if (sceneIds == null || sceneIds.isEmpty() || reportId == null) {
            return Map.of();
        }
        List<Long> ids = sceneIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(reportId);
        args.addAll(ids);
        List<SceneExecutionRef> refs = jdbcTemplate
            .query("SELECT scene_id, MAX(id) AS execution_id" + " FROM automation_ui_execution WHERE test_report_id = ? AND scene_id IN (" + placeholders + ")" + " GROUP BY scene_id", (rs,
                                                                                                                                                                                         rowNum) -> new SceneExecutionRef(rs
                                                                                                                                                                                             .getLong("scene_id"), rs
                                                                                                                                                                                                 .getLong("execution_id")), args
                                                                                                                                                                                                     .toArray());
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        refs.forEach(ref -> result.put(ref.sceneId(), loadRecord(ref.executionId())));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearScene(Long sceneId) {
        if (sceneId == null) {
            return;
        }
        jdbcTemplate
            .update("UPDATE automation_ui_execution_artifact a" + " JOIN automation_ui_execution e ON e.id = a.execution_id" + " SET a.expires_at = CURRENT_TIMESTAMP(3), a.update_time = CURRENT_TIMESTAMP(3)" + " WHERE e.scene_id = ? AND a.storage_status = 'active'", sceneId);
        jdbcTemplate
            .update("DELETE s FROM automation_ui_execution_step s" + " JOIN automation_ui_execution_case c ON c.id = s.execution_case_id" + " JOIN automation_ui_execution e ON e.id = c.execution_id WHERE e.scene_id = ?", sceneId);
        jdbcTemplate
            .update("DELETE c FROM automation_ui_execution_case c" + " JOIN automation_ui_execution e ON e.id = c.execution_id WHERE e.scene_id = ?", sceneId);
        jdbcTemplate.update("DELETE FROM automation_ui_execution WHERE scene_id = ?", sceneId);
        jdbcTemplate.update("DELETE FROM automation_ui_scene_execution_state WHERE scene_id = ?", sceneId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long sceneId) {
        if (sceneId == null) {
            return;
        }
        clearScene(sceneId);
        jdbcTemplate.update("DELETE FROM automation_ui_scene_definition_revision WHERE scene_id = ?", sceneId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTestPlanRecords(Long sceneId, String testPlanId) {
        Long planId = nullableLong(testPlanId);
        if (sceneId == null || planId == null) {
            return;
        }
        List<Long> executionIds = jdbcTemplate
            .query("SELECT id FROM automation_ui_execution" + " WHERE scene_id = ? AND test_plan_id = ?", (rs,
                                                                                                           rowNum) -> rs
                                                                                                               .getLong(1), sceneId, planId);
        deleteExecutions(executionIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markReportIncompleteFailed(String testReportId, String errorMessage) {
        Long reportId = nullableLong(testReportId);
        if (reportId == null) {
            return 0;
        }
        String safeError = abbreviate(errorMessage, MAX_ERROR_LENGTH);
        int updatedCases = jdbcTemplate
            .update("UPDATE automation_ui_execution_case c" + " JOIN automation_ui_execution e ON e.id = c.execution_id" + " SET c.status = 'failed', c.execute_status = 'completed', c.execute_result = 'failed'," + " c.error_message = ?, c.finished_at = CURRENT_TIMESTAMP(3), c.version = c.version + 1," + " c.update_time = CURRENT_TIMESTAMP(3) WHERE e.test_report_id = ?" + " AND c.status NOT IN ('passed','failed','cancelled','blocked','skipped')", safeError, reportId);
        int updatedExecutions = jdbcTemplate
            .update("UPDATE automation_ui_execution SET status = 'completed'," + " result = 'failed', error_message = ?, finished_at = CURRENT_TIMESTAMP(3), version = version + 1," + " update_time = CURRENT_TIMESTAMP(3) WHERE test_report_id = ? AND status NOT IN (" + terminalPlaceholders() + ")", safeError, reportId);
        return updatedCases + updatedExecutions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateLegacyScene(AutomationUiSceneDO scene) {
        if (scene == null || scene.getId() == null) {
            return 0;
        }
        List<Map<String, Object>> legacyRecords = new ArrayList<>();
        collectLegacyRecords(legacyRecords, scene.getDebugRecord());
        collectLegacyRecords(legacyRecords, scene.getTestRecord());
        for (Map<String, Object> record : legacyRecords) {
            saveRecord(scene, record, null);
            String executionKey = stableExecutionKey(scene.getId(), resolveRawExecutionKey(record));
            if (findExecutionId(executionKey) == null) {
                throw new IllegalStateException("旧执行记录迁移校验失败，sceneId=" + scene
                    .getId() + "，executionKey=" + executionKey);
            }
        }
        // 必须在同一事务内先写入并校验执行事实，再清空旧 JSON，避免迁移失败造成历史丢失。
        jdbcTemplate
            .update("UPDATE automation_ui_scene SET debug_record = NULL, test_record = NULL" + " WHERE id = ?", scene
                .getId());
        return legacyRecords.size();
    }

    private Long ensureDefinitionRevision(AutomationUiSceneDO scene) {
        Long definitionVersion = scene.getDefinitionVersion();
        if (definitionVersion == null || definitionVersion < 0) {
            throw new BusinessException("DEFINITION_VERSION_REQUIRED：场景定义版本不能为空");
        }
        AutomationUiDefinitionSnapshotMapper.Snapshot snapshot = AutomationUiDefinitionSnapshotMapper.map(scene
            .getCaseList());
        DefinitionRevisionRef existing = findDefinitionRevision(scene.getId(), definitionVersion);
        if (existing != null) {
            requireSameDefinition(existing, snapshot);
            return existing.id();
        }
        Long id = nextId(scene);
        try {
            jdbcTemplate
                .update("INSERT INTO automation_ui_scene_definition_revision" + " (id, scene_id, revision_no, definition_version, content_hash, definition_json, create_user, create_time)" + " VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))", id, scene
                    .getId(), definitionVersion, definitionVersion, snapshot.contentHash(), snapshot
                        .definitionJson(), scene.getUpdateUser() == null
                            ? scene.getCreateUser()
                            : scene.getUpdateUser());
            return id;
        } catch (DuplicateKeyException ignored) {
            DefinitionRevisionRef raced = findDefinitionRevision(scene.getId(), definitionVersion);
            if (raced == null) {
                throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：并发创建后未找到定义 revision");
            }
            requireSameDefinition(raced, snapshot);
            return raced.id();
        }
    }

    private DefinitionRevisionRef findDefinitionRevision(Long sceneId, Long definitionVersion) {
        return jdbcTemplate
            .query("SELECT id, content_hash FROM automation_ui_scene_definition_revision" + " WHERE scene_id = ? AND definition_version = ? LIMIT 1", (rs,
                                                                                                                                                       rowNum) -> new DefinitionRevisionRef(rs
                                                                                                                                                           .getLong("id"), rs
                                                                                                                                                               .getString("content_hash")), sceneId, definitionVersion)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private void requireSameDefinition(DefinitionRevisionRef revision,
                                       AutomationUiDefinitionSnapshotMapper.Snapshot snapshot) {
        if (!Objects.equals(revision.contentHash(), snapshot.contentHash())) {
            throw new BusinessException("DEFINITION_REVISION_CONFLICT：同一 definitionVersion 已绑定不同定义内容");
        }
    }

    private void insertExecution(Long id,
                                 String executionKey,
                                 AutomationUiSceneDO scene,
                                 Map<String, Object> record,
                                 Long definitionRevisionId) {
        jdbcTemplate
            .update("INSERT INTO automation_ui_execution (id, execution_key, scene_id, scene_key," + " definition_revision_id, execution_capability_digest, execution_capability_expires_at, batch_id, record_type, trigger_type, execution_engine, status, result," + " create_user, create_time, update_user, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?," + " CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", id, executionKey, scene
                .getId(), firstNonBlank(scene.getSceneId(), String.valueOf(scene
                    .getId())), definitionRevisionId, capabilityDigest(record), capabilityExpiresAt(record), nullableText(record
                        .get("batchId")), firstNonBlank(value(record
                            .get("recordType")), "execution"), resolveTriggerType(record), resolveEngine(record), firstNonBlank(value(record
                                .get("executeStatus")), "queued"), nullableText(record.get("executeResult")), scene
                                    .getCreateUser(), scene.getUpdateUser());
    }

    private void updateExecution(Long executionId,
                                 AutomationUiSceneDO scene,
                                 Map<String, Object> record,
                                 Long definitionRevisionId) {
        Map<String, Object> summary = sanitizedMap(record);
        summary.remove("caseResults");
        summary.remove("stepResults");
        summary.remove("executionCapability");
        String summaryJson = boundedSummary(summary, MAX_EXECUTION_SUMMARY_BYTES);
        String executionConfig = boundedSummary(asMap(record.get("executionConfig")), MAX_EXECUTION_SUMMARY_BYTES);
        jdbcTemplate
            .update("UPDATE automation_ui_execution SET definition_revision_id = ?, execution_capability_digest = COALESCE(?, execution_capability_digest), execution_capability_expires_at = COALESCE(?, execution_capability_expires_at), batch_id = ?," + " test_plan_id = ?, test_report_id = ?, record_type = ?, trigger_type = ?, execution_engine = ?," + " status = ?, result = ?, execute_user_id = ?, execute_username = ?, execute_name = ?, execute_email = ?," + " project_environment_id = ?, project_environment_name = ?, execution_config = CAST(? AS JSON)," + " build_number = ?, console_url = ?, test_report_url = ?, case_total = ?, case_pass = ?, case_fail = ?," + " case_skip = ?, case_cancelled = ?, step_total = ?, step_pass = ?, step_fail = ?, step_skip = ?," + " executor_node = ?, heartbeat_at = ?, lease_until = ?, cancel_requested = ?, started_at = ?," + " finished_at = ?, duration_ms = ?, error_code = ?, error_message = ?, summary_json = CAST(? AS JSON)," + " version = version + 1, update_user = ?, update_time = CURRENT_TIMESTAMP(3) WHERE id = ?", definitionRevisionId, capabilityDigest(record), capabilityExpiresAt(record), nullableText(record
                .get("batchId")), nullableLong(record.get("testPlanId")), nullableLong(record
                    .get("testReportId")), firstNonBlank(value(record
                        .get("recordType")), "execution"), resolveTriggerType(record), resolveEngine(record), firstNonBlank(value(record
                            .get("executeStatus")), "queued"), nullableText(record
                                .get("executeResult")), nullableLong(record.get("executeUserId")), nullableText(record
                                    .get("executeUsername")), nullableText(record
                                        .get("executeName")), nullableText(record
                                            .get("executeEmail")), nullableLong(record
                                                .get("projectEnvironmentId")), nullableText(record
                                                    .get("projectEnvironmentName")), executionConfig, nullableInteger(record
                                                        .get("buildNumber")), nullableText(record
                                                            .get("consoleUrl")), nullableText(record
                                                                .get("testReportUrl")), number(record
                                                                    .get("caseTotal")), number(record
                                                                        .get("casePass")), number(record
                                                                            .get("caseFail")), number(record
                                                                                .get("caseSkip")), number(record
                                                                                    .get("caseCancelled")), number(record
                                                                                        .get("stepTotal")), number(record
                                                                                            .get("stepPass")), number(record
                                                                                                .get("stepFail")), number(record
                                                                                                    .get("stepSkip")), nullableText(record
                                                                                                        .get("executorNode")), timestamp(record
                                                                                                            .get("heartbeatAt")), timestamp(record
                                                                                                                .get("leaseUntil")), truthy(record
                                                                                                                    .get("cancelRequested")), timestamp(record
                                                                                                                        .get("startedAt")), timestamp(record
                                                                                                                            .get("finishedAt")), nullableLong(record
                                                                                                                                .get("duration")), nullableText(record
                                                                                                                                    .get("errorCode")), abbreviate(firstNonBlank(value(record
                                                                                                                                        .get("error")), value(record
                                                                                                                                            .get("playwrightError"))), MAX_ERROR_LENGTH), summaryJson, scene
                                                                                                                                                .getUpdateUser(), executionId);
    }

    private void persistCases(Long executionId,
                              Long definitionRevisionId,
                              Map<String, Object> record,
                              String changedCaseId) {
        List<Object> caseResults = asList(record.get("caseResults"));
        for (int caseIndex = 0; caseIndex < caseResults.size(); caseIndex++) {
            Map<String, Object> caseResult = asMap(caseResults.get(caseIndex));
            String caseId = firstNonBlank(value(caseResult.get("case_id")), value(caseResult.get("caseId")));
            if (StringUtils.isBlank(caseId) || StringUtils.isNotBlank(changedCaseId) && !changedCaseId.equals(caseId)) {
                continue;
            }
            int attemptNo = Math.max(1, number(caseResult.get("attempt_no")));
            Long caseExecutionId = queryLong("SELECT id FROM automation_ui_execution_case" + " WHERE execution_id = ? AND case_id = ? AND attempt_no = ? LIMIT 1", executionId, caseId, attemptNo);
            if (caseExecutionId == null) {
                caseExecutionId = nextId(caseResult);
                try {
                    jdbcTemplate
                        .update("INSERT INTO automation_ui_execution_case (id, execution_id, case_id," + " case_index, attempt_no, status, step_total, step_pass, step_fail, step_skip, event_sequence," + " version, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0," + " CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))", caseExecutionId, executionId, caseId, caseIndex, attemptNo, firstNonBlank(value(caseResult
                            .get("status")), "queued"), number(caseResult.get("step_total")), number(caseResult
                                .get("step_pass")), number(caseResult.get("step_fail")), number(caseResult
                                    .get("step_skip")));
                } catch (DuplicateKeyException ignored) {
                    caseExecutionId = queryLong("SELECT id FROM automation_ui_execution_case" + " WHERE execution_id = ? AND case_id = ? AND attempt_no = ? LIMIT 1", executionId, caseId, attemptNo);
                }
            }
            if (caseExecutionId == null) {
                throw new IllegalStateException("创建 UI 自动化用例执行事实失败，caseId=" + caseId);
            }
            updateCase(caseExecutionId, caseIndex, caseResult);
            persistSteps(caseExecutionId, caseResult);
            ensureDefinitionStepExecutions(caseExecutionId, definitionRevisionId, caseId);
            persistArtifacts(executionId, caseExecutionId, caseResult);
        }
    }

    private void ensureDefinitionStepExecutions(Long caseExecutionId, Long definitionRevisionId, String caseId) {
        CaseDO sourceCase = loadFrozenCase(definitionRevisionId, caseId);
        if (sourceCase == null || sourceCase.getStepList() == null) {
            return;
        }
        for (int stepIndex = 0; stepIndex < sourceCase.getStepList().size(); stepIndex++) {
            StepDO sourceStep = sourceCase.getStepList().get(stepIndex);
            Long existingId = queryLong("SELECT id FROM automation_ui_execution_step" + " WHERE execution_case_id = ? AND step_index = ? AND attempt_no = 1 LIMIT 1", caseExecutionId, stepIndex);
            if (existingId != null) {
                continue;
            }
            try {
                // StepExecution 必须来自执行绑定的冻结 revision，后续场景编辑不能补入旧执行。
                jdbcTemplate
                    .update("INSERT INTO automation_ui_execution_step (id, execution_case_id, step_id," + " source_step_id, step_index, attempt_no, step_name, status, event_sequence, create_time, update_time)" + " VALUES (?, ?, ?, ?, ?, 1, ?, 'queued', 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))", nextId(sourceStep), caseExecutionId, sourceStep
                        .getId(), sourceStep.getId(), stepIndex, sourceStep.getName());
            } catch (DuplicateKeyException ignored) {
                // 并发初始化由唯一键收敛，已存在的 StepExecution 保持不变。
            }
        }
    }

    private CaseDO loadFrozenCase(Long definitionRevisionId, String caseId) {
        if (definitionRevisionId == null) {
            throw new BusinessException("DEFINITION_REVISION_NOT_FOUND：执行未绑定定义 revision");
        }
        String definitionJson = jdbcTemplate
            .query("SELECT definition_json FROM automation_ui_scene_definition_revision" + " WHERE id = ? LIMIT 1", (rs,
                                                                                                                     rowNum) -> rs
                                                                                                                         .getString(1), definitionRevisionId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("DEFINITION_REVISION_NOT_FOUND：执行绑定的定义 revision 不存在"));
        try {
            return objectMapper.readValue(definitionJson, new TypeReference<List<CaseDO>>() {
            }).stream().filter(item -> Objects.equals(caseId, item.getId())).findFirst().orElse(null);
        } catch (Exception e) {
            throw new BusinessException("DEFINITION_REVISION_INVALID：执行绑定的定义 revision 无法解析");
        }
    }

    private void updateCase(Long caseExecutionId, int caseIndex, Map<String, Object> caseResult) {
        Map<String, Object> summary = sanitizedMap(caseResult);
        summary.remove("steps");
        String summaryJson = boundedSummary(summary, MAX_CASE_SUMMARY_BYTES);
        jdbcTemplate
            .update("UPDATE automation_ui_execution_case SET case_key = ?, case_execution_key = ?, case_name = ?," + " case_index = ?, job_id = ?, status = ?, result = ?, execute_status = ?, execute_result = ?, step_total = ?," + " step_pass = ?, step_fail = ?, step_skip = ?, started_at = ?, finished_at = ?, duration_ms = ?," + " step_duration_ms = ?, wall_clock_duration_ms = ?, error_code = ?, error_message = ?," + " summary_json = CAST(? AS JSON), event_sequence = event_sequence + 1, version = version + 1," + " update_time = CURRENT_TIMESTAMP(3) WHERE id = ?", nullableText(caseResult
                .get("case_key")), nullableText(caseResult.get("execution_id")), nullableText(caseResult
                    .get("case_name")), caseIndex, nullableText(caseResult
                        .get("job_id")), firstNonBlank(value(caseResult
                            .get("status")), "queued"), nullableText(caseResult.get("result")), nullableText(caseResult
                                .get("executeStatus")), nullableText(caseResult.get("executeResult")), number(caseResult
                                    .get("step_total")), number(caseResult.get("step_pass")), number(caseResult
                                        .get("step_fail")), number(caseResult.get("step_skip")), timestamp(caseResult
                                            .get("started_at")), timestamp(caseResult
                                                .get("finished_at")), nullableLong(caseResult
                                                    .get("duration_ms")), nullableLong(caseResult
                                                        .get("step_duration_ms")), nullableLong(caseResult
                                                            .get("wall_clock_duration_ms")), nullableText(caseResult
                                                                .get("error_code")), abbreviate(value(caseResult
                                                                    .get("error")), MAX_ERROR_LENGTH), summaryJson, caseExecutionId);
    }

    private void persistSteps(Long caseExecutionId, Map<String, Object> caseResult) {
        List<Object> steps = asList(caseResult.get("steps"));
        for (int fallbackIndex = 0; fallbackIndex < steps.size(); fallbackIndex++) {
            Map<String, Object> step = asMap(steps.get(fallbackIndex));
            int stepIndex = step.containsKey("step_index") ? number(step.get("step_index")) : fallbackIndex;
            int attemptNo = Math.max(1, number(step.get("attempt_no")));
            Long stepExecutionId = queryLong("SELECT id FROM automation_ui_execution_step" + " WHERE execution_case_id = ? AND step_index = ? AND attempt_no = ? LIMIT 1", caseExecutionId, stepIndex, attemptNo);
            if (stepExecutionId == null) {
                stepExecutionId = nextId(step);
                try {
                    jdbcTemplate
                        .update("INSERT INTO automation_ui_execution_step (id, execution_case_id, step_index," + " attempt_no, status, event_sequence, create_time, update_time) VALUES (?, ?, ?, ?, ?, 0," + " CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))", stepExecutionId, caseExecutionId, stepIndex, attemptNo, firstNonBlank(value(step
                            .get("status")), "skipped"));
                } catch (DuplicateKeyException ignored) {
                    stepExecutionId = queryLong("SELECT id FROM automation_ui_execution_step" + " WHERE execution_case_id = ? AND step_index = ? AND attempt_no = ? LIMIT 1", caseExecutionId, stepIndex, attemptNo);
                }
            }
            if (stepExecutionId == null) {
                continue;
            }
            String diagnostics = boundedSummary(sanitizedMap(step), MAX_STEP_DIAGNOSTICS_BYTES);
            jdbcTemplate
                .update("UPDATE automation_ui_execution_step SET step_id = ?, source_step_id = ?, action_type = ?," + " step_name = ?, description = ?, status = ?, duration_ms = ?, locator_source = ?, locator_type = ?," + " locator_value = ?, error_code = ?, error_message = ?, diagnostics = CAST(? AS JSON)," + " event_sequence = event_sequence + 1, update_time = CURRENT_TIMESTAMP(3) WHERE id = ?", nullableText(step
                    .get("step_id")), nullableText(step.get("source_step_id")), nullableText(step
                        .get("action_type")), nullableText(step.get("step_name")), abbreviate(value(step
                            .get("description")), 1000), firstNonBlank(value(step
                                .get("status")), "skipped"), nullableLong(step.get("duration_ms")), nullableText(step
                                    .get("actual_locator_source")), nullableText(step
                                        .get("actual_locator_type")), abbreviate(firstNonBlank(value(step
                                            .get("actual_locator_value")), value(step
                                                .get("locator_value"))), 2000), nullableText(step
                                                    .get("error_code")), abbreviate(value(step
                                                        .get("error")), MAX_ERROR_LENGTH), diagnostics, stepExecutionId);
        }
    }

    private void persistArtifacts(Long executionId, Long caseExecutionId, Map<String, Object> caseResult) {
        Map<String, Object> fileIds = asMap(caseResult.get("artifact_file_ids"));
        for (Map.Entry<String, Object> entry : fileIds.entrySet()) {
            Long fileId = nullableLong(entry.getValue());
            if (fileId == null) {
                continue;
            }
            jdbcTemplate
                .update("INSERT INTO automation_ui_execution_artifact (id, execution_id, execution_case_id," + " execution_step_id, artifact_type, file_id, storage_status, expires_at, create_time, update_time)" + " VALUES (?, ?, ?, 0, ?, ?, 'active', DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 90 DAY)," + " CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE file_id = VALUES(file_id)," + " storage_status = 'active', update_time = CURRENT_TIMESTAMP(3)", nextId(entry), executionId, caseExecutionId, abbreviate(entry
                    .getKey(), 64), fileId);
        }
    }

    private void upsertSceneState(AutomationUiSceneDO scene, Map<String, Object> record, Long executionId) {
        jdbcTemplate
            .update("INSERT INTO automation_ui_scene_execution_state (scene_id, latest_execution_id," + " execution_revision, execute_status, execute_result, case_total, case_pass, case_fail, case_skip, pass_rate," + " last_result, step_total, step_pass, step_fail, step_skip, version, create_time, update_time)" + " VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))" + " ON DUPLICATE KEY UPDATE latest_execution_id = VALUES(latest_execution_id)," + " execution_revision = execution_revision + 1, execute_status = VALUES(execute_status)," + " execute_result = VALUES(execute_result), case_total = VALUES(case_total), case_pass = VALUES(case_pass)," + " case_fail = VALUES(case_fail), case_skip = VALUES(case_skip), pass_rate = VALUES(pass_rate)," + " last_result = VALUES(last_result), step_total = VALUES(step_total), step_pass = VALUES(step_pass)," + " step_fail = VALUES(step_fail), step_skip = VALUES(step_skip), version = version + 1," + " update_time = CURRENT_TIMESTAMP(3)", scene
                .getId(), executionId, nullableText(record.get("executeStatus")), nullableText(record
                    .get("executeResult")), number(record.get("caseTotal")), number(record
                        .get("casePass")), number(record.get("caseFail")), number(record
                            .get("caseSkip")), firstNonBlank(value(record.get("casePassRate")), value(record
                                .get("scenePassRate"))), nullableText(record.get("executeResult")), number(record
                                    .get("stepTotal")), number(record.get("stepPass")), number(record
                                        .get("stepFail")), number(record.get("stepSkip")));
    }

    private Map<String, Object> loadRecord(Long executionId) {
        List<ExecutionRow> rows = jdbcTemplate.query("SELECT * FROM automation_ui_execution WHERE id = ?", (rs,
                                                                                                            rowNum) -> {
            ExecutionRow row = new ExecutionRow();
            row.id = rs.getLong("id");
            row.batchId = rs.getString("batch_id");
            row.testPlanId = nullableLong(rs.getObject("test_plan_id"));
            row.testReportId = nullableLong(rs.getObject("test_report_id"));
            row.recordType = rs.getString("record_type");
            row.engine = rs.getString("execution_engine");
            row.status = rs.getString("status");
            row.result = rs.getString("result");
            row.startedAt = toLocalDateTime(rs.getTimestamp("started_at"));
            row.finishedAt = toLocalDateTime(rs.getTimestamp("finished_at"));
            row.durationMs = nullableLong(rs.getObject("duration_ms"));
            row.error = rs.getString("error_message");
            row.caseTotal = rs.getInt("case_total");
            row.casePass = rs.getInt("case_pass");
            row.caseFail = rs.getInt("case_fail");
            row.caseSkip = rs.getInt("case_skip");
            row.caseCancelled = rs.getInt("case_cancelled");
            row.stepTotal = rs.getInt("step_total");
            row.stepPass = rs.getInt("step_pass");
            row.stepFail = rs.getInt("step_fail");
            row.stepSkip = rs.getInt("step_skip");
            row.summaryJson = rs.getString("summary_json");
            return row;
        }, executionId);
        if (rows.isEmpty()) {
            return null;
        }
        ExecutionRow row = rows.get(0);
        Map<String, Object> record = parseMap(row.summaryJson);
        putIfNotBlank(record, "recordType", row.recordType);
        putIfNotBlank(record, "batchId", row.batchId);
        record.putIfAbsent("executionId", StringUtils.defaultIfBlank(row.batchId, String.valueOf(row.id)));
        putIfNotBlank(record, "executionType", row.engine);
        putIfNotBlank(record, "executor", row.engine);
        if (row.testPlanId != null)
            record.put("testPlanId", String.valueOf(row.testPlanId));
        if (row.testReportId != null)
            record.put("testReportId", String.valueOf(row.testReportId));
        putIfNotBlank(record, "executeStatus", row.status);
        putIfNotBlank(record, "executeResult", row.result);
        putIfNotBlank(record, "startedAt", format(row.startedAt));
        putIfNotBlank(record, "finishedAt", format(row.finishedAt));
        if (row.durationMs != null)
            record.put("duration", row.durationMs);
        putIfNotBlank(record, "error", row.error);
        record.put("caseTotal", row.caseTotal);
        record.put("casePass", row.casePass);
        record.put("caseFail", row.caseFail);
        record.put("caseSkip", row.caseSkip);
        record.put("caseCancelled", row.caseCancelled);
        record.put("stepTotal", row.stepTotal);
        record.put("stepPass", row.stepPass);
        record.put("stepFail", row.stepFail);
        record.put("stepSkip", row.stepSkip);
        record.put("caseResults", loadCases(executionId));
        return record;
    }

    private List<Object> loadCases(Long executionId) {
        return jdbcTemplate
            .query("SELECT * FROM automation_ui_execution_case WHERE execution_id = ?" + " ORDER BY case_index, attempt_no", (rs,
                                                                                                                              rowNum) -> {
                Long caseExecutionId = rs.getLong("id");
                Map<String, Object> result = parseMap(rs.getString("summary_json"));
                result.put("case_id", rs.getString("case_id"));
                putIfNotBlank(result, "case_key", rs.getString("case_key"));
                putIfNotBlank(result, "execution_id", rs.getString("case_execution_key"));
                putIfNotBlank(result, "case_name", rs.getString("case_name"));
                result.put("status", rs.getString("status"));
                putIfNotBlank(result, "executeStatus", rs.getString("execute_status"));
                putIfNotBlank(result, "executeResult", rs.getString("execute_result"));
                result.put("step_total", rs.getInt("step_total"));
                result.put("step_pass", rs.getInt("step_pass"));
                result.put("step_fail", rs.getInt("step_fail"));
                result.put("step_skip", rs.getInt("step_skip"));
                result.put("duration_ms", rs.getLong("duration_ms"));
                putIfNotBlank(result, "error", rs.getString("error_message"));
                result.put("steps", loadSteps(caseExecutionId));
                return (Object)result;
            }, executionId);
    }

    private List<Object> loadSteps(Long caseExecutionId) {
        return jdbcTemplate
            .query("SELECT diagnostics, step_id, source_step_id, step_index, attempt_no, action_type," + " step_name, description, status, duration_ms, locator_source, locator_type, locator_value, error_message" + " FROM automation_ui_execution_step WHERE execution_case_id = ? ORDER BY step_index, attempt_no", (rs,
                                                                                                                                                                                                                                                                                                                         rowNum) -> {
                Map<String, Object> step = parseMap(rs.getString("diagnostics"));
                putIfNotBlank(step, "step_id", rs.getString("step_id"));
                putIfNotBlank(step, "source_step_id", rs.getString("source_step_id"));
                step.put("step_index", rs.getInt("step_index"));
                step.put("attempt_no", rs.getInt("attempt_no"));
                putIfNotBlank(step, "action_type", rs.getString("action_type"));
                putIfNotBlank(step, "step_name", rs.getString("step_name"));
                putIfNotBlank(step, "description", rs.getString("description"));
                step.put("status", rs.getString("status"));
                step.put("duration_ms", rs.getLong("duration_ms"));
                putIfNotBlank(step, "actual_locator_source", rs.getString("locator_source"));
                putIfNotBlank(step, "actual_locator_type", rs.getString("locator_type"));
                putIfNotBlank(step, "actual_locator_value", rs.getString("locator_value"));
                putIfNotBlank(step, "error", rs.getString("error_message"));
                return (Object)step;
            }, caseExecutionId);
    }

    private void deleteExecutions(List<Long> executionIds) {
        if (executionIds == null || executionIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(executionIds.size(), "?"));
        Object[] args = executionIds.toArray();
        jdbcTemplate
            .update("UPDATE automation_ui_execution_artifact" + " SET expires_at = CURRENT_TIMESTAMP(3), update_time = CURRENT_TIMESTAMP(3)" + " WHERE execution_id IN (" + placeholders + ") AND storage_status = 'active'", args);
        jdbcTemplate
            .update("DELETE s FROM automation_ui_execution_step s JOIN automation_ui_execution_case c" + " ON c.id = s.execution_case_id WHERE c.execution_id IN (" + placeholders + ")", args);
        jdbcTemplate
            .update("DELETE FROM automation_ui_execution_case WHERE execution_id IN (" + placeholders + ")", args);
        jdbcTemplate.update("DELETE FROM automation_ui_execution WHERE id IN (" + placeholders + ")", args);
    }

    private void removeReplacedPlaceholder(Long sceneId, Long reportId, Long currentExecutionId, String recordType) {
        if (reportId == null || "plan-execution-placeholder".equals(recordType)) {
            return;
        }
        List<Long> placeholders = jdbcTemplate
            .query("SELECT id FROM automation_ui_execution WHERE scene_id = ?" + " AND test_report_id = ? AND record_type = 'plan-execution-placeholder' AND id <> ?", (rs,
                                                                                                                                                                        rowNum) -> rs
                                                                                                                                                                            .getLong(1), sceneId, reportId, currentExecutionId);
        deleteExecutions(placeholders);
    }

    private Long findExecutionId(String executionKey) {
        return queryLong("SELECT id FROM automation_ui_execution WHERE execution_key = ? LIMIT 1", executionKey);
    }

    private Long findBoundDefinitionRevision(Long executionId) {
        return queryLong("SELECT definition_revision_id FROM automation_ui_execution WHERE id = ? LIMIT 1", executionId);
    }

    private String capabilityDigest(Map<String, Object> record) {
        String token = nullableText(record.get("executionCapability"));
        return token == null ? null : AutomationExecutionCapability.digest(token);
    }

    private Timestamp capabilityExpiresAt(Map<String, Object> record) {
        return capabilityDigest(record) == null ? null : Timestamp.valueOf(AutomationExecutionCapability.expiresAt());
    }

    private String resolveRawExecutionKey(Map<String, Object> record) {
        String rawExecutionKey = firstNonBlank(value(record.get("batchId")), value(record
            .get("executionId")), value(record.get("buildNumber")));
        return StringUtils.isBlank(rawExecutionKey)
            ? "legacy-" + DigestUtil.sha256Hex(boundedSummary(record, MAX_EXECUTION_SUMMARY_BYTES))
            : rawExecutionKey;
    }

    private void collectLegacyRecords(List<Map<String, Object>> target, List<Object> records) {
        if (records == null) {
            return;
        }
        for (Object item : records) {
            Map<String, Object> record = asMap(item);
            if (!record.isEmpty()) {
                target.add(record);
            }
        }
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> values = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Object value = rs.getObject(1);
            return value == null ? null : ((Number)value).longValue();
        }, args);
        return values.isEmpty() ? null : values.get(0);
    }

    private Long nextId(Object entity) {
        return identifierGenerator.nextId(entity).longValue();
    }

    private String stableExecutionKey(Long sceneId, String rawKey) {
        String key = sceneId + ":" + rawKey;
        return key.length() <= 128 ? key : sceneId + ":sha256:" + DigestUtil.sha256Hex(key);
    }

    private String resolveTriggerType(Map<String, Object> record) {
        String explicit = value(record.get("triggerType"));
        if (StringUtils.isNotBlank(explicit))
            return explicit.trim().toLowerCase().replace('_', '-');
        if (record.get("testPlanId") != null)
            return "test-plan";
        return "jenkins".equalsIgnoreCase(resolveEngine(record)) ? "jenkins" : "manual";
    }

    private String resolveEngine(Map<String, Object> record) {
        String engine = firstNonBlank(value(record.get("executionType")), value(record.get("executor")), "unknown")
            .trim()
            .toLowerCase()
            .replace('_', '-');
        return switch (engine) {
            case "playwright", "runner", "playwright-runner" -> "playwright-runner";
            case "chrome-devtools-protocol", "cdp", "extension-cdp" -> "extension-cdp";
            default -> engine;
        };
    }

    private String resolveRunKey(Long executionId, Map<String, Object> record) {
        Long reportId = nullableLong(record.get("testReportId"));
        if (reportId != null) {
            return "REPORT:" + reportId;
        }
        String batchId = value(record.get("batchId"));
        if (StringUtils.isNotBlank(batchId)) {
            return abbreviate(resolveEngine(record).toUpperCase() + ":BATCH:" + batchId, 192);
        }
        return "EXECUTION:" + executionId;
    }

    private String boundedSummary(Map<String, Object> source, int maxBytes) {
        String json = JSONUtil.toJsonStr(source == null ? Map.of() : source);
        if (json.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return json;
        }
        Map<String, Object> minimal = new LinkedHashMap<>();
        for (String key : List
            .of("recordType", "batchId", "executionId", "executionType", "executeStatus", "executeResult", "testPlanId", "testReportId", "case_id", "case_name", "status", "step_id", "step_index", "step_name", "action_type", "duration_ms", "error")) {
            if (source.containsKey(key)) {
                minimal.put(key, sanitizeScalar(key, source.get(key)));
            }
        }
        minimal.put("_truncated", true);
        return JSONUtil.toJsonStr(minimal);
    }

    private Map<String, Object> sanitizedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> {
            Object sanitized = sanitizeValue(key, value);
            if (sanitized != null) {
                result.put(key, sanitized);
            }
        });
        return result;
    }

    private Object sanitizeValue(String key, Object value) {
        if (value instanceof Map<?, ?>) {
            return sanitizedMap(asMap(value));
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object sanitized = sanitizeValue(key, item);
                if (sanitized != null)
                    result.add(sanitized);
            }
            return result;
        }
        return sanitizeScalar(key, value);
    }

    private Object sanitizeScalar(String key, Object value) {
        if (!(value instanceof CharSequence)) {
            return value;
        }
        String text = String.valueOf(value);
        String normalizedKey = StringUtils.defaultString(key).toLowerCase().replace('-', '_');
        if (normalizedKey.contains("password") || normalizedKey.contains("token") || normalizedKey
            .contains("authorization") || normalizedKey.contains("cookie")) {
            return "[REDACTED]";
        }
        if (normalizedKey.contains("base64") || text.regionMatches(true, 0, "data:image/", 0, 11) || text
            .regionMatches(true, 0, "data:video/", 0, 11)) {
            return null;
        }
        return abbreviate(text, MAX_TEXT_LENGTH);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>((List<Object>)list) : new ArrayList<>();
    }

    private Map<String, Object> parseMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return asMap(JSONUtil.parseObj(json));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Timestamp timestamp(Object value) {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime parseDateTime(Object value) {
        String text = value(value);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return Instant.parse(text).atZone(PLATFORM_ZONE_ID).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续兼容偏移量和平台本地时间。
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(PLATFORM_ZONE_ID).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续兼容本地时间。
        }
        for (DateTimeFormatter formatter : List
            .of(DateTimeFormatter.ISO_LOCAL_DATE_TIME, PLATFORM_DATE_TIME_FORMATTER)) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一格式。
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(PLATFORM_DATE_TIME_FORMATTER);
    }

    private Long nullableLong(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer nullableInteger(Object value) {
        Long number = nullableLong(value);
        return number == null ? null : number.intValue();
    }

    private int number(Object value) {
        Long number = nullableLong(value);
        return number == null ? 0 : Math.max(0, number.intValue());
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String
            .valueOf(value));
    }

    private String nullableText(Object value) {
        String text = value(value);
        return StringUtils.isBlank(text) ? null : abbreviate(text, 1000);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value))
                return value;
        }
        return "";
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private String terminalPlaceholders() {
        return String.join(",", TERMINAL_EXECUTION_STATUSES.stream().map(item -> "'" + item + "'").toList());
    }

    private record SceneExecutionRef(Long sceneId, Long executionId) {
    }

    private record DefinitionRevisionRef(Long id, String contentHash) {
    }

    private record CapabilityRow(String digest, LocalDateTime expiresAt) {
    }

    private record FrozenExecutionRef(Long definitionRevisionId, Long projectEnvironmentId,
                                      String executionConfigJson) {
    }

    private static final class ExecutionRow {
        private Long id;
        private String batchId;
        private Long testPlanId;
        private Long testReportId;
        private String recordType;
        private String engine;
        private String status;
        private String result;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private String error;
        private int caseTotal;
        private int casePass;
        private int caseFail;
        private int caseSkip;
        private int caseCancelled;
        private int stepTotal;
        private int stepPass;
        private int stepFail;
        private int stepSkip;
        private String summaryJson;
    }
}
