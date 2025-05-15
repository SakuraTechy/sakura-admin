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
import java.util.ArrayList;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.query.ProjectConfigQuery;
import top.continew.admin.project.model.req.ProjectConfigReq;
import top.continew.admin.project.model.resp.ProjectConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectConfigResp;
import top.continew.admin.project.service.ProjectConfigService;

/**
 * 项目配置业务实现
 *
 * @author hagyao520
 * @since 2025/04/11 18:11
 */
@Service
@RequiredArgsConstructor
public class ProjectConfigServiceImpl extends BaseServiceImpl<ProjectConfigMapper, ProjectConfigDO, ProjectConfigResp, ProjectConfigDetailResp, ProjectConfigQuery, ProjectConfigReq> implements ProjectConfigService {
    @Override
    public List<ProjectConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), ProjectConfigDetailResp.class);
        for (ProjectConfigDetailResp item : list) {
            List<String> memberNames = new ArrayList<>();
            List<String> updateUserString = new ArrayList<>();
            item.getMember().forEach(memberId -> {
                //                String memberName1 = ExceptionUtils.exToNull(() -> SpringUtil.getBean(CommonUserService.class).getNicknameById(Long.valueOf(memberId)));
                String memberName = UserContextHolder.getNickname(Long.valueOf(memberId));
                memberNames.add(memberName);
            });
            item.setMemberNames(memberNames);
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        }
        ;
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(String name, String abbreviate, Long id) {
        return baseMapper.lambdaQuery()
            .eq(ProjectConfigDO::getName, name)
            .eq(ProjectConfigDO::getAbbreviate, abbreviate)
            .eq(ProjectConfigDO::getDelFlag, 1)
            .ne(null != id, ProjectConfigDO::getId, id)
            .exists();
    }
}