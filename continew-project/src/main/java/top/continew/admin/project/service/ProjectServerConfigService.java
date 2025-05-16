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
import top.continew.admin.project.model.query.ProjectServerConfigQuery;
import top.continew.admin.project.model.req.ProjectServerConfigReq;
import top.continew.admin.project.model.resp.ProjectServerConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectServerConfigResp;

/**
 * 项目管理-服务器配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
public interface ProjectServerConfigService extends BaseService<ProjectServerConfigResp, ProjectServerConfigDetailResp, ProjectServerConfigQuery, ProjectServerConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<ProjectServerConfigDetailResp> selectByIds(List<Long> ids);

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
     * @param id   ID
     * @return true：存在；false：不存在
     */
    boolean isExists(Long id, Object... param);

    /**
     * 测试服务器配置信息
     *
     * @param projectServerConfigReq 服务器配置
     * @return true：测试成功；false：测试失败
     */
    boolean testServer(ProjectServerConfigReq projectServerConfigReq);
}