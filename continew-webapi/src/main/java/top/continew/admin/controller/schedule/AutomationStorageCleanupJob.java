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

package top.continew.admin.controller.schedule;

import java.util.List;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.admin.system.service.FileService;

/**
 * UI 自动化存储治理任务。
 *
 * <p>只做小批量、可重复的过期数据清理，不执行 OPTIMIZE TABLE，避免在磁盘紧张时制造额外临时空间需求。
 * 执行表正式切换后再启用该任务。</p>
 */
@Component
@RequiredArgsConstructor
public class AutomationStorageCleanupJob {

    private static final String EXECUTOR_NAME = "CleanupAutomationStorage";
    private static final int BATCH_SIZE = 5000;
    private static final int FILE_BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final FileService fileService;

    @Value("${automation.storage-cleanup.sys-log-retention-days:30}")
    private int sysLogRetentionDays;

    @Value("${automation.storage-cleanup.sys-log-failure-retention-days:90}")
    private int sysLogFailureRetentionDays;

    @Value("${automation.storage-cleanup.job-retention-days:30}")
    private int jobRetentionDays;

    @Value("${automation.storage-cleanup.execution-retention-days:90}")
    private int executionRetentionDays;

    @JobExecutor(name = EXECUTOR_NAME)
    public void cleanup() {
        int deletedLog = deleteSysLog();
        int deletedArtifact = deleteExpiredArtifacts();
        int deletedJob = delete("automation_playwright_job", "finished_at", jobRetentionDays, true);
        ExecutionCleanupResult executionCleanup = deleteExpiredExecutions();
        SnailJobLog.REMOTE
            .info("UI 自动化存储清理完成，sysLog={}, jobs={}, steps={}, cases={}, executions={}, artifacts={}", deletedLog, deletedJob, executionCleanup
                .steps(), executionCleanup.cases(), executionCleanup.executions(), deletedArtifact);
    }

    private int deleteSysLog() {
        int deletedSuccess = deleteByStatus("sys_log", "create_time", sysLogRetentionDays, "status = 1");
        int deletedFailure = deleteByStatus("sys_log", "create_time", sysLogFailureRetentionDays, "status = 2");
        return deletedSuccess + deletedFailure;
    }

    private int delete(String table, String timeColumn, int retentionDays, boolean terminalOnly) {
        if (retentionDays <= 0) {
            return 0;
        }
        String terminalClause = terminalOnly
            ? " AND status IN ('completed','passed','failed','cancelled','interrupted','blocked','skipped')"
            : "";
        int safeRetentionDays = Math.max(1, retentionDays);
        String sql = "DELETE FROM " + table + " WHERE " + timeColumn + " < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL " + safeRetentionDays + " DAY)" + terminalClause + " ORDER BY " + timeColumn + " LIMIT " + BATCH_SIZE;
        return jdbcTemplate.update(sql);
    }

    private int deleteByStatus(String table, String timeColumn, int retentionDays, String statusPredicate) {
        if (retentionDays <= 0) {
            return 0;
        }
        int safeRetentionDays = Math.max(1, retentionDays);
        String sql = "DELETE FROM " + table + " WHERE " + statusPredicate + " AND " + timeColumn + " < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL " + safeRetentionDays + " DAY) ORDER BY " + timeColumn + " LIMIT " + BATCH_SIZE;
        return jdbcTemplate.update(sql);
    }

    private int deleteExpiredArtifacts() {
        List<ArtifactRef> artifacts = jdbcTemplate
            .query("SELECT id, file_id FROM automation_ui_execution_artifact" + " WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP" + " AND storage_status = 'active' ORDER BY expires_at LIMIT " + FILE_BATCH_SIZE, (rs,
                                                                                                                                                                                                                                     rowNum) -> {
                Object fileId = rs.getObject("file_id");
                return new ArtifactRef(rs.getLong("id"), fileId instanceof Number number ? number.longValue() : null);
            });
        List<Long> fileIds = List.of();
        if (!artifacts.isEmpty()) {
            String candidateIds = placeholders(artifacts.size());
            Object[] candidateArgs = artifacts.stream().map(ArtifactRef::id).toArray();
            fileIds = jdbcTemplate
                .query("SELECT DISTINCT a.file_id FROM automation_ui_execution_artifact a" + " WHERE a.id IN (" + candidateIds + ") AND a.file_id IS NOT NULL" + " AND NOT EXISTS (SELECT 1 FROM automation_ui_execution_artifact other" + " WHERE other.file_id = a.file_id AND other.storage_status = 'active'" + " AND other.id <> a.id)", (rs,
                                                                                                                                                                                                                                                                                                                                               rowNum) -> rs
                                                                                                                                                                                                                                                                                                                                                   .getLong(1), candidateArgs);
        }
        if (!fileIds.isEmpty()) {
            // 先通过统一文件服务删除真实对象；失败时不更新引用状态，下一轮可安全重试。
            fileService.delete(fileIds);
        }
        if (!artifacts.isEmpty()) {
            String ids = placeholders(artifacts.size());
            Object[] args = artifacts.stream().map(ArtifactRef::id).toArray();
            jdbcTemplate
                .update("UPDATE automation_ui_execution_artifact SET storage_status = 'deleted'," + " update_time = CURRENT_TIMESTAMP(3) WHERE id IN (" + ids + ")", args);
        }
        return jdbcTemplate
            .update("DELETE FROM automation_ui_execution_artifact" + " WHERE storage_status IN ('deleted','expired')" + " ORDER BY update_time LIMIT " + BATCH_SIZE);
    }

    private ExecutionCleanupResult deleteExpiredExecutions() {
        if (executionRetentionDays <= 0) {
            return new ExecutionCleanupResult(0, 0, 0);
        }
        int days = Math.max(1, executionRetentionDays);
        List<Long> ids = jdbcTemplate
            .query("SELECT e.id FROM automation_ui_execution e" + " WHERE e.retention_hold = 0" + " AND e.status IN ('completed','passed','failed','cancelled','interrupted','blocked','skipped')" + " AND COALESCE(e.finished_at, e.create_time) < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL " + days + " DAY)" + " AND NOT EXISTS (SELECT 1 FROM automation_ui_execution_artifact a WHERE a.execution_id = e.id)" + " ORDER BY e.id LIMIT " + BATCH_SIZE, (rs,
                                                                                                                                                                                                                                                                                                                                                                                                                                                        rowNum) -> rs
                                                                                                                                                                                                                                                                                                                                                                                                                                                            .getLong(1));
        if (ids.isEmpty()) {
            return new ExecutionCleanupResult(0, 0, 0);
        }
        String placeholders = placeholders(ids.size());
        Object[] args = ids.toArray();
        int steps = jdbcTemplate
            .update("DELETE s FROM automation_ui_execution_step s" + " JOIN automation_ui_execution_case c ON c.id = s.execution_case_id" + " WHERE c.execution_id IN (" + placeholders + ")", args);
        int cases = jdbcTemplate
            .update("DELETE FROM automation_ui_execution_case WHERE execution_id IN (" + placeholders + ")", args);
        int executions = jdbcTemplate
            .update("DELETE FROM automation_ui_execution WHERE id IN (" + placeholders + ")", args);
        return new ExecutionCleanupResult(steps, cases, executions);
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private record ArtifactRef(Long id, Long fileId) {
    }

    private record ExecutionCleanupResult(int steps, int cases, int executions) {
    }
}
