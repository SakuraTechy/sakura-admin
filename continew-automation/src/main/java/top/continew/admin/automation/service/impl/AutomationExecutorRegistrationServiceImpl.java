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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.mapper.AutomationExecutorRegistrationMapper;
import top.continew.admin.automation.model.entity.AutomationExecutorRegistrationDO;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorCapabilityReq;
import top.continew.admin.automation.model.req.catalog.AutomationExecutorRegistrationReq;
import top.continew.admin.automation.service.AutomationExecutorRegistrationService;
import top.continew.starter.core.exception.BusinessException;

/** 执行器独立注册服务实现。 */
@Service
@RequiredArgsConstructor
public class AutomationExecutorRegistrationServiceImpl implements AutomationExecutorRegistrationService {

    private static final int ENABLED = 1;

    private final AutomationExecutorRegistrationMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isRegistered(String executorType, String executorInstanceId, Long projectEnvironmentId) {
        return isRegistered(executorType, executorInstanceId, projectEnvironmentId, null);
    }

    @Override
    public boolean isRegistered(String executorType,
                                String executorInstanceId,
                                Long projectEnvironmentId,
                                String applicationAccessKey) {
        String normalizedType = normalize(executorType);
        String normalizedInstance = normalize(executorInstanceId);
        if (normalizedType.isBlank() || normalizedInstance.isBlank()) {
            return false;
        }
        var query = mapper.lambdaQuery()
            .eq(AutomationExecutorRegistrationDO::getExecutorType, normalizedType)
            .eq(AutomationExecutorRegistrationDO::getExecutorInstanceId, normalizedInstance)
            .eq(AutomationExecutorRegistrationDO::getStatus, ENABLED);
        if (applicationAccessKey == null || applicationAccessKey.isBlank()) {
            query.isNull(AutomationExecutorRegistrationDO::getApplicationAccessKey);
        } else {
            query
                .eq(AutomationExecutorRegistrationDO::getApplicationAccessKey, normalizeAccessKey(applicationAccessKey));
        }
        return query.and(wrapper -> wrapper.isNull(AutomationExecutorRegistrationDO::getProjectEnvironmentId)
            .or()
            .eq(projectEnvironmentId != null, AutomationExecutorRegistrationDO::getProjectEnvironmentId, projectEnvironmentId))
            .exists();
    }

    @Override
    public Optional<AutomationExecutorRegistrationDO> find(String executorType, String executorInstanceId) {
        return mapper.lambdaQuery()
            .eq(AutomationExecutorRegistrationDO::getExecutorType, normalize(executorType))
            .eq(AutomationExecutorRegistrationDO::getExecutorInstanceId, normalize(executorInstanceId))
            .oneOpt();
    }

    @Override
    public synchronized void register(AutomationExecutorRegistrationReq req) {
        String executorType = normalize(req.getExecutorType());
        String executorInstanceId = normalize(req.getExecutorInstanceId());
        if (!"playwright".equals(executorType) && !"cuecast".equals(executorType)) {
            throw new BusinessException("不支持的执行器类型：" + req.getExecutorType());
        }
        AutomationExecutorRegistrationDO existing = mapper.lambdaQuery()
            .eq(AutomationExecutorRegistrationDO::getExecutorType, executorType)
            .eq(AutomationExecutorRegistrationDO::getExecutorInstanceId, executorInstanceId)
            .one();
        AutomationExecutorRegistrationDO registration = existing == null
            ? new AutomationExecutorRegistrationDO()
            : existing;
        registration.setExecutorType(executorType);
        registration.setExecutorInstanceId(executorInstanceId);
        if (existing == null || req.getApplicationAccessKey() != null) {
            registration.setApplicationAccessKey(normalizeAccessKey(req.getApplicationAccessKey()));
        }
        if (existing == null || req.getNodeConfigId() != null) {
            registration.setNodeConfigId(req.getNodeConfigId());
        }
        if (existing == null || req.getProjectEnvironmentId() != null) {
            registration.setProjectEnvironmentId(req.getProjectEnvironmentId());
        }
        if (existing == null || req.getDescription() != null) {
            registration.setDescription(req.getDescription());
        }
        registration.setStatus(ENABLED);
        if (existing == null) {
            mapper.insert(registration);
        } else {
            mapper.updateById(registration);
        }
    }

    @Override
    public void disable(String executorType, String executorInstanceId) {
        boolean updated = mapper.lambdaUpdate()
            .eq(AutomationExecutorRegistrationDO::getExecutorType, normalize(executorType))
            .eq(AutomationExecutorRegistrationDO::getExecutorInstanceId, normalize(executorInstanceId))
            .set(AutomationExecutorRegistrationDO::getStatus, 0)
            .update();
        if (!updated) {
            throw new BusinessException("执行器注册信息不存在：" + executorInstanceId);
        }
    }

    @Override
    public void recordReport(String executorType, AutomationExecutorCapabilityReq req) {
        mapper.lambdaUpdate()
            .eq(AutomationExecutorRegistrationDO::getExecutorType, normalize(executorType))
            .eq(AutomationExecutorRegistrationDO::getExecutorInstanceId, normalize(req.getExecutorInstanceId()))
            .set(AutomationExecutorRegistrationDO::getLastExecutorVersion, req.getExecutorVersion())
            .set(AutomationExecutorRegistrationDO::getLastCatalogVersion, req.getCatalogVersion())
            .set(AutomationExecutorRegistrationDO::getLastActions, writeJson(req.getActions()))
            .set(AutomationExecutorRegistrationDO::getLastFeatures, writeJson(req.getFeatures()))
            .set(AutomationExecutorRegistrationDO::getLastReportedAt, LocalDateTime.now())
            .update();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAccessKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.List.of() : value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("执行器能力清单保存失败");
        }
    }
}
