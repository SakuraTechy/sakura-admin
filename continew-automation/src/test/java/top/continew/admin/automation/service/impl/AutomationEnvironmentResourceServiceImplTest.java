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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.automation.mapper.AutomationFileAssetMapper;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService;
import top.continew.admin.automation.service.AutomationEnvironmentResourceService.ResolvedResource;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentResourceBindingMapper;
import top.continew.admin.project.mapper.ProjectResourceSlotMapper;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentResourceBindingDO;
import top.continew.admin.project.model.entity.ProjectResourceSlotDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;

@ExtendWith(MockitoExtension.class)
class AutomationEnvironmentResourceServiceImplTest {

    @Mock
    private ProjectResourceSlotMapper slotMapper;
    @Mock
    private ProjectEnvironmentResourceBindingMapper bindingMapper;
    @Mock
    private ProjectEnvironmentConfigMapper environmentMapper;
    @Mock
    private ProjectServerConfigMapper serverMapper;
    @Mock
    private ProjectDataBaseConfigMapper databaseMapper;
    @Mock
    private AutomationFileAssetMapper fileAssetMapper;
    @Mock
    private AutomationCertificateWorkspaceService certificateWorkspaceService;

    private AutomationEnvironmentResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationEnvironmentResourceServiceImpl(slotMapper, bindingMapper, environmentMapper, serverMapper, databaseMapper, fileAssetMapper, certificateWorkspaceService);
    }

    @Test
    void shouldResolveResourceBoundToSelectedEnvironment() {
        when(environmentMapper.selectById(7L)).thenReturn(environment(7L, 11L, "测试环境"));
        when(slotMapper.selectById(101L)).thenReturn(slot(101L, 11L, "APP_SERVER", "应用服务器", "SERVER"));
        when(bindingMapper.selectOne(any())).thenReturn(binding(7L, 101L, 201L, 3));
        ProjectServerConfigDO server = new ProjectServerConfigDO();
        server.setId(201L);
        server.setProjectId(11L);
        server.setStatus(DisEnableStatusEnum.ENABLE);
        when(serverMapper.selectById(201L)).thenReturn(server);

        ResolvedResource result = service.resolve(7L, 11L, "SERVER", Map
            .of("scope", "project_environment", "slot_id", 101L));

        assertThat(result.resourceCode()).isEqualTo("APP_SERVER");
        assertThat(result.resourceId()).isEqualTo(201L);
        assertThat(result.bindingVersion()).isEqualTo(3);
    }

    @Test
    void shouldRejectLegacyResourceOutsideSelectedEnvironment() {
        when(environmentMapper.selectById(7L)).thenReturn(environment(7L, 11L, "测试环境"));
        when(slotMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolve(7L, 11L, "SERVER", Map
            .of("scope", "project_config", "config_id", 999L))).hasMessageContaining("旧步骤目标不属于环境");
    }

    @Test
    void shouldRejectMissingCertificateBinding() {
        when(environmentMapper.selectById(7L)).thenReturn(environment(7L, 11L, "测试环境"));
        when(slotMapper.selectById(103L)).thenReturn(slot(103L, 11L, "CLIENT_LICENSE", "客户端证书", "CERTIFICATE"));
        when(bindingMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.resolve(7L, 11L, "CERTIFICATE", Map
            .of("scope", "project_environment", "slot_id", 103L))).hasMessageContaining("测试环境")
            .hasMessageContaining("客户端证书");
    }

    @Test
    void shouldCreateCustomDatabaseSlotForAdditionalDatabase() {
        when(slotMapper.selectList(any())).thenReturn(List
            .of(slot(101L, 11L, "APP_SERVER", "应用服务器", "SERVER"), slot(102L, 11L, "BUSINESS_DB", "业务数据库", "DATABASE"), slot(103L, 11L, "CUSTOM_DB_1", "自定义数据库1", "DATABASE"), slot(104L, 11L, "CLIENT_LICENSE", "客户端证书", "CERTIFICATE")));

        var result = service.createCustomDatabaseSlot(11L);

        assertThat(result.getResourceCode()).isEqualTo("CUSTOM_DB_2");
        assertThat(result.getResourceName()).isEqualTo("自定义数据库2");
        assertThat(result.getResourceKind()).isEqualTo("DATABASE");
        assertThat(result.getRequired()).isFalse();
        verify(slotMapper).insert(any(ProjectResourceSlotDO.class));
    }

    private ProjectEnvironmentConfigDO environment(Long id, Long projectId, String name) {
        ProjectEnvironmentConfigDO environment = new ProjectEnvironmentConfigDO();
        environment.setId(id);
        environment.setProjectId(projectId);
        environment.setName(name);
        environment.setStatus(DisEnableStatusEnum.ENABLE);
        return environment;
    }

    private ProjectResourceSlotDO slot(Long id, Long projectId, String code, String name, String kind) {
        ProjectResourceSlotDO slot = new ProjectResourceSlotDO();
        slot.setId(id);
        slot.setProjectId(projectId);
        slot.setResourceCode(code);
        slot.setResourceName(name);
        slot.setResourceKind(kind);
        slot.setStatus(DisEnableStatusEnum.ENABLE);
        return slot;
    }

    private ProjectEnvironmentResourceBindingDO binding(Long environmentId,
                                                        Long slotId,
                                                        Long resourceId,
                                                        Integer version) {
        ProjectEnvironmentResourceBindingDO binding = new ProjectEnvironmentResourceBindingDO();
        binding.setEnvironmentId(environmentId);
        binding.setResourceSlotId(slotId);
        binding.setResourceId(resourceId);
        binding.setBindingVersion(version);
        binding.setStatus(DisEnableStatusEnum.ENABLE);
        return binding;
    }
}
