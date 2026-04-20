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

import java.util.List;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.query.ProjectEnvironmentConfigQuery;
import top.continew.admin.project.model.req.ProjectEnvironmentConfigReq;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigResp;
import top.continew.admin.project.service.ProjectEnvironmentConfigService;

/**
 * 项目管理-环境配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Service
@RequiredArgsConstructor
public class ProjectEnvironmentConfigServiceImpl extends BaseServiceImpl<ProjectEnvironmentConfigMapper, ProjectEnvironmentConfigDO, ProjectEnvironmentConfigResp, ProjectEnvironmentConfigDetailResp, ProjectEnvironmentConfigQuery, ProjectEnvironmentConfigReq> implements ProjectEnvironmentConfigService {

    private final ProjectConfigService projectConfigService;

    @Override
    public List<ProjectEnvironmentConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectEnvironmentConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), ProjectEnvironmentConfigDetailResp.class);
        list.forEach(item -> {
            String projectName = projectConfigService.get(item.getProjectId()).getName();
            item.setProjectName(projectName);
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        });
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
            .eq(ProjectEnvironmentConfigDO::getProjectId, param[0])
            .eq(ProjectEnvironmentConfigDO::getName, param[1])
            .eq(ProjectEnvironmentConfigDO::getDelFlag, 3)
            .ne(null != id, ProjectEnvironmentConfigDO::getId, id)
            .exists();
    }
}