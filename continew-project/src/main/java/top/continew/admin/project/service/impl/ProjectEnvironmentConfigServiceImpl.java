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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectDataBaseConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.admin.project.model.query.ProjectEnvironmentConfigQuery;
import top.continew.admin.project.model.req.ProjectEnvironmentConfigReq;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigResp;
import top.continew.admin.project.model.resp.ProjectEnvironmentRuntimeStatusResp;
import top.continew.admin.project.service.ProjectEnvironmentConfigService;
import top.continew.starter.core.validation.CheckUtils;

/**
 * 项目管理-环境配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectEnvironmentConfigServiceImpl extends BaseServiceImpl<ProjectEnvironmentConfigMapper, ProjectEnvironmentConfigDO, ProjectEnvironmentConfigResp, ProjectEnvironmentConfigDetailResp, ProjectEnvironmentConfigQuery, ProjectEnvironmentConfigReq> implements ProjectEnvironmentConfigService {

    private final ProjectConfigService projectConfigService;
    private final ProjectVersionConfigMapper projectVersionConfigMapper;
    private final ProjectServerConfigMapper projectServerConfigMapper;
    private final ProjectDataBaseConfigMapper projectDataBaseConfigMapper;

    @Override
    public void beforeCreate(ProjectEnvironmentConfigReq req) {
        sanitizeDatabaseSnapshots(req);
    }

    @Override
    public void beforeUpdate(ProjectEnvironmentConfigReq req, Long id) {
        sanitizeDatabaseSnapshots(req);
    }

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
    public ProjectEnvironmentRuntimeStatusResp getRuntimeStatus(Long id) {
        ProjectEnvironmentConfigDO environmentConfig = baseMapper.selectById(id);
        CheckUtils.throwIfNull(environmentConfig, "环境配置不存在");

        ProjectServerConfigDO serverConfig = resolvePrimaryServer(environmentConfig.getServerConfig());
        CheckUtils.throwIfNull(serverConfig, "环境未配置服务器信息");

        ProjectEnvironmentRuntimeStatusResp resp = new ProjectEnvironmentRuntimeStatusResp();
        resp.setEnvironmentId(id);
        resp.setServerIp(serverConfig.getIp());
        resp.setOnlineStatus(checkServerOnline(serverConfig) ? StatusTypeEnum.ONLINE : StatusTypeEnum.OFFLINE);
        return resp;
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

    @Override
    public boolean updateVersionConfig(String type, Long id) {
        try {
            List<ProjectEnvironmentConfigDO> environmentList = baseMapper.lambdaQuery()
                .eq(ProjectEnvironmentConfigDO::getDelFlag, 3)
                .list();
            ProjectVersionConfigDO versionConfig = projectVersionConfigMapper.selectById(id);
            for (ProjectEnvironmentConfigDO environment : environmentList) {
                List<Object> versionConfigList = environment.getVersionConfig();
                if (versionConfigList == null || versionConfigList.isEmpty()) {
                    continue;
                }
                if ("delete".equals(type)) {
                    versionConfigList.removeIf(item -> {
                        ProjectVersionConfigDO candidate = BeanUtil.toBean(item, ProjectVersionConfigDO.class);
                        return candidate != null && candidate.getId() != null && candidate.getId().equals(id);
                    });
                } else {
                    if (versionConfig == null) {
                        continue;
                    }
                    for (int i = 0; i < versionConfigList.size(); i++) {
                        ProjectVersionConfigDO candidate = BeanUtil.toBean(versionConfigList
                            .get(i), ProjectVersionConfigDO.class);
                        if (candidate != null && candidate.getId() != null && candidate.getId().equals(id)) {
                            versionConfigList.set(i, versionConfig);
                            break;
                        }
                    }
                }
                environment.setVersionConfig(versionConfigList);
                baseMapper.updateById(environment);
            }
            return true;
        } catch (Exception e) {
            log.error("同步环境版本配置失败，{}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    public boolean updateServerConfig(String type, Long id) {
        try {
            List<ProjectEnvironmentConfigDO> environmentList = baseMapper.lambdaQuery()
                .eq(ProjectEnvironmentConfigDO::getDelFlag, 3)
                .list();
            ProjectServerConfigDO serverConfig = projectServerConfigMapper.selectById(id);
            for (ProjectEnvironmentConfigDO environment : environmentList) {
                List<Object> serverConfigList = environment.getServerConfig();
                if (serverConfigList == null || serverConfigList.isEmpty()) {
                    continue;
                }
                if ("delete".equals(type)) {
                    serverConfigList.removeIf(item -> {
                        ProjectServerConfigDO candidate = BeanUtil.toBean(item, ProjectServerConfigDO.class);
                        return candidate != null && candidate.getId() != null && candidate.getId().equals(id);
                    });
                } else {
                    if (serverConfig == null) {
                        continue;
                    }
                    for (int i = 0; i < serverConfigList.size(); i++) {
                        ProjectServerConfigDO candidate = BeanUtil.toBean(serverConfigList
                            .get(i), ProjectServerConfigDO.class);
                        if (candidate != null && candidate.getId() != null && candidate.getId().equals(id)) {
                            serverConfigList.set(i, serverConfig);
                            break;
                        }
                    }
                }
                environment.setServerConfig(serverConfigList);
                baseMapper.updateById(environment);
            }
            return true;
        } catch (Exception e) {
            log.error("同步环境服务器配置失败，{}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    public boolean updateDataBaseConfig(String type, Long id) {
        try {
            List<ProjectEnvironmentConfigDO> environmentList = baseMapper.lambdaQuery()
                .eq(ProjectEnvironmentConfigDO::getDelFlag, 3)
                .list();
            ProjectDataBaseConfigDO dataBaseConfig = projectDataBaseConfigMapper.selectById(id);
            for (ProjectEnvironmentConfigDO environment : environmentList) {
                List<Object> dataBaseConfigList = environment.getDataBaseConfig();
                if (dataBaseConfigList == null || dataBaseConfigList.isEmpty()) {
                    continue;
                }
                if ("delete".equals(type)) {
                    dataBaseConfigList.removeIf(item -> {
                        ProjectDataBaseConfigDO candidate = BeanUtil.toBean(item, ProjectDataBaseConfigDO.class);
                        return candidate != null && candidate.getId() != null && candidate.getId().equals(id);
                    });
                } else {
                    if (dataBaseConfig == null) {
                        continue;
                    }
                    for (int i = 0; i < dataBaseConfigList.size(); i++) {
                        ProjectDataBaseConfigDO candidate = BeanUtil.toBean(dataBaseConfigList
                            .get(i), ProjectDataBaseConfigDO.class);
                        if (candidate != null && candidate.getId() != null && candidate.getId().equals(id)) {
                            // 环境 JSON 只保留非敏感快照；执行时按 ID 回查数据库主表获取凭据。
                            dataBaseConfigList.set(i, toEnvironmentDatabaseSnapshot(dataBaseConfig));
                            break;
                        }
                    }
                }
                environment.setDataBaseConfig(dataBaseConfigList);
                baseMapper.updateById(environment);
            }
            return true;
        } catch (Exception e) {
            log.error("同步环境数据库配置失败，{}", e.getMessage(), e);
        }
        return false;
    }

    static ProjectDataBaseConfigDO toEnvironmentDatabaseSnapshot(ProjectDataBaseConfigDO source) {
        if (source == null) {
            return null;
        }
        ProjectDataBaseConfigDO snapshot = BeanUtil.copyProperties(source, ProjectDataBaseConfigDO.class);
        snapshot.setUserName(null);
        snapshot.setPassWord(null);
        snapshot.setUrl(null);
        snapshot.setConfigList(null);
        // 环境快照仅用于定位资源，审计字段属于主表元数据，不能写入 JSON 快照。
        snapshot.setCreateUser(null);
        snapshot.setCreateTime(null);
        snapshot.setUpdateUser(null);
        snapshot.setUpdateTime(null);
        return snapshot;
    }

    private void sanitizeDatabaseSnapshots(ProjectEnvironmentConfigReq req) {
        if (req == null || req.getDataBaseConfig() == null) {
            return;
        }
        req.setDataBaseConfig(req.getDataBaseConfig()
            .stream()
            .map(item -> (Object)toEnvironmentDatabaseSnapshot(BeanUtil.toBean(item, ProjectDataBaseConfigDO.class)))
            .toList());
    }

    private ProjectServerConfigDO resolvePrimaryServer(List<?> serverConfigList) {
        if (serverConfigList == null || serverConfigList.isEmpty()) {
            return null;
        }
        return serverConfigList.stream()
            .map(item -> BeanUtil.toBean(item, ProjectServerConfigDO.class))
            .filter(item -> item != null && item.getStatus() == DisEnableStatusEnum.ENABLE)
            .findFirst()
            .orElseGet(() -> serverConfigList.stream()
                .filter(item -> item != null)
                .findFirst()
                .map(item -> BeanUtil.toBean(item, ProjectServerConfigDO.class))
                .orElse(null));
    }

    private boolean checkServerOnline(ProjectServerConfigDO serverConfig) {
        if (serverConfig == null || serverConfig.getIp() == null || serverConfig.getIp().isBlank()) {
            return false;
        }
        int port = serverConfig.getPort() == null ? 22 : serverConfig.getPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverConfig.getIp(), port), 1500);
            return true;
        } catch (Exception e) {
            log.debug("Check project environment server status failed, ip={}, port={}, msg={}", serverConfig
                .getIp(), port, e.getMessage());
            return false;
        }
    }
}
