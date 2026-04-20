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

import java.util.List;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.mapper.AutomationProjectConfigMapper;
import top.continew.admin.automation.model.entity.AutomationProjectConfigDO;
import top.continew.admin.automation.model.query.AutomationProjectConfigQuery;
import top.continew.admin.automation.model.req.AutomationProjectConfigReq;
import top.continew.admin.automation.model.resp.AutomationProjectConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationProjectConfigResp;
import top.continew.admin.automation.service.AutomationProjectConfigService;

/**
 * 自动化管理-项目配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
@Service
@RequiredArgsConstructor
public class AutomationProjectConfigServiceImpl extends BaseServiceImpl<AutomationProjectConfigMapper, AutomationProjectConfigDO, AutomationProjectConfigResp, AutomationProjectConfigDetailResp, AutomationProjectConfigQuery, AutomationProjectConfigReq> implements AutomationProjectConfigService {

    @Override
    public List<AutomationProjectConfigDetailResp> selectByIds(List<Long> ids) {
        List<AutomationProjectConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), AutomationProjectConfigDetailResp.class);
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

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
            .eq(AutomationProjectConfigDO::getName, param[0])
            .eq(AutomationProjectConfigDO::getUrl, param[1])
            .eq(AutomationProjectConfigDO::getDelFlag, 3)
            .ne(null != id, AutomationProjectConfigDO::getId, id)
            .exists();
    }
}