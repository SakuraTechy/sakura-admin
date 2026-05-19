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

import top.continew.admin.common.ssh.FreeSshUtil;
import top.continew.admin.common.ssh.SSHUtil;
import top.continew.admin.project.model.resp.ProjectServerConfigResp;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.admin.project.model.query.ProjectServerConfigQuery;
import top.continew.admin.project.model.req.ProjectServerConfigReq;
import top.continew.admin.project.model.resp.ProjectServerConfigDetailResp;
import top.continew.admin.project.service.ProjectServerConfigService;
import top.continew.admin.project.service.ProjectConfigService;

/**
 * 项目管理-服务器配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Service
@RequiredArgsConstructor
public class ProjectServerConfigServiceImpl extends BaseServiceImpl<ProjectServerConfigMapper, ProjectServerConfigDO, ProjectServerConfigResp, ProjectServerConfigDetailResp, ProjectServerConfigQuery, ProjectServerConfigReq> implements ProjectServerConfigService {

    private final ProjectConfigService projectConfigService;

    @Override
    public List<ProjectServerConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectServerConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), ProjectServerConfigDetailResp.class);
        for (ProjectServerConfigDetailResp item : list) {
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
            .eq(ProjectServerConfigDO::getProjectId, param[0])
            .eq(ProjectServerConfigDO::getIp, param[1])
            .eq(ProjectServerConfigDO::getPort, param[2])
            .eq(ProjectServerConfigDO::getDelFlag, 3)
            .ne(null != id, ProjectServerConfigDO::getId, id)
            .exists();
    }

    /**
     * 测试服务器配置信息
     *
     * @param projectServerConfigReq 服务器配置
     * @return true：测试成功；false：测试失败
     */
    @Override
    public boolean testServer(ProjectServerConfigReq projectServerConfigReq) {
        if (projectServerConfigReq.getType().equals("Linux")) {
            return SSHUtil.testConnection(projectServerConfigReq.getIp(), projectServerConfigReq
                .getPort(), projectServerConfigReq.getUserName(), projectServerConfigReq.getPassWord());
        } else if (projectServerConfigReq.getType().equals("Windows")) {
            return FreeSshUtil.testConnection(projectServerConfigReq.getIp(), projectServerConfigReq
                .getPort(), projectServerConfigReq.getUserName(), projectServerConfigReq.getPassWord());
        }
        return false;
    }
}