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

import top.continew.admin.common.db.DataBaseUtil;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.model.entity.ProjectDataBaseConfigDO;
import top.continew.admin.project.model.query.ProjectDataBaseConfigQuery;
import top.continew.admin.project.model.req.ProjectDataBaseConfigReq;
import top.continew.admin.project.model.resp.ProjectDataBaseConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectDataBaseConfigResp;
import top.continew.admin.project.service.ProjectDataBaseConfigService;

/**
 * 项目管理-数据库配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/08 18:00
 */
@Service
@RequiredArgsConstructor
public class ProjectDataBaseConfigServiceImpl extends BaseServiceImpl<ProjectDataBaseConfigMapper, ProjectDataBaseConfigDO, ProjectDataBaseConfigResp, ProjectDataBaseConfigDetailResp, ProjectDataBaseConfigQuery, ProjectDataBaseConfigReq> implements ProjectDataBaseConfigService {

    private final ProjectConfigService projectConfigService;

    @Override
    public List<ProjectDataBaseConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectDataBaseConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), ProjectDataBaseConfigDetailResp.class);
        for (ProjectDataBaseConfigDetailResp item : list) {
            String projectName = projectConfigService.get(item.getProjectId()).getName();
            item.setProjectName(projectName);
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        }
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
                .eq(ProjectDataBaseConfigDO::getProjectId, param[0])
                .eq(ProjectDataBaseConfigDO::getIp, param[1])
                .eq(ProjectDataBaseConfigDO::getPort, param[2])
                .eq(ProjectDataBaseConfigDO::getDelFlag, 1)
                .ne(null != id, ProjectDataBaseConfigDO::getId, id)
                .exists();
    }
    /**
     * 测试数据库配置信息
     *
     * @param projectDataBaseConfigReq 数据库配置
     * @return true：测试成功；false：测试失败
     */
    @Override
    public boolean testDataBase(ProjectDataBaseConfigReq projectDataBaseConfigReq) {
        if (projectDataBaseConfigReq.getType().equals("MongoDB")) {
            return DataBaseUtil.testConnection(projectDataBaseConfigReq.getUrl());
        } else {
            return DataBaseUtil.testConnection(projectDataBaseConfigReq.getDriver(), projectDataBaseConfigReq
                .getUrl(), projectDataBaseConfigReq.getUserName(), projectDataBaseConfigReq.getPassWord());
        }
    }
}