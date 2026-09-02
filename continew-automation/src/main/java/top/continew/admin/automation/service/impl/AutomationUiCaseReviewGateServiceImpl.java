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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.automation.converter.AutomationUiCaseFingerprint;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.service.AutomationUiCaseReviewGateService;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.starter.core.exception.BusinessException;

/** Project-level review gate shared by every UI execution entry point. */
@Service
@RequiredArgsConstructor
public class AutomationUiCaseReviewGateServiceImpl implements AutomationUiCaseReviewGateService {

    private static final String ADMIN_PERMISSION = "automation:automationUiScene:review:admin";

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void assertExecutionAllowed(List<AutomationUiSceneDO> scenes,
                                       Map<Long, ? extends Collection<String>> selectedCaseIds,
                                       String triggerType,
                                       String bypassReason,
                                       boolean bypassAuthorized) {
        if (scenes == null || scenes.isEmpty()) {
            return;
        }
        Map<Long, List<BlockedCase>> blockedByScene = new LinkedHashMap<>();
        Map<Long, GatePolicy> policies = new LinkedHashMap<>();
        for (AutomationUiSceneDO scene : scenes) {
            if (scene == null) {
                continue;
            }
            GatePolicy policy = policies.computeIfAbsent(scene.getProjectId(), this::loadPolicy);
            if (!policy.enforced()) {
                continue;
            }
            Set<String> selected = selectedCaseIds == null || !selectedCaseIds.containsKey(scene.getId())
                ? null
                : new LinkedHashSet<>(selectedCaseIds.get(scene.getId()));
            List<CaseDO> targets = resolveTargets(scene, selected);
            List<BlockedCase> blocked = targets.stream().map(caseDO -> {
                AutomationUiCaseFingerprint.Fingerprint fingerprint = AutomationUiCaseFingerprint.compute(caseDO);
                String requirement = missingRequirement(scene.getId(), caseDO.getId(), fingerprint, policy);
                return requirement == null
                    ? null
                    : new BlockedCase(caseDO.getId(), StringUtils.defaultIfBlank(caseDO.getName(), StringUtils
                        .defaultIfBlank(caseDO.getId(), "未命名用例")), fingerprint.hash(), fingerprint
                            .schemaVersion(), requirement);
            }).filter(Objects::nonNull).toList();
            if (!blocked.isEmpty()) {
                blockedByScene.put(scene.getId(), blocked);
            }
        }
        if (blockedByScene.isEmpty()) {
            return;
        }

        String reason = StringUtils.trimToEmpty(bypassReason);
        if (StringUtils.isBlank(reason)) {
            throw new BusinessException(blockMessage(blockedByScene));
        }
        if (reason.length() > 1000) {
            throw new BusinessException("REVIEW_GATE_BYPASS_REASON_TOO_LONG：管理员放行原因不能超过 1000 个字符");
        }
        if (!bypassAuthorized && !StpUtil.hasPermission(ADMIN_PERMISSION)) {
            throw new BusinessException("REVIEW_GATE_BYPASS_FORBIDDEN：仅评审管理员可放行未批准版本");
        }
        auditBypass(scenes, blockedByScene, StringUtils.defaultIfBlank(triggerType, "UNKNOWN"), reason);
    }

    private GatePolicy loadPolicy(Long projectId) {
        if (projectId == null) {
            return GatePolicy.OBSERVE;
        }
        return jdbcTemplate
            .query("SELECT mode, execution_evidence_required, execution_evidence_max_age_h" + " FROM automation_ui_case_review_policy WHERE project_id = ?", (rs,
                                                                                                                                                              rowNum) -> new GatePolicy("ENFORCE"
                                                                                                                                                                  .equalsIgnoreCase(rs
                                                                                                                                                                      .getString("mode")), rs
                                                                                                                                                                          .getBoolean("execution_evidence_required"), rs
                                                                                                                                                                              .getInt("execution_evidence_max_age_h")), projectId)
            .stream()
            .findFirst()
            .orElse(GatePolicy.OBSERVE);
    }

    private List<CaseDO> resolveTargets(AutomationUiSceneDO scene, Set<String> selected) {
        List<CaseDO> source = scene.getCaseList() == null ? List.of() : scene.getCaseList();
        if (selected == null) {
            return source.stream().filter(Objects::nonNull).toList();
        }
        Map<String, CaseDO> cases = new LinkedHashMap<>();
        source.stream().filter(Objects::nonNull).forEach(caseDO -> cases.put(caseDO.getId(), caseDO));
        List<String> missing = selected.stream().filter(caseId -> !cases.containsKey(caseId)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("REVIEW_GATE_CASE_NOT_FOUND：目标用例不存在：" + String.join("、", missing));
        }
        return selected.stream().map(cases::get).toList();
    }

    private String missingRequirement(Long sceneId,
                                      String caseId,
                                      AutomationUiCaseFingerprint.Fingerprint fingerprint,
                                      GatePolicy policy) {
        Integer count = jdbcTemplate
            .queryForObject("SELECT COUNT(*) FROM automation_ui_case_review WHERE scene_id = ? AND case_id = ?" + " AND case_content_hash = ? AND hash_schema_version = ? AND status = 'APPROVED'", Integer.class, sceneId, caseId, fingerprint
                .hash(), fingerprint.schemaVersion());
        if (count == null || count == 0) {
            return "APPROVAL_REQUIRED";
        }
        if (!policy.evidenceRequired()) {
            return null;
        }
        Integer evidence = jdbcTemplate
            .queryForObject("SELECT COUNT(*) FROM automation_ui_execution_case c" + " JOIN automation_ui_execution e ON e.id = c.execution_id WHERE e.scene_id = ? AND c.case_id = ?" + " AND c.case_content_hash = ? AND c.hash_schema_version = ? AND c.finished_at >= ?" + " AND (LOWER(c.status) IN ('passed','pass','success','successful')" + " OR LOWER(c.result) IN ('passed','pass','success','successful'))", Integer.class, sceneId, caseId, fingerprint
                .hash(), fingerprint.schemaVersion(), Timestamp.valueOf(LocalDateTime.now()
                    .minusHours(policy.evidenceMaxAgeHours())));
        return evidence != null && evidence > 0 ? null : "EXECUTION_EVIDENCE_REQUIRED";
    }

    private String blockMessage(Map<Long, List<BlockedCase>> blockedByScene) {
        List<String> names = blockedByScene.values()
            .stream()
            .flatMap(Collection::stream)
            .map(BlockedCase::name)
            .distinct()
            .limit(8)
            .toList();
        int total = blockedByScene.values().stream().mapToInt(List::size).sum();
        String suffix = total > names.size() ? " 等 " + total + " 个用例" : "";
        return "REVIEW_GATE_BLOCKED：当前项目已开启评审门禁，以下用例的当前版本未满足批准或执行证据策略：" + String.join("、", names) + suffix;
    }

    private void auditBypass(List<AutomationUiSceneDO> scenes,
                             Map<Long, List<BlockedCase>> blockedByScene,
                             String triggerType,
                             String reason) {
        Long actor = UserContextHolder.getUserId();
        Map<Long, AutomationUiSceneDO> sceneById = new LinkedHashMap<>();
        scenes.stream().filter(Objects::nonNull).forEach(scene -> sceneById.put(scene.getId(), scene));
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        template.executeWithoutResult(status -> blockedByScene.forEach((sceneId, blocked) -> {
            AutomationUiSceneDO scene = sceneById.get(sceneId);
            List<Map<String, Object>> cases = new ArrayList<>();
            blocked.forEach(item -> cases.add(Map.of("caseId", item.caseId(), "caseName", item
                .name(), "caseContentHash", item.hash(), "hashSchemaVersion", item.schemaVersion(), "requirement", item
                    .requirement())));
            jdbcTemplate
                .update("INSERT INTO automation_ui_case_review_gate_bypass (id, project_id, scene_id, trigger_type," + " reason, actor_id, blocked_cases_json, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", identifierGenerator
                    .nextId(sceneId)
                    .longValue(), scene == null
                        ? null
                        : scene.getProjectId(), sceneId, triggerType, reason, actor, JSONUtil
                            .toJsonStr(cases), Timestamp.valueOf(java.time.LocalDateTime.now()));
        }));
    }

    private record BlockedCase(String caseId, String name, String hash, String schemaVersion, String requirement) {
    }

    private record GatePolicy(boolean enforced, boolean evidenceRequired, int evidenceMaxAgeHours) {
        private static final GatePolicy OBSERVE = new GatePolicy(false, false, 168);
    }
}
