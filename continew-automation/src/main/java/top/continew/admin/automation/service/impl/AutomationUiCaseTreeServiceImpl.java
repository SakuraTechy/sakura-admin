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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.converter.AutomationOperationStepAssembler;
import top.continew.admin.automation.converter.AutomationOperationStepReverseAdapter;
import top.continew.admin.automation.mapper.AutomationPlaywrightJobMapper;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.CaseExecutionConfigDO;
import top.continew.admin.automation.model.entity.ui.CaseOriginDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.enums.AutomationUiTreeMovePosition;
import top.continew.admin.automation.model.enums.AutomationUiTreeNodeType;
import top.continew.admin.automation.model.req.AutomationUiTreeCopyReq;
import top.continew.admin.automation.model.req.AutomationUiTreeDeleteReq;
import top.continew.admin.automation.model.req.AutomationUiTreeMoveReq;
import top.continew.admin.automation.model.req.AutomationUiTreeNodeRefReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepConfigEditReq;
import top.continew.admin.automation.model.req.ui.AutomationUiStepCopyReq;
import top.continew.admin.automation.model.resp.AutomationUiTreeMutationResp;
import top.continew.admin.automation.model.resp.AutomationUiTreeNodeRefResp;
import top.continew.admin.automation.service.AutomationUiCaseTreeService;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
import top.continew.starter.core.exception.BusinessException;

/**
 * 用稳定业务 ID 操作 case_list。这里故意不复用旧的拖拽 DTO，避免 UI 临时字段进入持久化 JSON。
 */
@Service
@RequiredArgsConstructor
public class AutomationUiCaseTreeServiceImpl implements AutomationUiCaseTreeService {
    private static final String DEFAULT_CASE_PREFIX = "CASE_";
    private static final String DEFAULT_STEP_PREFIX = "CASE_STEP_";
    private static final String CASE_SEQUENCE_SCOPE = "CASE";
    private static final Set<String> INFRASTRUCTURE_ACTION_TYPES = Set
        .of("server_command", "database_sql", "database_native", "infra-server-command", "infra-database-sql", "infra-database-native");
    private static final Map<String, String> ERROR_MESSAGES = Map.ofEntries(Map
        .entry("SCENE_NOT_FOUND", "场景不存在或已被删除"), Map.entry("TREE_NODE_CONFLICT", "节点 ID 已存在，请更换后重试"), Map
            .entry("TREE_SOURCE_NOT_FOUND", "源节点不存在，请刷新后重试"), Map.entry("TREE_TARGET_NOT_FOUND", "目标节点不存在，请刷新后重试"), Map
                .entry("TREE_DROP_NOT_ALLOWED", "当前节点不能移动到所选位置"), Map
                    .entry("TREE_DELETE_TARGET_EMPTY", "请选择要删除的用例或步骤"), Map
                        .entry("TREE_CONCURRENT_MODIFICATION", "场景定义已被其他操作修改，请刷新后重试"), Map
                            .entry("TREE_SCENE_EXECUTING", "场景正在执行，暂不能修改用例树"));
    private static final Set<String> IMMUTABLE_RECORDING_CONFIGS = Set
        .of("playwright_step", "original_case_id", "original_step_id", "recording_id", "value_masked", "target_selector", "target_xpath", "url", "screenshot_url", "screenshot_file_id", "screenshot_path", "screenshot_present");

    private final AutomationUiSceneMapper sceneMapper;
    private final AutomationPlaywrightJobMapper playwrightJobMapper;
    private final AutomationOperationStepAssembler operationStepAssembler;
    private final AutomationOperationStepReverseAdapter operationStepReverseAdapter;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp addCase(Long sceneDbId, CaseDO request) {
        return mutate(sceneDbId, request.getExpectedDefinitionVersion(), true, scene -> {
            CaseDO added = copyCase(request);
            String requestedId = request.getId() == null ? "" : request.getId().trim();
            if (requestedId.isEmpty() || requestedId.endsWith("_")) {
                added.setId(nextId(scene.getId(), CASE_SEQUENCE_SCOPE, caseIds(scene.getCaseList()), requestedId
                    .isEmpty() ? DEFAULT_CASE_PREFIX : requestedId));
            } else if (caseIds(scene.getCaseList()).contains(requestedId)) {
                throw error("TREE_NODE_CONFLICT");
            }
            added.setType("case");
            allocateInitialStepIds(scene.getId(), added);
            rewriteStepParents(added);
            assembleManualSteps(added);
            scene.getCaseList().add(insertionIndex(request.getOrder(), scene.getCaseList().size(), "用例"), added);
            return result(true, caseRef(added.getId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp updateCase(Long sceneDbId, CaseDO request) {
        return mutate(sceneDbId, request.getExpectedDefinitionVersion(), scene -> {
            CaseDO target = requireCase(scene.getCaseList(), request.getId(), "TREE_SOURCE_NOT_FOUND");
            target.setName(request.getName());
            target.setRemark(request.getRemark());
            target.setStatus(request.getStatus());
            if (request.getExecutionConfig() != null) {
                // 起始地址、窗口和截图策略属于用例级定义，不能继续从第一条步骤反推。
                target.setExecutionConfig(copyExecutionConfig(request.getExecutionConfig()));
            }
            return result(true, caseRef(target.getId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp addStep(Long sceneDbId, StepDO request) {
        return mutate(sceneDbId, request.getExpectedDefinitionVersion(), scene -> {
            CaseDO parent = requireCase(scene.getCaseList(), request.getPid(), "TREE_TARGET_NOT_FOUND");
            StepDO added = copyStep(request);
            String requestedId = request.getId() == null ? "" : request.getId().trim();
            if (requestedId.isEmpty() || requestedId.endsWith("_")) {
                added.setId(nextId(scene.getId(), stepSequenceScope(parent.getId()), stepIds(parent), requestedId
                    .isEmpty() ? DEFAULT_STEP_PREFIX : requestedId));
            } else if (stepIds(parent).contains(requestedId)) {
                throw error("TREE_NODE_CONFLICT");
            }
            added.setType("step");
            added.setPid(parent.getId());
            // 先分配稳定 StepDO.id，再生成执行快照，避免 raw step 内残留 CASE_STEP_ 占位 ID。
            added = operationStepAssembler.assembleManualStep(added);
            parent.getStepList().add(insertionIndex(request.getOrder(), parent.getStepList().size(), "步骤"), added);
            return result(true, stepRef(parent.getId(), added.getId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp updateStep(Long sceneDbId, StepDO request) {
        return mutate(sceneDbId, request.getExpectedDefinitionVersion(), scene -> {
            AutomationUiTreeNodeRefReq ref = new AutomationUiTreeNodeRefReq();
            ref.setType(AutomationUiTreeNodeType.STEP);
            ref.setCaseId(request.getPid());
            ref.setStepId(request.getId());
            StepLocation target = requireStep(scene.getCaseList(), ref, "TREE_SOURCE_NOT_FOUND");
            applyStepEdit(target.step(), request);
            // 序号以步骤在列表中的位置为准，移动后由统一 normalize 保证连续。
            reorderStep(target.caseDO(), target.step(), request.getOrder());
            return result(true, stepRef(target.step().getPid(), target.step().getId()));
        });
    }

    private void applyStepEdit(StepDO target, StepDO request) {
        StepDO editRequest = reverseLegacyStepForExplicitEdit(request);
        StepDO assembledRequest = operationStepAssembler.assembleManualStep(editRequest);
        boolean maskedValue = isMasked(target.getConfigList());
        preserveOriginalRecordingConfigs(target);
        String existingOperationValue = target.getOperationValue();
        target.setName(assembledRequest.getName());
        target.setRemark(assembledRequest.getRemark());
        if (assembledRequest.getOperationType() != null) {
            target.setOperationType(assembledRequest.getOperationType());
        }
        if (assembledRequest.getOperationName() != null) {
            target.setOperationName(assembledRequest.getOperationName());
        }
        // 展示接口返回的是掩码，编辑或复制时必须保留数据库中的真实操作值。
        target.setOperationValue(maskedValue ? existingOperationValue : assembledRequest.getOperationValue());
        target.setStatus(assembledRequest.getStatus());
        boolean replaceCanonicalStep = hasConfig(assembledRequest, "method_code") || "admin-manual"
            .equals(configValue(assembledRequest, "source")) || isInfrastructureStep(assembledRequest);
        target.setConfigList(mergeProtectedConfigs(target.getConfigList(), assembledRequest
            .getConfigList(), replaceCanonicalStep));
    }

    private void preserveOriginalRecordingConfigs(StepDO target) {
        if (target == null || target.getConfigList() == null) {
            return;
        }
        if (!"sakura-playwright".equalsIgnoreCase(configValue(target, "source"))) {
            return;
        }
        preserveOriginalConfig(target, "playwright_step");
        preserveOriginalConfig(target, "locator_meta");
    }

    private void preserveOriginalConfig(StepDO target, String name) {
        String originalName = "original_" + name;
        String value = configValue(target, name);
        if (configValue(target, originalName) == null && value != null && !value.isBlank()) {
            putConfig(target.getConfigList(), originalName, value);
        }
    }

    @Override
    public AutomationUiTreeMutationResp deleteLegacyCase(Long sceneDbId, CaseDO request) {
        AutomationUiTreeDeleteReq req = new AutomationUiTreeDeleteReq();
        req.setExpectedDefinitionVersion(request.getExpectedDefinitionVersion());
        List<AutomationUiTreeNodeRefReq> nodes = new ArrayList<>();
        for (String id : request.getId().split(",")) {
            if (!id.isBlank())
                nodes.add(caseNode(id.trim()));
        }
        req.setNodes(nodes);
        return delete(sceneDbId, req);
    }

    @Override
    public AutomationUiTreeMutationResp deleteLegacyStep(Long sceneDbId, StepDO request) {
        AutomationUiTreeDeleteReq req = new AutomationUiTreeDeleteReq();
        req.setExpectedDefinitionVersion(request.getExpectedDefinitionVersion());
        List<AutomationUiTreeNodeRefReq> nodes = new ArrayList<>();
        for (String id : request.getId().split(",")) {
            if (!id.isBlank())
                nodes.add(stepNode(request.getPid(), id.trim()));
        }
        req.setNodes(nodes);
        return delete(sceneDbId, req);
    }

    @Override
    public AutomationUiTreeMutationResp moveLegacyCase(Long sceneDbId, CaseDO request) {
        if (request.getDragNode() == null || request.getDropNode() == null)
            throw new BusinessException("dragNode 或 dropNode 不能为空");
        AutomationUiTreeMoveReq req = new AutomationUiTreeMoveReq();
        req.setExpectedDefinitionVersion(request.getExpectedDefinitionVersion());
        req.setSource(caseNode(request.getDragNode().getId()));
        req.setTarget(caseNode(request.getDropNode().getId()));
        req.setPosition(legacyPosition(request.getDropPosition()));
        return move(sceneDbId, req);
    }

    @Override
    public AutomationUiTreeMutationResp moveLegacyStep(Long sceneDbId, StepDO request) {
        if (request.getDragNode() == null || request.getDropNode() == null)
            throw new BusinessException("dragNode 或 dropNode 不能为空");
        AutomationUiTreeMoveReq req = new AutomationUiTreeMoveReq();
        req.setExpectedDefinitionVersion(request.getExpectedDefinitionVersion());
        req.setSource(stepNode(request.getDragNode().getPid(), request.getDragNode().getId()));
        String targetType = request.getDropNode().getType();
        if ("case".equalsIgnoreCase(targetType)) {
            req.setTarget(caseNode(request.getDropNode().getId()));
            req.setPosition(AutomationUiTreeMovePosition.INSIDE_LAST);
        } else {
            req.setTarget(stepNode(request.getDropNode().getPid(), request.getDropNode().getId()));
            req.setPosition(legacyPosition(request.getDropPosition()));
        }
        return move(sceneDbId, req);
    }

    private AutomationUiTreeNodeRefReq caseNode(String caseId) {
        AutomationUiTreeNodeRefReq ref = new AutomationUiTreeNodeRefReq();
        ref.setType(AutomationUiTreeNodeType.CASE);
        ref.setCaseId(caseId);
        return ref;
    }

    private AutomationUiTreeNodeRefReq stepNode(String caseId, String stepId) {
        AutomationUiTreeNodeRefReq ref = new AutomationUiTreeNodeRefReq();
        ref.setType(AutomationUiTreeNodeType.STEP);
        ref.setCaseId(caseId);
        ref.setStepId(stepId);
        return ref;
    }

    private AutomationUiTreeMovePosition legacyPosition(Integer dropPosition) {
        return Objects.equals(dropPosition, 1)
            ? AutomationUiTreeMovePosition.AFTER
            : AutomationUiTreeMovePosition.BEFORE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp copy(Long sceneDbId, AutomationUiTreeCopyReq req) {
        return mutate(sceneDbId, req.getExpectedDefinitionVersion(), scene -> {
            requireRef(req.getSource());
            List<CaseDO> cases = scene.getCaseList();
            if (req.getSource().getType() == AutomationUiTreeNodeType.CASE) {
                CaseDO source = requireCase(cases, req.getSource().getCaseId(), "TREE_SOURCE_NOT_FOUND");
                requireCopyCasePosition(req);
                CaseDO copy = copyCase(source);
                copy.setId(nextId(scene.getId(), CASE_SEQUENCE_SCOPE, caseIds(cases), prefixOf(source
                    .getId(), DEFAULT_CASE_PREFIX)));
                if (hasText(req.getName()))
                    copy.setName(req.getName());
                if (req.getRemark() != null)
                    copy.setRemark(req.getRemark());
                rewriteStepParents(copy);
                int index = copyCaseIndex(cases, req.getPosition(), req.getAnchor());
                cases.add(index, copy);
                return result(true, caseRef(copy.getId()));
            }
            StepLocation source = requireStep(cases, req.getSource(), "TREE_SOURCE_NOT_FOUND");
            requireCopyStepPosition(req);
            CaseDO targetCase = copyTargetCase(cases, req.getPosition(), req.getAnchor());
            StepDO copy = copyStep(source.step());
            copy.setId(nextId(scene.getId(), stepSequenceScope(targetCase.getId()), stepIds(targetCase), prefixOf(source
                .step()
                .getId(), DEFAULT_STEP_PREFIX)));
            copy.setPid(targetCase.getId());
            if (hasText(req.getName()))
                copy.setName(req.getName());
            if (req.getRemark() != null)
                copy.setRemark(req.getRemark());
            applyStepCopyOverrides(copy, req.getStep());
            Integer requestedOrder = req.getStep() == null ? null : req.getStep().getOrder();
            int index = requestedOrder == null
                ? copyStepIndex(targetCase, req.getPosition(), req.getAnchor())
                : insertionIndex(requestedOrder, targetCase.getStepList().size(), "步骤");
            targetCase.getStepList().add(index, copy);
            return result(true, stepRef(targetCase.getId(), copy.getId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp move(Long sceneDbId, AutomationUiTreeMoveReq req) {
        return mutate(sceneDbId, req.getExpectedDefinitionVersion(), scene -> {
            requireRef(req.getSource());
            requireRef(req.getTarget());
            List<CaseDO> cases = scene.getCaseList();
            if (req.getSource().getType() == AutomationUiTreeNodeType.CASE) {
                if (req.getTarget().getType() != AutomationUiTreeNodeType.CASE || (req
                    .getPosition() != AutomationUiTreeMovePosition.BEFORE && req
                        .getPosition() != AutomationUiTreeMovePosition.AFTER)) {
                    throw error("TREE_DROP_NOT_ALLOWED");
                }
                CaseDO source = requireCase(cases, req.getSource().getCaseId(), "TREE_SOURCE_NOT_FOUND");
                CaseDO target = requireCase(cases, req.getTarget().getCaseId(), "TREE_TARGET_NOT_FOUND");
                if (source == target)
                    throw error("TREE_DROP_NOT_ALLOWED");
                int oldIndex = cases.indexOf(source);
                cases.remove(source);
                int targetIndex = cases.indexOf(target) + (req.getPosition() == AutomationUiTreeMovePosition.AFTER
                    ? 1
                    : 0);
                cases.add(targetIndex, source);
                return result(oldIndex != targetIndex, caseRef(source.getId()));
            }
            StepLocation source = requireStep(cases, req.getSource(), "TREE_SOURCE_NOT_FOUND");
            if (req.getTarget().getType() == AutomationUiTreeNodeType.CASE) {
                if (req.getPosition() != AutomationUiTreeMovePosition.INSIDE_LAST)
                    throw error("TREE_DROP_NOT_ALLOWED");
                CaseDO targetCase = requireCase(cases, req.getTarget().getCaseId(), "TREE_TARGET_NOT_FOUND");
                if (source.caseDO() == targetCase && source.index() == targetCase.getStepList().size() - 1) {
                    return result(false, stepRef(targetCase.getId(), source.step().getId()));
                }
                source.caseDO().getStepList().remove(source.step());
                moveStepIdIfConflicting(scene.getId(), source.step(), targetCase);
                source.step().setPid(targetCase.getId());
                targetCase.getStepList().add(source.step());
                return result(true, stepRef(targetCase.getId(), source.step().getId()));
            }
            if (req.getTarget().getType() != AutomationUiTreeNodeType.STEP || (req
                .getPosition() != AutomationUiTreeMovePosition.BEFORE && req
                    .getPosition() != AutomationUiTreeMovePosition.AFTER)) {
                throw error("TREE_DROP_NOT_ALLOWED");
            }
            StepLocation target = requireStep(cases, req.getTarget(), "TREE_TARGET_NOT_FOUND");
            if (source.step() == target.step())
                throw error("TREE_DROP_NOT_ALLOWED");
            boolean sameParent = source.caseDO() == target.caseDO();
            int oldIndex = source.index();
            source.caseDO().getStepList().remove(source.step());
            moveStepIdIfConflicting(scene.getId(), source.step(), target.caseDO());
            source.step().setPid(target.caseDO().getId());
            int index = target.caseDO().getStepList().indexOf(target.step()) + (req
                .getPosition() == AutomationUiTreeMovePosition.AFTER ? 1 : 0);
            target.caseDO().getStepList().add(index, source.step());
            return result(!sameParent || oldIndex != index, stepRef(target.caseDO().getId(), source.step().getId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiTreeMutationResp delete(Long sceneDbId, AutomationUiTreeDeleteReq req) {
        return mutate(sceneDbId, req.getExpectedDefinitionVersion(), scene -> {
            List<CaseDO> cases = scene.getCaseList();
            List<AutomationUiTreeNodeRefReq> nodes = normalizeDelete(req.getNodes());
            if (nodes.isEmpty())
                throw error("TREE_DELETE_TARGET_EMPTY");
            // 先完整定位，确保错误请求不会产生部分删除。
            for (AutomationUiTreeNodeRefReq node : nodes) {
                requireRef(node);
                if (node.getType() == AutomationUiTreeNodeType.CASE)
                    requireCase(cases, node.getCaseId(), "TREE_SOURCE_NOT_FOUND");
                else
                    requireStep(cases, node, "TREE_SOURCE_NOT_FOUND");
            }
            AutomationUiTreeNodeRefResp selected = null;
            for (AutomationUiTreeNodeRefReq node : nodes) {
                if (node.getType() == AutomationUiTreeNodeType.CASE) {
                    cases.removeIf(item -> Objects.equals(item.getId(), node.getCaseId()));
                } else {
                    CaseDO parent = requireCase(cases, node.getCaseId(), "TREE_SOURCE_NOT_FOUND");
                    parent.getStepList().removeIf(step -> Objects.equals(step.getId(), node.getStepId()));
                    selected = caseRef(parent.getId());
                }
            }
            if (selected == null && !cases.isEmpty())
                selected = caseRef(cases.get(Math.max(0, cases.size() - 1)).getId());
            return result(true, selected);
        });
    }

    private AutomationUiTreeMutationResp mutate(Long id, Long expectedVersion, Mutation mutation) {
        return mutate(id, expectedVersion, false, mutation);
    }

    private AutomationUiTreeMutationResp mutate(Long id,
                                                Long expectedVersion,
                                                boolean initializeEmptyDefinition,
                                                Mutation mutation) {
        // 自定义 FOR UPDATE 查询只负责加锁；JSON 字段需由 MyBatis-Plus 的实体映射读取，
        // 否则 JacksonTypeHandler 不会还原 caseList，合法移动会被误判为空数据。
        if (sceneMapper.selectByIdForUpdate(id) == null)
            throw error("SCENE_NOT_FOUND");
        AutomationUiSceneDO scene = sceneMapper.selectById(id);
        if (!Objects.equals(normalizeVersion(scene.getDefinitionVersion()), expectedVersion))
            throw error("TREE_CONCURRENT_MODIFICATION");
        assertNoActiveRunner(scene);
        if (scene.getCaseList() == null) {
            // 历史空场景只在用户明确新增首个用例时初始化，其他树操作仍拒绝损坏定义。
            if (!initializeEmptyDefinition)
                throw error("场景用例树数据异常：用例列表不能为空");
            scene.setCaseList(new ArrayList<>());
        }
        scene.setCaseList(copyCases(scene.getCaseList()));
        validate(scene.getCaseList());
        // 删除前先记录当前最大后缀，保证删除最大编号后也不会复用旧业务 ID。
        syncNodeIdSequences(scene);
        AutomationUiTreeMutationResp response = mutation.apply(scene);
        if (!response.isChanged()) {
            response.setDefinitionVersion(normalizeVersion(scene.getDefinitionVersion()));
            return response;
        }
        normalize(scene.getCaseList());
        validate(scene.getCaseList());
        syncNodeIdSequences(scene);
        int updated = sceneMapper.updateDefinition(id, expectedVersion, scene.getCaseList(), scene.getCaseList()
            .size(), stepTotal(scene.getCaseList()));
        if (updated != 1)
            throw error("TREE_CONCURRENT_MODIFICATION");
        response.setDefinitionVersion(expectedVersion + 1);
        return response;
    }

    private void assertNoActiveRunner(AutomationUiSceneDO scene) {
        if (AutomationUiSceneStatusCodes.STATUS_RUNNING.equals(AutomationUiSceneStatusCodes.normalizeStatus(scene
            .getExecuteStatus()))) {
            throw error("TREE_SCENE_EXECUTING");
        }
        long active = playwrightJobMapper.countActiveBySceneKeys(String.valueOf(scene.getId()), scene.getSceneId());
        if (active > 0)
            throw error("TREE_SCENE_EXECUTING");
    }

    private void validate(List<CaseDO> cases) {
        if (cases == null)
            throw error("场景用例树数据异常：用例列表不能为空");
        Set<String> caseIds = new HashSet<>();
        for (int i = 0; i < cases.size(); i++) {
            CaseDO item = cases.get(i);
            if (item == null || !hasText(item.getId()) || !caseIds.add(item.getId()))
                throw error("场景用例树数据异常：用例[" + i + "]无效或 ID 重复");
            if (item.getOrder() == null || item.getOrder() != i + 1)
                throw error("场景用例树数据异常：用例[" + i + "]顺序不连续");
            if (item.getStepList() == null)
                throw error("场景用例树数据异常：用例[" + i + "]的步骤列表不能为空");
            Set<String> stepIds = new HashSet<>();
            for (int j = 0; j < item.getStepList().size(); j++) {
                StepDO step = item.getStepList().get(j);
                if (step == null || !hasText(step.getId()) || !stepIds.add(step.getId()) || !Objects.equals(item
                    .getId(), step.getPid())) {
                    throw error("场景用例树数据异常：用例[" + i + "]的步骤[" + j + "]无效、ID 重复或父用例不匹配");
                }
                if (step.getOrder() == null || step.getOrder() != j + 1)
                    throw error("场景用例树数据异常：用例[" + i + "]的步骤[" + j + "]顺序不连续");
            }
        }
    }

    private void normalize(List<CaseDO> cases) {
        for (int i = 0; i < cases.size(); i++) {
            CaseDO item = cases.get(i);
            item.setOrder(i + 1);
            for (int j = 0; j < item.getStepList().size(); j++) {
                StepDO step = item.getStepList().get(j);
                step.setOrder(j + 1);
                step.setPid(item.getId());
            }
        }
    }

    private int insertionIndex(Integer requestedOrder, int currentSize, String nodeLabel) {
        if (requestedOrder == null)
            return currentSize;
        int maxOrder = currentSize + 1;
        if (requestedOrder < 1 || requestedOrder > maxOrder)
            throw error(nodeLabel + "序号必须在 1 到 " + maxOrder + " 之间");
        return requestedOrder - 1;
    }

    private void reorderStep(CaseDO parent, StepDO step, Integer requestedOrder) {
        if (requestedOrder == null) {
            return;
        }
        int targetIndex = insertionIndex(requestedOrder, parent.getStepList().size() - 1, "步骤");
        int currentIndex = parent.getStepList().indexOf(step);
        if (currentIndex == targetIndex) {
            return;
        }
        parent.getStepList().remove(currentIndex);
        parent.getStepList().add(targetIndex, step);
    }

    private List<AutomationUiTreeNodeRefReq> normalizeDelete(List<AutomationUiTreeNodeRefReq> requested) {
        List<AutomationUiTreeNodeRefReq> result = new ArrayList<>();
        Set<String> cases = new HashSet<>();
        Set<String> steps = new HashSet<>();
        for (AutomationUiTreeNodeRefReq node : requested) {
            requireRef(node);
            if (node.getType() == AutomationUiTreeNodeType.CASE)
                cases.add(node.getCaseId());
        }
        for (AutomationUiTreeNodeRefReq node : requested) {
            String key = node.getType() + ":" + node.getCaseId() + ":" + node.getStepId();
            if (node.getType() == AutomationUiTreeNodeType.CASE && cases.contains(node.getCaseId()) && steps.add(key))
                result.add(node);
            if (node.getType() == AutomationUiTreeNodeType.STEP && !cases.contains(node.getCaseId()) && steps.add(key))
                result.add(node);
        }
        return result;
    }

    private void requireRef(AutomationUiTreeNodeRefReq ref) {
        if (ref == null || ref.getType() == null || !hasText(ref.getCaseId()) || (ref
            .getType() == AutomationUiTreeNodeType.CASE && hasText(ref.getStepId())) || (ref
                .getType() == AutomationUiTreeNodeType.STEP && !hasText(ref.getStepId())))
            throw error("场景用例树数据异常：节点引用不合法");
    }

    private CaseDO requireCase(List<CaseDO> cases, String id, String code) {
        return cases.stream()
            .filter(item -> Objects.equals(item.getId(), id))
            .findFirst()
            .orElseThrow(() -> error(code));
    }

    private StepLocation requireStep(List<CaseDO> cases, AutomationUiTreeNodeRefReq ref, String code) {
        if (ref.getType() != AutomationUiTreeNodeType.STEP)
            throw error(code);
        CaseDO parent = requireCase(cases, ref.getCaseId(), code);
        for (int i = 0; i < parent.getStepList().size(); i++)
            if (Objects.equals(parent.getStepList().get(i).getId(), ref.getStepId()))
                return new StepLocation(parent, parent.getStepList().get(i), i);
        throw error(code);
    }

    private void requireCopyCasePosition(AutomationUiTreeCopyReq req) {
        if (req.getPosition() != AutomationUiTreeMovePosition.BEFORE && req
            .getPosition() != AutomationUiTreeMovePosition.AFTER && req
                .getPosition() != AutomationUiTreeMovePosition.LAST)
            throw error("TREE_DROP_NOT_ALLOWED");
    }

    private void requireCopyStepPosition(AutomationUiTreeCopyReq req) {
        if (req.getPosition() != AutomationUiTreeMovePosition.BEFORE && req
            .getPosition() != AutomationUiTreeMovePosition.AFTER && req
                .getPosition() != AutomationUiTreeMovePosition.INSIDE_LAST)
            throw error("TREE_DROP_NOT_ALLOWED");
    }

    private int copyCaseIndex(List<CaseDO> cases,
                              AutomationUiTreeMovePosition position,
                              AutomationUiTreeNodeRefReq anchor) {
        if (position == AutomationUiTreeMovePosition.LAST)
            return cases.size();
        requireRef(anchor);
        if (anchor.getType() != AutomationUiTreeNodeType.CASE)
            throw error("TREE_DROP_NOT_ALLOWED");
        int index = cases.indexOf(requireCase(cases, anchor.getCaseId(), "TREE_TARGET_NOT_FOUND"));
        return position == AutomationUiTreeMovePosition.AFTER ? index + 1 : index;
    }

    private CaseDO copyTargetCase(List<CaseDO> cases,
                                  AutomationUiTreeMovePosition position,
                                  AutomationUiTreeNodeRefReq anchor) {
        if (position == AutomationUiTreeMovePosition.INSIDE_LAST) {
            requireRef(anchor);
            if (anchor.getType() != AutomationUiTreeNodeType.CASE)
                throw error("TREE_DROP_NOT_ALLOWED");
            return requireCase(cases, anchor.getCaseId(), "TREE_TARGET_NOT_FOUND");
        }
        requireRef(anchor);
        return requireCase(cases, anchor.getCaseId(), "TREE_TARGET_NOT_FOUND");
    }

    private int copyStepIndex(CaseDO target, AutomationUiTreeMovePosition position, AutomationUiTreeNodeRefReq anchor) {
        if (position == AutomationUiTreeMovePosition.INSIDE_LAST)
            return target.getStepList().size();
        if (anchor.getType() != AutomationUiTreeNodeType.STEP || !Objects.equals(anchor.getCaseId(), target.getId()))
            throw error("TREE_DROP_NOT_ALLOWED");
        int index = requireStep(List.of(target), anchor, "TREE_TARGET_NOT_FOUND").index();
        return position == AutomationUiTreeMovePosition.AFTER ? index + 1 : index;
    }

    private void moveStepIdIfConflicting(Long sceneId, StepDO step, CaseDO target) {
        if (target.getStepList().stream().anyMatch(item -> Objects.equals(item.getId(), step.getId())))
            step.setId(nextId(sceneId, stepSequenceScope(target.getId()), stepIds(target), prefixOf(step
                .getId(), DEFAULT_STEP_PREFIX)));
    }

    private List<CaseDO> copyCases(List<CaseDO> source) {
        List<CaseDO> copies = new ArrayList<>();
        if (source != null)
            for (CaseDO item : source)
                copies.add(copyCase(item));
        return copies;
    }

    private CaseDO copyCase(CaseDO source) {
        if (source == null)
            return null;
        CaseDO copy = new CaseDO();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setRemark(source.getRemark());
        copy.setCancel(source.getCancel());
        copy.setType(source.getType());
        copy.setOrder(source.getOrder());
        copy.setStatus(source.getStatus());
        copy.setExecutionConfig(copyExecutionConfig(source.getExecutionConfig()));
        copy.setOrigin(copyOrigin(source.getOrigin()));
        List<StepDO> steps = new ArrayList<>();
        if (source.getStepList() != null)
            for (StepDO step : source.getStepList())
                steps.add(copyStep(step));
        copy.setStepList(steps);
        return copy;
    }

    private CaseExecutionConfigDO copyExecutionConfig(CaseExecutionConfigDO source) {
        if (source == null) {
            return null;
        }
        CaseExecutionConfigDO copy = new CaseExecutionConfigDO();
        copy.setStartUrl(source.getStartUrl());
        copy.setWindowSizeMode(source.getWindowSizeMode());
        copy.setViewportWidth(source.getViewportWidth());
        copy.setViewportHeight(source.getViewportHeight());
        copy.setScreenshotMode(source.getScreenshotMode());
        copy.setPageErrorCheckEnabled(source.getPageErrorCheckEnabled());
        return copy;
    }

    private CaseOriginDO copyOrigin(CaseOriginDO source) {
        if (source == null) {
            return null;
        }
        CaseOriginDO copy = new CaseOriginDO();
        copy.setCreationSource(source.getCreationSource());
        copy.setOriginalCaseId(source.getOriginalCaseId());
        copy.setInitialRecordingId(source.getInitialRecordingId());
        copy.setCopiedFromCaseId(source.getCopiedFromCaseId());
        return copy;
    }

    private StepDO copyStep(StepDO source) {
        if (source == null)
            return null;
        StepDO copy = new StepDO();
        copy.setPid(source.getPid());
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setRemark(source.getRemark());
        copy.setType(source.getType());
        copy.setOperationType(source.getOperationType());
        copy.setOperationName(source.getOperationName());
        copy.setOperationValue(source.getOperationValue());
        copy.setSetting(source.getSetting());
        copy.setOrder(source.getOrder());
        copy.setStatus(source.getStatus());
        List<StepDO.Config> configs = new ArrayList<>();
        if (source.getConfigList() != null)
            for (StepDO.Config config : source.getConfigList()) {
                StepDO.Config item = new StepDO.Config();
                item.setParamsName(config.getParamsName());
                item.setParamsValue(config.getParamsValue());
                configs.add(item);
            }
        copy.setConfigList(configs);
        return copy;
    }

    private void applyStepCopyOverrides(StepDO target, AutomationUiStepCopyReq request) {
        if (request == null) {
            return;
        }
        StepDO editRequest = copyStep(target);
        if (request.getName() != null) {
            editRequest.setName(request.getName());
        }
        if (request.getRemark() != null) {
            editRequest.setRemark(request.getRemark());
        }
        if (request.getStatus() != null) {
            editRequest.setStatus(request.getStatus());
        }
        if (request.getOperationType() != null) {
            editRequest.setOperationType(request.getOperationType());
        }
        if (request.getOperationName() != null) {
            editRequest.setOperationName(request.getOperationName());
        }
        if (request.getOperationValue() != null) {
            editRequest.setOperationValue(request.getOperationValue());
        }
        if (request.getConfigList() != null) {
            List<StepDO.Config> configs = new ArrayList<>();
            for (AutomationUiStepConfigEditReq source : request.getConfigList()) {
                if (source == null || source.getParamsName() == null || source.getParamsName().isBlank()) {
                    continue;
                }
                StepDO.Config config = new StepDO.Config();
                config.setParamsName(source.getParamsName());
                config.setParamsValue(source.getParamsValue());
                configs.add(config);
            }
            editRequest.setConfigList(configs);
        }
        // 复制覆盖与修改步骤共用保护规则，原始 Playwright step、定位元数据和掩码值不能丢失。
        applyStepEdit(target, editRequest);
    }

    /**
     * 仅在用户明确保存编辑时把旧 operationValue/raw step 投影为目录配置；读取场景不会隐式回写。
     * 原始录制 step 另存为 original_playwright_step，避免正向组装后丢失执行事实。
     */
    private StepDO reverseLegacyStepForExplicitEdit(StepDO request) {
        StepDO copy = copyStep(request);
        if (copy == null || hasConfig(copy, "method_code")) {
            return copy;
        }
        AutomationOperationStepReverseAdapter.ReverseResult result = operationStepReverseAdapter.adapt(copy);
        if (result == null || !result.recognized()) {
            return copy;
        }
        List<StepDO.Config> configs = copy.getConfigList() == null ? new ArrayList<>() : copy.getConfigList();
        putConfig(configs, "method_code", result.methodCode());
        if (result.methodVersion() != null) {
            putConfig(configs, "method_version", String.valueOf(result.methodVersion()));
        }
        try {
            putConfig(configs, "method_config", objectMapper.writeValueAsString(result.methodConfig()));
        } catch (Exception e) {
            throw new BusinessException("METHOD_CONFIG_INVALID：历史步骤配置无法序列化");
        }
        String raw = configValue(copy, "playwright_step");
        if (raw != null && !raw.isBlank() && configValue(copy, "original_playwright_step") == null) {
            putConfig(configs, "original_playwright_step", raw);
        }
        copy.setConfigList(configs);
        return copy;
    }

    private void putConfig(List<StepDO.Config> configs, String name, String value) {
        configs.removeIf(config -> config != null && Objects.equals(name, config.getParamsName()));
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        configs.add(config);
    }

    /** 普通编辑表单可能只回传掩码值；保留来源、敏感值和初始录制快照，避免执行事实不可追溯。 */
    private List<StepDO.Config> mergeProtectedConfigs(List<StepDO.Config> existing,
                                                      List<StepDO.Config> requested,
                                                      boolean replaceCanonicalStep) {
        if (existing == null || existing.isEmpty())
            return requested == null ? new ArrayList<>() : requested;
        boolean maskedValue = isMasked(existing);
        List<StepDO.Config> merged = new ArrayList<>();
        if (requested != null)
            for (StepDO.Config config : requested) {
                StepDO.Config item = new StepDO.Config();
                item.setParamsName(config.getParamsName());
                item.setParamsValue(config.getParamsValue());
                merged.add(item);
            }
        for (StepDO.Config config : existing)
            if (config != null && isProtectedRecordingConfig(config
                .getParamsName(), maskedValue, replaceCanonicalStep)) {
                merged.removeIf(item -> Objects.equals(item.getParamsName(), config.getParamsName()));
                StepDO.Config item = new StepDO.Config();
                item.setParamsName(config.getParamsName());
                item.setParamsValue(config.getParamsValue());
                merged.add(item);
            }
        return merged;
    }

    private boolean isMasked(List<StepDO.Config> configs) {
        return configs != null && configs.stream()
            .anyMatch(config -> config != null && "value_masked".equals(config.getParamsName()) && "1".equals(config
                .getParamsValue()));
    }

    private boolean isInfrastructureStep(StepDO step) {
        if (step == null) {
            return false;
        }
        if (isInfrastructureAction(step.getOperationValue())) {
            return true;
        }
        List<StepDO.Config> configs = step.getConfigList();
        return configs != null && configs.stream()
            .anyMatch(config -> config != null && "action_type".equals(config
                .getParamsName()) && isInfrastructureAction(config.getParamsValue()));
    }

    private boolean isInfrastructureAction(String value) {
        return value != null && INFRASTRUCTURE_ACTION_TYPES.contains(value.trim().toLowerCase());
    }

    private boolean isProtectedRecordingConfig(String name, boolean maskedValue, boolean replaceCanonicalStep) {
        if (name == null)
            return false;
        // 手工目录和基础设施步骤由后端重新生成；录制步骤则必须保持原始执行事实。
        if (replaceCanonicalStep && "playwright_step".equals(name)) {
            return false;
        }
        return IMMUTABLE_RECORDING_CONFIGS.contains(name) || name.startsWith("original_") || name
            .startsWith("screenshot_") || maskedValue && ("value".equals(name) || "operationValue".equals(name));
    }

    private boolean hasConfig(StepDO step, String name) {
        return configValue(step, name) != null;
    }

    private String configValue(StepDO step, String name) {
        if (step == null || step.getConfigList() == null) {
            return null;
        }
        return step.getConfigList()
            .stream()
            .filter(config -> config != null && Objects.equals(name, config.getParamsName()))
            .map(StepDO.Config::getParamsValue)
            .findFirst()
            .orElse(null);
    }

    private void assembleManualSteps(CaseDO caseDO) {
        if (caseDO.getStepList() == null || caseDO.getStepList().isEmpty()) {
            return;
        }
        List<StepDO> assembled = new ArrayList<>();
        for (StepDO step : caseDO.getStepList()) {
            assembled.add(operationStepAssembler.assembleManualStep(step));
        }
        caseDO.setStepList(assembled);
    }

    private void allocateInitialStepIds(Long sceneId, CaseDO caseDO) {
        if (caseDO.getStepList() == null || caseDO.getStepList().isEmpty()) {
            return;
        }
        Set<String> usedIds = new HashSet<>();
        for (StepDO step : caseDO.getStepList()) {
            String requestedId = step.getId() == null ? "" : step.getId().trim();
            if (requestedId.isEmpty() || requestedId.endsWith("_")) {
                String prefix = requestedId.isEmpty() ? DEFAULT_STEP_PREFIX : requestedId;
                step.setId(nextId(sceneId, stepSequenceScope(caseDO.getId()), usedIds, prefix));
            } else if (!usedIds.add(requestedId)) {
                throw error("TREE_NODE_CONFLICT");
            }
            usedIds.add(step.getId());
        }
    }

    private void rewriteStepParents(CaseDO caseDO) {
        for (StepDO step : caseDO.getStepList())
            step.setPid(caseDO.getId());
    }

    private Set<String> caseIds(List<CaseDO> cases) {
        Set<String> ids = new HashSet<>();
        for (CaseDO item : cases)
            ids.add(item.getId());
        return ids;
    }

    private Set<String> stepIds(CaseDO caseDO) {
        Set<String> ids = new HashSet<>();
        for (StepDO item : caseDO.getStepList())
            ids.add(item.getId());
        return ids;
    }

    private String nextId(Long sceneId, String scopeKey, Set<String> ids, String prefix) {
        long max = 0;
        for (String id : ids) {
            if (id != null && id.startsWith(prefix)) {
                String suffix = id.substring(prefix.length());
                if (suffix.matches("\\d+"))
                    max = Math.max(max, Long.parseLong(suffix));
            }
        }
        Long stored = sceneMapper.selectNodeIdSequence(sceneId, scopeKey, prefix);
        max = Math.max(max, stored == null ? 0L : stored);
        do {
            max++;
        } while (ids.contains(prefix + String.format("%03d", max)));
        sceneMapper.upsertNodeIdSequence(sceneId, scopeKey, prefix, max);
        return prefix + String.format("%03d", max);
    }

    private void syncNodeIdSequences(AutomationUiSceneDO scene) {
        for (CaseDO caseDO : scene.getCaseList()) {
            syncNodeIdSequence(scene.getId(), CASE_SEQUENCE_SCOPE, caseDO.getId());
            for (StepDO step : caseDO.getStepList())
                syncNodeIdSequence(scene.getId(), stepSequenceScope(caseDO.getId()), step.getId());
        }
    }

    private void syncNodeIdSequence(Long sceneId, String scopeKey, String id) {
        if (!hasText(id))
            return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*?)(\\d+)$").matcher(id);
        if (!matcher.matches())
            return;
        sceneMapper.upsertNodeIdSequence(sceneId, scopeKey, matcher.group(1), Long.parseLong(matcher.group(2)));
    }

    private String stepSequenceScope(String caseId) {
        return "STEP:" + caseId;
    }

    private String prefixOf(String id, String fallback) {
        if (!hasText(id))
            return fallback;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*?)(\\d+)$").matcher(id);
        return matcher.matches() ? matcher.group(1) : fallback;
    }

    private int stepTotal(List<CaseDO> cases) {
        return cases.stream().mapToInt(item -> item.getStepList().size()).sum();
    }

    private Long normalizeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AutomationUiTreeMutationResp result(boolean changed, AutomationUiTreeNodeRefResp selected) {
        AutomationUiTreeMutationResp response = new AutomationUiTreeMutationResp();
        response.setChanged(changed);
        response.setSelectedNode(selected);
        return response;
    }

    private AutomationUiTreeNodeRefResp caseRef(String caseId) {
        return new AutomationUiTreeNodeRefResp(AutomationUiTreeNodeType.CASE, caseId, null);
    }

    private AutomationUiTreeNodeRefResp stepRef(String caseId, String stepId) {
        return new AutomationUiTreeNodeRefResp(AutomationUiTreeNodeType.STEP, caseId, stepId);
    }

    private BusinessException error(String code) {
        return new BusinessException(ERROR_MESSAGES.getOrDefault(code, code));
    }

    @FunctionalInterface
    private interface Mutation { AutomationUiTreeMutationResp apply(AutomationUiSceneDO scene); }

    private record StepLocation(CaseDO caseDO, StepDO step, int index) {}
}
