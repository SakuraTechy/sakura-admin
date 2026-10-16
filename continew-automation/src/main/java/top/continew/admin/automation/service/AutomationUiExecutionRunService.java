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

package top.continew.admin.automation.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.mapper.AutomationUiExecutionQueryMapper;
import top.continew.admin.automation.model.req.AutomationUiExecutionRunCreateReq;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightBatchCreateReq;
import top.continew.admin.automation.support.AutomationUiSceneAccessScopeResolver;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.starter.core.exception.BusinessException;

/** 只管理整次任务归属及调度边界，实际执行、认证态和变量仍隔离在原场景批次中。 */
@Service
@RequiredArgsConstructor
public class AutomationUiExecutionRunService {
    private final JdbcTemplate jdbc;
    private final AutomationUiExecutionQueryMapper queryMapper;
    private final AutomationUiSceneAccessScopeResolver accessResolver;

    @Transactional(rollbackFor = Exception.class)
    public String create(AutomationUiExecutionRunCreateReq req) {
        var access = accessResolver.currentScope();
        if (req.getSceneIds() == null || req.getSceneIds().isEmpty() || req.getSceneIds()
            .size() > 10000 || new HashSet<>(req.getSceneIds()).size() != req.getSceneIds().size()) {
            throw new BusinessException("执行任务场景范围为空、重复或超过 10000 个");
        }
        if (!List.of("playwright-runner", "extension-cdp").contains(req.getExecutionType())) {
            throw new BusinessException("执行任务不支持当前引擎");
        }
        for (Long sceneId : req.getSceneIds()) {
            if (queryMapper.selectSceneAccess(sceneId, access.userId(), access.admin()) == null) {
                throw new BusinessException("执行任务场景不存在或无权访问");
            }
            Integer matched = jdbc
                .queryForObject("SELECT COUNT(*) FROM automation_ui_scene s JOIN project_environment_config pe ON pe.project_id = s.project_id WHERE s.id = ? AND pe.id = ?", Integer.class, sceneId, req
                    .getProjectEnvironmentId());
            if (!Objects.equals(matched, 1))
                throw new BusinessException("执行任务的产品环境与场景项目不一致");
        }
        String id = String.valueOf(IdUtil.getSnowflakeNextId());
        String name = Objects.requireNonNullElse(UserContextHolder.getNickname(), Objects
            .requireNonNullElse(UserContextHolder.getUsername(), "-"));
        jdbc.update("INSERT INTO automation_ui_execution_run (id, project_environment_id, execution_engine, execute_user_id, execute_name, create_time) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))", id, req
            .getProjectEnvironmentId(), req.getExecutionType(), access.userId(), name);
        for (int index = 0; index < req.getSceneIds().size(); index++) {
            jdbc.update("INSERT INTO automation_ui_execution_run_scene (run_id, scene_id, scene_order) VALUES (?, ?, ?)", id, req
                .getSceneIds()
                .get(index), index);
        }
        return id;
    }

    /** 与 createBatch 的事务共用行锁，取消与创建不能越过彼此的调度屏障。 */
    public void validateBatch(AutomationPlaywrightBatchCreateReq req, Long sceneId) {
        Map<String, Object> run = lockOwnedRun(req.getRunId());
        if (run.get("finished_at") != null)
            throw new BusinessException("执行任务已结束或取消，不能继续创建子批次");
        if (req.getTestPlanId() != null && !req.getTestPlanId().isBlank() || req.getTestReportId() != null && !req
            .getTestReportId()
            .isBlank()) {
            throw new BusinessException("普通调试任务不能关联测试计划或测试报告");
        }
        if (!Objects.equals(String.valueOf(run.get("project_environment_id")), String.valueOf(req
            .getProjectEnvironmentId())) || !Objects.equals(run.get("execution_engine"), req.getExecutionType())) {
            throw new BusinessException("子批次的环境或引擎与执行任务不一致");
        }
        Integer pending = jdbc
            .queryForObject("SELECT COUNT(*) FROM automation_ui_execution_run_scene WHERE run_id = ? AND scene_id = ? AND batch_id IS NULL AND status = 'queued'", Integer.class, req
                .getRunId(), sceneId);
        if (!Objects.equals(pending, 1))
            throw new BusinessException("场景不属于当前任务、已取消或已创建子批次");
    }

    public void attachBatch(String runId, Long sceneId, String batchId) {
        int changed = jdbc
            .update("UPDATE automation_ui_execution_run_scene SET batch_id = ? WHERE run_id = ? AND scene_id = ? AND batch_id IS NULL AND status = 'queued'", batchId, runId, sceneId);
        if (changed != 1)
            throw new BusinessException("执行任务场景关联发生变化，请刷新历史确认");
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(String runId, Long failedSceneId) {
        Map<String, Object> run = lockOwnedRun(runId);
        // 取消终态不能被迟到的前端完成或失败通知覆盖。
        if (run.get("finished_at") != null)
            return;
        if (failedSceneId != null) {
            jdbc.update("UPDATE automation_ui_execution_run_scene SET status = 'failed' WHERE run_id = ? AND scene_id = ? AND batch_id IS NULL AND status = 'queued'", runId, failedSceneId);
        }
        jdbc.update("UPDATE automation_ui_execution_run_scene SET status = 'cancelled' WHERE run_id = ? AND batch_id IS NULL AND status = 'queued'", runId);
        jdbc.update("UPDATE automation_ui_execution_run SET finished_at = CURRENT_TIMESTAMP(3) WHERE id = ?", runId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> cancel(String runId) {
        lockOwnedRun(runId);
        jdbc.update("UPDATE automation_ui_execution_run_scene SET status = 'cancelled' WHERE run_id = ? AND batch_id IS NULL AND status = 'queued'", runId);
        jdbc.update("UPDATE automation_ui_execution_run SET finished_at = COALESCE(finished_at, CURRENT_TIMESTAMP(3)) WHERE id = ?", runId);
        return jdbc
            .queryForList("SELECT rs.scene_id, rs.batch_id FROM automation_ui_execution_run_scene rs JOIN automation_ui_execution e ON e.scene_id = rs.scene_id AND e.batch_id = rs.batch_id WHERE rs.run_id = ? AND e.status IN ('queued', 'waiting', 'starting', 'running', 'cancelling', '10', '11')", runId);
    }

    private Map<String, Object> lockOwnedRun(String runId) {
        if (runId == null || !runId.matches("[1-9][0-9]{0,18}"))
            throw new BusinessException("执行任务标识无效");
        var access = accessResolver.currentScope();
        List<Map<String, Object>> rows = jdbc
            .queryForList("SELECT * FROM automation_ui_execution_run WHERE id = ? FOR UPDATE", runId);
        if (rows.isEmpty() || !access.admin() && !String.valueOf(access.userId())
            .equals(String.valueOf(rows.get(0).get("execute_user_id")))) {
            throw new BusinessException("执行任务不存在或无权操作");
        }
        return rows.get(0);
    }
}
