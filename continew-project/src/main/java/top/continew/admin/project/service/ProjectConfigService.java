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

package top.continew.admin.project.service;

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.project.model.query.ProjectConfigQuery;
import top.continew.admin.project.model.req.ProjectConfigReq;
import top.continew.admin.project.model.resp.ProjectConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectConfigResp;

/**
 * 项目配置业务接口
 *
 * @author hagyao520
 * @since 2025/04/11 18:11
 */
public interface ProjectConfigService extends BaseService<ProjectConfigResp, ProjectConfigDetailResp, ProjectConfigQuery, ProjectConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<ProjectConfigDetailResp> selectByIds(List<Long> ids);

    /**
     * 根据 ID 删除
     *
     * @param ids ID 列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据项目名称和简称，判断项目是否存在
     *
     * @param name       项目名称
     * @param abbreviate 项目简称
     * @param id         ID
     * @return true：存在；false：不存在
     */
    boolean isExists(String name, String abbreviate, Long id);
}