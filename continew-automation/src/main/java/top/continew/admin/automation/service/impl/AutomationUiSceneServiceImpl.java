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
import java.util.Iterator;
import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.RequiredArgsConstructor;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.common.regex.RegexUtil;
import top.continew.admin.common.sort.DragSortUtil;
import top.continew.admin.common.util.StringUtils;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiSceneReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneResp;
import top.continew.admin.automation.service.AutomationUiSceneService;

/**
 * 自动化管理-UI自动化场景业务实现
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Service
@RequiredArgsConstructor
public class AutomationUiSceneServiceImpl extends BaseServiceImpl<AutomationUiSceneMapper, AutomationUiSceneDO, AutomationUiSceneResp, AutomationUiSceneDetailResp, AutomationUiSceneQuery, AutomationUiSceneReq> implements AutomationUiSceneService {
    @Override
    public List<AutomationUiSceneDetailResp> selectByIds(List<Long> ids) {
        List<AutomationUiSceneDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), AutomationUiSceneDetailResp.class);
        list.forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        });
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    public void addCase(CaseDO caseDO, Long id) {
        String caseId = caseDO.getId();
        if (!RegexUtil.isClassMethod(caseId)) {
            throw ReflectUtil.newInstance(BusinessException.class, new Object[] {"场景用例ID不合法"});
        }
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isEmpty(caseList)) {
            caseList = new ArrayList<>();
            caseDO.setOrder(1);
            caseList.add(caseDO);
        } else {
            caseList.forEach(item -> {
                if (item.getId().equals(caseId)) {
                    throw ReflectUtil.newInstance(BusinessException.class, new Object[] {"该场景下，用例ID已存在"});
                }
            });
            caseDO.setOrder(caseList.size() + 1);
            caseList.add(caseDO);
            if (caseDO.getSortType() != null) {
                if (caseDO.getSortType() == 1) {
                    DragSortUtil.swap(caseList, caseDO.getOrder() - 1, caseDO.getItemOrder() - 1);
                } else if (caseDO.getSortType() == 2) {
                    DragSortUtil.move(caseList, caseDO.getOrder() - 1, caseDO.getItemOrder() - 1);
                }
            }
        }
        caseList.forEach(DragSortUtil.getIndex((item, index) -> {
            item.setOrder(index + 1);
            item.setId(caseId + String.format("%03d", item.getOrder()));
        }));
        automationUiSceneDO.setCaseList(caseList);
        baseMapper.updateById(automationUiSceneDO);
    }

    public void updateCase(CaseDO caseDO, Long id) {
        String caseId = caseDO.getId();
        if (!RegexUtil.isClassMethod(caseId)) {
            throw ReflectUtil.newInstance(BusinessException.class, new Object[] {"场景用例ID不合法"});
        }
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)) {
            for (CaseDO item : caseList) {
                if (item.getId().equals(caseId + String.format("%03d", caseDO.getOrder()))) {
                    item.setName(caseDO.getName());
                    item.setRemark(caseDO.getRemark());
                    item.setStatus(caseDO.getStatus());
                    break;
                }
            }
            if (caseDO.getSortType() != null) {
                if (caseDO.getSortType() == 1) {
                    DragSortUtil.swap(caseList, caseDO.getOrder() - 1, caseDO.getItemOrder() - 1);
                } else if (caseDO.getSortType() == 2) {
                    DragSortUtil.move(caseList, caseDO.getOrder() - 1, caseDO.getItemOrder() - 1);
                }
            }
            caseList.forEach(DragSortUtil.getIndex((item, index) -> {
                item.setOrder(index + 1);
                item.setId(caseId + String.format("%03d", item.getOrder()));
            }));
            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
    }

    public void deleteCase(CaseDO caseDO, Long id) {
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)) {
            String[] ids = caseDO.getId().split(",");
            String caseIdToDelete = "";
            for (String caseId : ids) {
                caseIdToDelete = caseId;
                caseList.removeIf(item -> item.getId().equals(caseId));
            }
            // 从被删除的ID中提取基础ID部分
            String baseId = caseIdToDelete.replaceAll("\\d+$", "");
            // 重新排序并设置新的ID
            caseList.forEach(DragSortUtil.getIndex((item, index) -> {
                item.setOrder(index + 1);
                item.setId(baseId + String.format("%03d", item.getOrder()));
            }));
            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void dragCase(CaseDO caseDO, Long id) {
        // 校验拖拽节点和目标节点是否存在
        if (caseDO.getDragNode() == null || caseDO.getDropNode() == null) {
            CheckUtils.throwIf(true, "dragNode 或 dropNode 不能为空");
        }
        CheckUtils.throwIf(caseDO.getDragNode().getId().equals(caseDO.getDropNode().getId()), "不能移动到自己");
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)){
            if (caseDO.getDropPosition() == -1) {
                DragSortUtil.move(caseList, caseDO.getDragNode().getOrder() - 1, caseDO.getDropNode().getOrder() - 1);
            } else if (caseDO.getDropPosition() == 0) {
                DragSortUtil.swap(caseList, caseDO.getDragNode().getOrder() - 1, caseDO.getDropNode().getOrder() - 1);
            } else if (caseDO.getDropPosition() == 1) {
                DragSortUtil.move(caseList, caseDO.getDragNode().getOrder() - 1, caseDO.getDropNode().getOrder() - 1);
            }
            String baseId = caseDO.getDragNode().getId().replaceAll("\\d+$", "");
            caseList.forEach(DragSortUtil.getIndex((item, index) -> {
                item.setOrder(index + 1);
                item.setId(baseId + String.format("%03d", item.getOrder()));
            }));
            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
    }

    public String addStep(StepDO stepDO, Long id) {
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        IdGenerator idGenerator = DefaultIdGeneratorProvider.INSTANCE.getShare();
        String stepId = String.valueOf(idGenerator.generate());
        if (StringUtils.isNotEmpty(caseList)) {
            for (CaseDO caseItem : caseList) {
                if (caseItem.getId().equals(stepDO.getPid())) {
                    List<StepDO> stepList = caseItem.getStepList();
                    if (StringUtils.isEmpty(stepList)) {
                        stepList = new ArrayList<>();
                        stepDO.setOrder(1);
                    } else {
                        stepDO.setOrder(stepList.size() + 1);
                    }
                    stepDO.setId(stepId);
                    stepList.add(stepDO);
                    if (stepDO.getSortType() != null) {
                        if (stepDO.getSortType() == 1) {
                            DragSortUtil.swap(stepList, stepDO.getOrder() - 1, stepDO.getItemOrder() - 1);
                        } else if (stepDO.getSortType() == 2) {
                            DragSortUtil.move(stepList, stepDO.getOrder() - 1, stepDO.getItemOrder() - 1);
                        }
                    }
                    stepList.forEach(DragSortUtil.getIndex((item, index) -> {
                        item.setOrder(index + 1);
                    }));
                    caseItem.setStepList(stepList);
                    break;
                }
            }
            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
        return stepId;
    }

    public void updateStep(StepDO stepDO, Long id) {
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)) {
            for (CaseDO caseItem : caseList) {
                if (caseItem.getId().equals(stepDO.getPid())) {
                    List<StepDO> stepList = caseItem.getStepList();
                    for (StepDO stepItem : stepList){
                        if (stepItem.getId().equals(stepDO.getId())) {
                            stepItem.setName(stepDO.getName());
                            stepItem.setRemark(stepDO.getRemark());
                            stepItem.setOperationType(stepDO.getOperationType());
                            stepItem.setOperationName(stepDO.getOperationName());
                            stepItem.setOperationValue(stepDO.getOperationValue());
                            stepItem.setConfigList(stepDO.getConfigList());
                            stepItem.setStatus(stepDO.getStatus());
                            break;
                        }
                    }
                    if (stepDO.getSortType() != null) {
                        if (stepDO.getSortType() == 1) {
                            DragSortUtil.swap(stepList, stepDO.getOrder() - 1, stepDO.getItemOrder() - 1);
                        } else if (stepDO.getSortType() == 2) {
                            DragSortUtil.move(stepList, stepDO.getOrder() - 1, stepDO.getItemOrder() - 1);
                        }
                    }
                    stepList.forEach(DragSortUtil.getIndex((item, index) -> {
                        item.setOrder(index + 1);
                    }));
                    caseItem.setStepList(stepList);
                    break;
                }
            }
            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
    }

    public void deleteStep(StepDO stepDO, Long id) {
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)) {
            String[] ids = stepDO.getId().split(",");
            for (String stepId : ids) {
                outerLoop:
                for (CaseDO caseItem : caseList) {
                    List<StepDO> stepList = caseItem.getStepList();
                    for (StepDO stepItem : stepList){
                        if (stepItem.getId().equals(stepId)) {
                            stepList.remove(stepItem);
                            stepList.forEach(DragSortUtil.getIndex((item, index) -> {
                                item.setOrder(index + 1);
                            }));
                            caseItem.setStepList(stepList);
                            break outerLoop;
                        }
                    }
                }
                automationUiSceneDO.setCaseList(caseList);
                baseMapper.updateById(automationUiSceneDO);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void dragStep1(StepDO stepDO, Long id) {
        // 校验拖拽节点和目标节点是否存在
        if (stepDO.getDragNode() == null || stepDO.getDropNode() == null) {
            CheckUtils.throwIf(true, "dragNode 或 dropNode 不能为空");
        }
        CheckUtils.throwIf(stepDO.getDragNode().getId().equals(stepDO.getDropNode().getId()), "不能移动到自己");
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        StepDO stepNew = new StepDO();
        if (StringUtils.isNotEmpty(caseList)) {
            for (CaseDO caseItem : caseList) {
                List<StepDO> stepList = caseItem.getStepList();
                if (caseItem.getId().equals(stepDO.getDragNode().getPid())) {
                    if (StringUtils.isNotEmpty(stepList)) {
                        if(stepDO.getDropNode().getType().equals("step")){
                            if (stepDO.getDropPosition() == -1) {
                                DragSortUtil.move(stepList, stepDO.getDragNode().getOrder() - 1, stepDO.getDropNode().getOrder() - 1);
                            } else if (stepDO.getDropPosition() == 0) {
                                DragSortUtil.swap(stepList, stepDO.getDragNode().getOrder() - 1, stepDO.getDropNode().getOrder() - 1);
                            } else if (stepDO.getDropPosition() == 1) {
                                DragSortUtil.move(stepList, stepDO.getDragNode().getOrder() - 1, stepDO.getDropNode().getOrder() - 1);
                            }
                            stepList.forEach(DragSortUtil.getIndex((item, index) -> {
                                item.setOrder(index + 1);
                            }));
                            break;
                        } else if(stepDO.getDropNode().getType().equals("case")){
                            for (StepDO stepItem : stepList){
                                if(stepItem.getId().equals(stepDO.getDragNode().getId())) {
                                    stepList.remove(stepItem);
                                    stepNew = stepItem;
                                    break;
                                }
                            }
                            stepList.forEach(DragSortUtil.getIndex((item, index) -> {
                                item.setOrder(index + 1);
                            }));
                            continue;
                        }
                    }
                }
                if (caseItem.getId().equals(stepDO.getDropNode().getId())) {
                    stepNew.setPid(stepDO.getDropNode().getId());
                    stepNew.setOrder(stepList.size() + 1);
                    stepList.add(stepNew);
                }
                caseItem.setStepList(stepList);
                break;
            }
            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
    }

    public void dragStep(StepDO stepDO, Long id) {
        // 校验拖拽节点和目标节点是否存在
        if (stepDO.getDragNode() == null || stepDO.getDropNode() == null) {
            CheckUtils.throwIf(true, "dragNode 或 dropNode 不能为空");
        }
        CheckUtils.throwIf(stepDO.getDragNode().getId().equals(stepDO.getDropNode().getId()), "不能移动到自己");
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        // 添加空值检查
        if (automationUiSceneDO == null) {
            return;
        }
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)) {
            StepDO stepNew = null;
            CaseDO sourceCase = null;
            CaseDO targetCase = null;

            // 先找到源用例和目标用例
            for (CaseDO caseItem : caseList) {
                if (caseItem.getId().equals(stepDO.getDragNode().getPid())) {
                    sourceCase = caseItem;
                }
                if (caseItem.getId().equals(stepDO.getDropNode().getId())) {
                    targetCase = caseItem;
                }
            }

            // 处理步骤移动
            if (sourceCase != null) {
                List<StepDO> sourceStepList = sourceCase.getStepList();
                if (StringUtils.isNotEmpty(sourceStepList)) {
                    // 使用Iterator避免ConcurrentModificationException
                    Iterator<StepDO> iterator = sourceStepList.iterator();
                    while (iterator.hasNext()) {
                        StepDO stepItem = iterator.next();
                        if (stepItem.getId().equals(stepDO.getDragNode().getId())
                                && stepDO.getDropNode().getType() != null
                                && stepDO.getDropNode().getType().equals("case")) {
                            iterator.remove();
                            stepNew = stepItem;
                            break;
                        }
                    }

                    if (stepNew != null && targetCase != null) {
                        // 对源列表重新排序
                        sourceStepList.forEach(DragSortUtil.getIndex((item, index) -> {
                            item.setOrder(index + 1);
                        }));
                        sourceCase.setStepList(sourceStepList);

                        // 添加到目标列表
                        List<StepDO> targetStepList = targetCase.getStepList();
                        if (targetStepList == null) {
                            targetStepList = new ArrayList<>();
                        }
                        stepNew.setPid(stepDO.getDropNode().getId());
                        stepNew.setOrder(targetStepList.size() + 1);
                        targetStepList.add(stepNew);
                        targetCase.setStepList(targetStepList);
                    } else if (stepNew == null && StringUtils.isNotEmpty(sourceStepList)) {
                        // 同一个用例内的步骤排序
                        if (stepDO.getDropPosition() == -1) {
                            DragSortUtil.move(sourceStepList, stepDO.getDragNode().getOrder() - 1, stepDO.getDropNode().getOrder() - 1);
                        } else if (stepDO.getDropPosition() == 0) {
                            DragSortUtil.swap(sourceStepList, stepDO.getDragNode().getOrder() - 1, stepDO.getDropNode().getOrder() - 1);
                        } else if (stepDO.getDropPosition() == 1) {
                            DragSortUtil.move(sourceStepList, stepDO.getDragNode().getOrder() - 1, stepDO.getDropNode().getOrder() - 1);
                        }
                        sourceStepList.forEach(DragSortUtil.getIndex((item, index) -> {
                            item.setOrder(index + 1);
                        }));
                        sourceCase.setStepList(sourceStepList);
                    }
                }
            }

            automationUiSceneDO.setCaseList(caseList);
            baseMapper.updateById(automationUiSceneDO);
        }
    }

    public void addStep1(CaseDO caseDO, Long id) {
        AutomationUiSceneDO automationUiSceneDO = baseMapper.selectById(id);
        List<CaseDO> caseList = automationUiSceneDO.getCaseList();
        if (StringUtils.isNotEmpty(caseList)) {
            IdGenerator idGenerator = DefaultIdGeneratorProvider.INSTANCE.getShare();
            for (CaseDO caseItem : caseList) {
                if (caseItem.getId().equals(caseDO.getId())) {
                    StepDO stepDO = caseDO.getStep();
                    List<StepDO> stepList = caseItem.getStepList();
                    if (StringUtils.isEmpty(stepList)) {
                        stepList = new ArrayList<>();
                        stepDO.setOrder(1);
                    } else {
                        stepDO.setOrder(stepList.size() + 1);
                    }
                    stepDO.setId(String.valueOf(idGenerator.generate()));
                    stepList.add(stepDO);
                    if (caseDO.getSortType() != null) {
                        if (caseDO.getSortType() == 1) {
                            DragSortUtil.swap(stepList, caseDO.getOrder() - 1, caseDO.getItemOrder() - 1);
                        } else if (caseDO.getSortType() == 2) {
                            DragSortUtil.move(stepList, caseDO.getOrder() - 1, caseDO.getItemOrder() - 1);
                        }
                        stepList.forEach(DragSortUtil.getIndex((item, index) -> {
                            item.setOrder(index + 1);
                        }));
                    }
                    caseItem.setStepList(stepList);
                    break;
                }
            }
        }
        automationUiSceneDO.setCaseList(caseList);
        baseMapper.updateById(automationUiSceneDO);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
            .eq(AutomationUiSceneDO::getProjectId, param[0])
            .eq(AutomationUiSceneDO::getVersionId, param[1])
            .eq(AutomationUiSceneDO::getSceneId, param[2])
            .eq(AutomationUiSceneDO::getDelFlag, 3)
            .ne(null != id, AutomationUiSceneDO::getId, id)
            .exists();
    }
}