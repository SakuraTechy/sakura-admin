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
import top.continew.admin.project.model.query.ProjectEnvironmentConfigQuery;
import top.continew.admin.project.model.req.ProjectEnvironmentConfigReq;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigResp;
import top.continew.admin.project.model.resp.ProjectEnvironmentRuntimeStatusResp;

/**
 * 项目管理-环境配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
public interface ProjectEnvironmentConfigService extends BaseService<ProjectEnvironmentConfigResp, ProjectEnvironmentConfigDetailResp, ProjectEnvironmentConfigQuery, ProjectEnvironmentConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<ProjectEnvironmentConfigDetailResp> selectByIds(List<Long> ids);

    /**
     * Query realtime runtime status by environment ID.
     *
     * @param id environment ID
     * @return runtime status
     */
    ProjectEnvironmentRuntimeStatusResp getRuntimeStatus(Long id);

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

    /**
     * 同步环境中的版本配置快照。
     *
     * @param type 操作类型（update/delete）
     * @param id   版本配置 ID
     * @return 是否存在同步更新
     */
    boolean updateVersionConfig(String type, Long id);

    /**
     * 同步环境中的服务器配置快照。
     *
     * @param type 操作类型（update/delete）
     * @param id   服务器配置 ID
     * @return 是否存在同步更新
     */
    boolean updateServerConfig(String type, Long id);

    /**
     * 同步环境中的数据库配置快照。
     *
     * @param type 操作类型（update/delete）
     * @param id   数据库配置 ID
     * @return 是否存在同步更新
     */
    boolean updateDataBaseConfig(String type, Long id);
}
