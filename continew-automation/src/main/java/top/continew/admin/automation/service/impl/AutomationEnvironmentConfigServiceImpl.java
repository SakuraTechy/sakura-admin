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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import top.continew.admin.automation.mapper.*;
import top.continew.admin.automation.model.entity.*;
import top.continew.admin.automation.model.resp.AutomationEnvironmentRuntimeStatusResp;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.jenkins.JenkinsService;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.model.query.AutomationEnvironmentConfigQuery;
import top.continew.admin.automation.model.req.AutomationEnvironmentConfigReq;
import top.continew.admin.automation.model.resp.AutomationEnvironmentConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationEnvironmentConfigResp;
import top.continew.admin.automation.service.AutomationEnvironmentConfigService;

/**
 * 自动化管理-环境配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationEnvironmentConfigServiceImpl extends BaseServiceImpl<AutomationEnvironmentConfigMapper, AutomationEnvironmentConfigDO, AutomationEnvironmentConfigResp, AutomationEnvironmentConfigDetailResp, AutomationEnvironmentConfigQuery, AutomationEnvironmentConfigReq> implements AutomationEnvironmentConfigService {

    private final AutomationProjectConfigMapper automationProjectConfigMapper;
    private final AutomationJenkinsConfigMapper automationJenkinsConfigMapper;
    private final AutomationNodeConfigMapper automationNodeConfigMapper;
    private final AutomationBrowserConfigMapper automationBrowserConfigMapper;

    @Override
    public List<AutomationEnvironmentConfigDetailResp> selectByIds(List<Long> ids) {
        List<AutomationEnvironmentConfigDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), AutomationEnvironmentConfigDetailResp.class);
        list.forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        });
        return list;
    }

    @Override
    public AutomationEnvironmentRuntimeStatusResp getRuntimeStatus(Long id) {
        AutomationEnvironmentConfigDO environmentConfig = baseMapper.selectById(id);
        CheckUtils.throwIfNull(environmentConfig, "自动化环境不存在");

        AutomationJenkinsConfigDO jenkinsConfig = resolvePrimaryJenkins(environmentConfig.getJenkinsConfig());
        AutomationNodeConfigDO nodeConfig = resolvePrimaryNode(environmentConfig.getNodeConfig());
        CheckUtils.throwIfNull(jenkinsConfig, "自动化环境未配置 Jenkins");
        CheckUtils.throwIfNull(nodeConfig, "自动化环境未配置节点");

        AutomationEnvironmentRuntimeStatusResp resp = new AutomationEnvironmentRuntimeStatusResp();
        resp.setEnvironmentId(id);
        resp.setNodeName(nodeConfig.getName());

        try {
            JsonNode computer = JenkinsService.getJenkinsNode(jenkinsConfig.getUrl(), jenkinsConfig
                .getUserName(), jenkinsConfig.getPassWord(), nodeConfig.getName());
            boolean offline = computer.path("offline").asBoolean();
            boolean idle = computer.path("idle").asBoolean();

            resp.setOnlineStatus(offline ? StatusTypeEnum.OFFLINE : StatusTypeEnum.ONLINE);
            resp.setUseStatus(offline ? StatusTypeEnum.OFFLINE : idle ? StatusTypeEnum.IDLE : StatusTypeEnum.IN_USE);
        } catch (Exception e) {
            log.warn("Query automation environment runtime status failed, environmentId={}, msg={}", id, e
                .getMessage());
            resp.setOnlineStatus(StatusTypeEnum.OFFLINE);
            resp.setUseStatus(StatusTypeEnum.OFFLINE);
        } finally {
            try {
                JenkinsService.close();
            } catch (Exception e) {
                log.debug("Close Jenkins connection failed: {}", e.getMessage());
            }
        }
        return resp;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
            .eq(AutomationEnvironmentConfigDO::getType, param[0])
            .eq(AutomationEnvironmentConfigDO::getName, param[1])
            .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
            .ne(null != id, AutomationEnvironmentConfigDO::getId, id)
            .exists();
    }

    @Override
    public boolean updateProjectConfig(String type, Long id) {
        try {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                .list();
            AutomationProjectConfigDO automationProjectConfigDO = automationProjectConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationProjectConfigDO> projectConfigList = automationEnvironmentConfigDO.getProjectConfig();
                if (projectConfigList == null || projectConfigList.isEmpty()) {
                    continue;
                }
                if (type.equals("delete")) {
                    projectConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                } else {
                    if (automationProjectConfigDO == null) {
                        continue;
                    }
                    for (int i = 0; i < projectConfigList.size(); i++) {
                        if (Objects.equals(projectConfigList.get(i).getId(), id)) {
                            projectConfigList.set(i, automationProjectConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setProjectConfig(projectConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
            }
            return true;
        } catch (Exception e) {
            log.error("更新环境项目配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateJenkinsConfig(String type, Long id) {
        try {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                .list();
            AutomationJenkinsConfigDO automationJenkinsConfigDO = automationJenkinsConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationJenkinsConfigDO> jenkinsConfigList = automationEnvironmentConfigDO.getJenkinsConfig();
                if (jenkinsConfigList == null || jenkinsConfigList.isEmpty()) {
                    continue;
                }
                if (type.equals("delete")) {
                    jenkinsConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                } else {
                    if (automationJenkinsConfigDO == null) {
                        continue;
                    }
                    for (int i = 0; i < jenkinsConfigList.size(); i++) {
                        if (Objects.equals(jenkinsConfigList.get(i).getId(), id)) {
                            jenkinsConfigList.set(i, automationJenkinsConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setJenkinsConfig(jenkinsConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
            }
            return true;
        } catch (Exception e) {
            log.error("更新环境Jenkins配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateNodeConfig(String type, Long id) {
        try {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                .list();
            AutomationNodeConfigDO automationNodeConfigDO = automationNodeConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationNodeConfigDO> nodeConfigList = automationEnvironmentConfigDO.getNodeConfig();
                if (nodeConfigList == null || nodeConfigList.isEmpty()) {
                    continue;
                }
                if (type.equals("delete")) {
                    nodeConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                } else {
                    if (automationNodeConfigDO == null) {
                        continue;
                    }
                    for (int i = 0; i < nodeConfigList.size(); i++) {
                        if (Objects.equals(nodeConfigList.get(i).getId(), id)) {
                            nodeConfigList.set(i, automationNodeConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setNodeConfig(nodeConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
            }
            return true;
        } catch (Exception e) {
            log.error("更新环境节点配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateBrowserConfig(String type, Long id) {
        try {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                .list();
            AutomationBrowserConfigDO automationBrowserConfigDO = automationBrowserConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationBrowserConfigDO> browserConfigList = automationEnvironmentConfigDO.getBrowserConfig();
                if (browserConfigList == null || browserConfigList.isEmpty()) {
                    continue;
                }
                if (type.equals("delete")) {
                    browserConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                } else {
                    if (automationBrowserConfigDO == null) {
                        continue;
                    }
                    for (int i = 0; i < browserConfigList.size(); i++) {
                        if (Objects.equals(browserConfigList.get(i).getId(), id)) {
                            browserConfigList.set(i, automationBrowserConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setBrowserConfig(browserConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
            }
            return true;
        } catch (Exception e) {
            log.error("更新环境浏览器配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateJenkinsProjectConfig(String type, Long id) {
        try {
            List<AutomationJenkinsConfigDO> jenkinsConfigList = automationJenkinsConfigMapper.lambdaQuery()
                .eq(AutomationJenkinsConfigDO::getDelFlag, 3)
                .list();
            AutomationProjectConfigDO projectConfig = automationProjectConfigMapper.selectById(id);
            boolean updated = false;
            for (AutomationJenkinsConfigDO jenkinsConfig : jenkinsConfigList) {
                List<Object> jobList = jenkinsConfig.getJobList();
                if (jobList == null || jobList.isEmpty()) {
                    continue;
                }
                boolean changed = false;
                if ("delete".equals(type)) {
                    int before = jobList.size();
                    jobList.removeIf(item -> isLinkedProject(item, id, projectConfig));
                    changed = before != jobList.size();
                } else {
                    if (projectConfig == null) {
                        continue;
                    }
                    for (int i = 0; i < jobList.size(); i++) {
                        if (isLinkedProject(jobList.get(i), id, projectConfig)) {
                            jobList.set(i, buildLinkedProjectPayload(projectConfig));
                            changed = true;
                        }
                    }
                }
                if (!changed) {
                    continue;
                }
                List<Object> normalizedJobList = normalizeJobListForStorage(jobList);
                // Double-normalize via JSON round-trip to guarantee no entity objects remain.
                normalizedJobList = JSONUtil.toList(JSONUtil.parseArray(JSONUtil
                    .toJsonStr(normalizedJobList)), Object.class);
                jenkinsConfig.setJobList(normalizedJobList);
                automationJenkinsConfigMapper.updateById(jenkinsConfig);
                updateJenkinsConfig("update", jenkinsConfig.getId());
                updated = true;
            }
            return updated;
        } catch (Exception e) {
            log.error("更新 Jenkins 关联项目配置失败，{}", e.getMessage(), e);
        }
        return false;
    }

    private boolean isLinkedProject(Object item, Long projectId, AutomationProjectConfigDO projectConfig) {
        Long linkedId = resolveLinkedProjectId(item);
        if (linkedId != null && Objects.equals(linkedId, projectId)) {
            return true;
        }
        String linkedName = resolveLinkedProjectField(item, "name");
        String linkedUrl = resolveLinkedProjectField(item, "url");
        if (projectConfig != null) {
            if (Objects.equals(linkedName, projectConfig.getName())) {
                return true;
            }
            if (Objects.equals(linkedUrl, projectConfig.getUrl())) {
                return true;
            }
        }
        return false;
    }

    private Long resolveLinkedProjectId(Object item) {
        if (item == null) {
            return null;
        }
        Object idValue = null;
        if (item instanceof Map<?, ?> map) {
            idValue = map.get("id");
            if (idValue == null) {
                idValue = map.get("value");
            }
            if (idValue == null) {
                idValue = map.get("projectId");
            }
        } else {
            idValue = BeanUtil.getProperty(item, "id");
            if (idValue == null) {
                idValue = BeanUtil.getProperty(item, "value");
            }
            if (idValue == null) {
                idValue = BeanUtil.getProperty(item, "projectId");
            }
        }
        if (idValue == null) {
            String extra = resolveLinkedProjectField(item, "extra");
            if (extra != null && JSONUtil.isTypeJSON(extra)) {
                JSONObject json = JSONUtil.parseObj(extra);
                idValue = json.get("id");
                if (idValue == null) {
                    idValue = json.get("value");
                }
                if (idValue == null) {
                    idValue = json.get("projectId");
                }
            }
            if (idValue == null) {
                return null;
            }
        }
        if (idValue instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(idValue));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveLinkedProjectField(Object item, String fieldName) {
        if (item == null) {
            return null;
        }
        Object value;
        if (item instanceof Map<?, ?> map) {
            value = map.get(fieldName);
        } else {
            value = BeanUtil.getProperty(item, fieldName);
        }
        if (value != null) {
            return String.valueOf(value);
        }
        String extra = null;
        if (item instanceof Map<?, ?> map) {
            Object extraObj = map.get("extra");
            extra = extraObj == null ? null : String.valueOf(extraObj);
        } else {
            Object extraObj = BeanUtil.getProperty(item, "extra");
            extra = extraObj == null ? null : String.valueOf(extraObj);
        }
        if (extra != null && JSONUtil.isTypeJSON(extra)) {
            JSONObject json = JSONUtil.parseObj(extra);
            Object extraValue = json.get(fieldName);
            if (extraValue != null) {
                return String.valueOf(extraValue);
            }
        }
        return null;
    }

    private Map<String, Object> buildLinkedProjectPayload(AutomationProjectConfigDO projectConfig) {
        Map<String, Object> payload = new LinkedHashMap<>(8);
        payload.put("id", projectConfig.getId());
        payload.put("name", projectConfig.getName());
        payload.put("type", projectConfig.getType());
        payload.put("url", projectConfig.getUrl());
        payload.put("description", projectConfig.getDescription());
        payload.put("scriptPath", projectConfig.getScriptPath());
        payload.put("status", projectConfig.getStatus());
        payload.put("delFlag", projectConfig.getDelFlag());
        return payload;
    }

    private List<Object> normalizeJobListForStorage(List<Object> jobList) {
        List<Object> normalized = new java.util.ArrayList<>(jobList.size());
        for (Object item : jobList) {
            Map<String, Object> payload = new LinkedHashMap<>(8);
            Long id = resolveLinkedProjectId(item);
            if (id != null) {
                payload.put("id", id);
            }
            String name = resolveLinkedProjectField(item, "name");
            if (name != null) {
                payload.put("name", name);
            }
            String type = resolveLinkedProjectField(item, "type");
            if (type != null) {
                payload.put("type", type);
            }
            String url = resolveLinkedProjectField(item, "url");
            if (url != null) {
                payload.put("url", url);
            }
            String description = resolveLinkedProjectField(item, "description");
            if (description != null) {
                payload.put("description", description);
            }
            String scriptPath = resolveLinkedProjectField(item, "scriptPath");
            if (scriptPath != null) {
                payload.put("scriptPath", scriptPath);
            }
            String status = resolveLinkedProjectField(item, "status");
            if (status != null) {
                payload.put("status", status);
            }
            String delFlag = resolveLinkedProjectField(item, "delFlag");
            if (delFlag != null) {
                payload.put("delFlag", delFlag);
            }
            if (payload.isEmpty()) {
                payload.put("name", String.valueOf(item));
            }
            normalized.add(payload);
        }
        return normalized;
    }

    private AutomationJenkinsConfigDO resolvePrimaryJenkins(List<AutomationJenkinsConfigDO> jenkinsConfigList) {
        if (jenkinsConfigList == null || jenkinsConfigList.isEmpty()) {
            return null;
        }
        return jenkinsConfigList.stream()
            .filter(item -> item != null && item.getStatus() == StatusTypeEnum.ENABLE)
            .findFirst()
            .orElse(jenkinsConfigList.get(0));
    }

    private AutomationNodeConfigDO resolvePrimaryNode(List<AutomationNodeConfigDO> nodeConfigList) {
        if (nodeConfigList == null || nodeConfigList.isEmpty()) {
            return null;
        }
        return nodeConfigList.stream()
            .filter(item -> item != null && item.getStatus() == StatusTypeEnum.ENABLE)
            .findFirst()
            .orElse(nodeConfigList.get(0));
    }
}
