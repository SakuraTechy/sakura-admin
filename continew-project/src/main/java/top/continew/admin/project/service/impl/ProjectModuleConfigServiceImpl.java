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

package top.continew.admin.project.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.project.model.req.ProjectModuleDragReq;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectModuleConfigMapper;
import top.continew.admin.project.model.entity.ProjectModuleConfigDO;
import top.continew.admin.project.model.query.ProjectModuleConfigQuery;
import top.continew.admin.project.model.req.ProjectModuleConfigReq;
import top.continew.admin.project.model.resp.ProjectModuleConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectModuleConfigResp;
import top.continew.admin.project.service.ProjectModuleConfigService;

/**
 * 项目管理-模块配置业务实现
 *
 * @author hagyao520
 * @since 2025/06/06 17:44
 */
@Service
@RequiredArgsConstructor
public class ProjectModuleConfigServiceImpl extends BaseServiceImpl<ProjectModuleConfigMapper, ProjectModuleConfigDO, ProjectModuleConfigResp, ProjectModuleConfigDetailResp, ProjectModuleConfigQuery, ProjectModuleConfigReq> implements ProjectModuleConfigService {
    @Override
    public List<ProjectModuleConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectModuleConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), ProjectModuleConfigDetailResp.class);
        list.forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        });
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void drag(ProjectModuleDragReq req) {
        // 校验拖拽节点和目标节点是否存在
        if (req.getDragNode() == null || req.getDropNode() == null) {
            //            throw new IllegalArgumentException("dragNode 或 dropNode 不能为空");
            CheckUtils.throwIf(true, "dragNode 或 dropNode 不能为空");
        }
        CheckUtils.throwIf(req.getDragNode().getId().equals(req.getDropNode().getId()), "不能移动到自己");
        if (req.getDropPosition() == -1) {
            if (req.getDropNode().getParentId() == 0) {
                CheckUtils.throwIf(true, "不能移动到根节点之上");
            }
            // 更新拖拽节点的 parentId 和 sort
            ProjectModuleConfigReq dragNode = new ProjectModuleConfigReq();
            dragNode.setParentId(req.getDropNode().getParentId());
            dragNode.setSort(req.getDropNode().getSort());
            super.update(dragNode, req.getDragNode().getId());

            // 查询目标父节点下的所有子节点
            List<ProjectModuleConfigDO> list = baseMapper.lambdaQuery()
                .eq(ProjectModuleConfigDO::getProjectId, req.getDropNode().getProjectId())
                .eq(ProjectModuleConfigDO::getVersionId, req.getDropNode().getVersionId())
                .eq(ProjectModuleConfigDO::getParentId, req.getDropNode().getParentId())
                .eq(ProjectModuleConfigDO::getDelFlag, 3)
                .list();
            // 修改大于目标节点且不等于自己 + 1
            for (ProjectModuleConfigDO item : list) {
                if (item.getSort() >= req.getDropNode().getSort() && !Objects.equals(item.getId(), req.getDragNode()
                    .getId())) {
                    item.setSort(item.getSort() + 1);
                    super.updateById(item);
                }
            }
            // 重新排序所有模块的 sort 值
            //            list.sort((o1, o2) -> o1.getSort() - o2.getSort());
            //            IntStream.range(0, list.size()).forEach(index -> {
            //                ProjectModuleConfigReq dropNode = new ProjectModuleConfigReq();
            //                dropNode.setSort(index+1);
            //                super.update(dropNode, list.get(index).getId());
            //            });
        }
        if (req.getDropPosition() == 0) {
            // 更新拖拽节点的 parentId 和 sort
            ProjectModuleConfigReq dragNode = new ProjectModuleConfigReq();
            dragNode.setParentId(req.getDropNode().getId());
            super.update(dragNode, req.getDragNode().getId());

            // 查询目标父节点下的所有子节点
            List<ProjectModuleConfigDO> list = baseMapper.lambdaQuery()
                .eq(ProjectModuleConfigDO::getProjectId, req.getDropNode().getProjectId())
                .eq(ProjectModuleConfigDO::getVersionId, req.getDropNode().getVersionId())
                .eq(ProjectModuleConfigDO::getParentId, req.getDropNode().getId())
                .eq(ProjectModuleConfigDO::getDelFlag, 3)
                .list();

            // 修改等于自己的节点为最后一个
            for (ProjectModuleConfigDO item : list) {
                if (Objects.equals(item.getId(), req.getDragNode().getId())) {
                    item.setSort(list.size());
                    super.updateById(item);
                }
            }
        }
        if (req.getDropPosition() == 1) {
            // 更新拖拽节点的 parentId 和 sort
            ProjectModuleConfigReq dragNode = new ProjectModuleConfigReq();
            dragNode.setParentId(req.getDropNode().getParentId() != 0
                ? req.getDropNode().getParentId()
                : req.getDropNode().getId());
            dragNode.setSort(req.getDropNode().getSort() + 1);
            super.update(dragNode, req.getDragNode().getId());

            // 查询目标父节点下的所有子节点
            List<ProjectModuleConfigDO> list = baseMapper.lambdaQuery()
                .eq(ProjectModuleConfigDO::getProjectId, req.getDropNode().getProjectId())
                .eq(ProjectModuleConfigDO::getVersionId, req.getDropNode().getVersionId())
                .eq(ProjectModuleConfigDO::getParentId, req.getDropNode().getParentId() != 0
                    ? req.getDropNode().getParentId()
                    : req.getDropNode().getId())
                .eq(ProjectModuleConfigDO::getDelFlag, 3)
                .list();
            // 修改大于目标节点且不等于自己 + 1
            for (ProjectModuleConfigDO item : list) {
                if (item.getSort() > req.getDropNode().getSort() && !Objects.equals(item.getId(), req.getDragNode()
                    .getId())) {
                    item.setSort(item.getSort() + 1);
                    super.updateById(item);
                }
            }
            // 重新排序所有模块的 sort 值
            //            list.sort((o1, o2) -> o1.getSort() - o2.getSort());
            //            IntStream.range(0, list.size()).forEach(index -> {
            //                ProjectModuleConfigReq dropNode = new ProjectModuleConfigReq();
            //                dropNode.setSort(index+1);
            //                super.update(dropNode, list.get(index).getId());
            //            });
        }
        // 更新拖拽节点原有父节点下的排序
        List<ProjectModuleConfigDO> updateList = new ArrayList<>();
        List<ProjectModuleConfigDO> list = baseMapper.lambdaQuery()
            .eq(ProjectModuleConfigDO::getProjectId, req.getDropNode().getProjectId())
            .eq(ProjectModuleConfigDO::getVersionId, req.getDropNode().getVersionId())
            .eq(ProjectModuleConfigDO::getParentId, req.getDragNode().getParentId())
            .eq(ProjectModuleConfigDO::getDelFlag, 3)
            .list();
        List<ProjectModuleConfigDO> sortedList = list.stream()
            .sorted(Comparator.comparingInt(ProjectModuleConfigDO::getSort))
            .toList();
        int index = 1;
        for (ProjectModuleConfigDO item : sortedList) {
            item.setSort(index++);
            updateList.add(item);
        }
        if (!updateList.isEmpty()) {
            super.updateBatchById(updateList, 500);
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
            .eq(ProjectModuleConfigDO::getProjectId, param[0])
            .eq(ProjectModuleConfigDO::getVersionId, param[1])
            .eq(ProjectModuleConfigDO::getParentId, param[2])
            .eq(ProjectModuleConfigDO::getName, param[3])
            .eq(ProjectModuleConfigDO::getDelFlag, 3)
            .ne(null != id, ProjectModuleConfigDO::getId, id)
            .exists();
    }
}