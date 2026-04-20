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

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.automation.model.query.AutomationProjectConfigQuery;
import top.continew.admin.automation.model.req.AutomationProjectConfigReq;
import top.continew.admin.automation.model.resp.AutomationProjectConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationProjectConfigResp;

/**
 * 自动化管理-项目配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
public interface AutomationProjectConfigService extends BaseService<AutomationProjectConfigResp, AutomationProjectConfigDetailResp, AutomationProjectConfigQuery, AutomationProjectConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<AutomationProjectConfigDetailResp> selectByIds(List<Long> ids);

    /**
     * 根据 ID 删除
     *
     * @param ids ID 列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据参数条件，判断项目是否存在
     *
     * @param param 参数条件
     * @param id    ID
     * @return true：存在；false：不存在
     */
    boolean isExists(Long id, Object... param);
}