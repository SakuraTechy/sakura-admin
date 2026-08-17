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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.mapper.AutomationFileAssetMapper;
import top.continew.admin.automation.model.entity.AutomationFileAssetDO;
import top.continew.admin.automation.model.resp.environment.AutomationEnvironmentResourceResp;
import top.continew.admin.automation.service.AutomationCertificateWorkspaceService;
import top.continew.admin.automation.service.AutomationEnvironmentResourceService;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.project.mapper.ProjectDataBaseConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentResourceBindingMapper;
import top.continew.admin.project.mapper.ProjectResourceSlotMapper;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.admin.project.model.entity.ProjectDataBaseConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentResourceBindingDO;
import top.continew.admin.project.model.entity.ProjectResourceSlotDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.starter.core.exception.BusinessException;

/** 项目环境资源绑定服务实现。 */
@Service
@RequiredArgsConstructor
public class AutomationEnvironmentResourceServiceImpl implements AutomationEnvironmentResourceService {

    private static final List<DefaultSlot> DEFAULT_SLOTS = List
        .of(new DefaultSlot("APP_SERVER", "应用服务器", SERVER), new DefaultSlot("BUSINESS_DB", "业务数据库", DATABASE), new DefaultSlot("CLIENT_LICENSE", "客户端证书", CERTIFICATE));

    private final ProjectResourceSlotMapper slotMapper;
    private final ProjectEnvironmentResourceBindingMapper bindingMapper;
    private final ProjectEnvironmentConfigMapper environmentMapper;
    private final ProjectServerConfigMapper serverMapper;
    private final ProjectDataBaseConfigMapper databaseMapper;
    private final AutomationFileAssetMapper fileAssetMapper;
    private final AutomationCertificateWorkspaceService certificateWorkspaceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AutomationEnvironmentResourceResp> listSlots(Long projectId, String kind) {
        requireProjectId(projectId);
        ensureDefaultSlots(projectId);
        String normalizedKind = normalizeKind(kind, false);
        return slotMapper.selectList(Wrappers.<ProjectResourceSlotDO>lambdaQuery()
            .eq(ProjectResourceSlotDO::getProjectId, projectId)
            .eq(normalizedKind != null, ProjectResourceSlotDO::getResourceKind, normalizedKind)
            .eq(ProjectResourceSlotDO::getStatus, DisEnableStatusEnum.ENABLE)
            .orderByAsc(ProjectResourceSlotDO::getResourceKind, ProjectResourceSlotDO::getResourceCode))
            .stream()
            .map(slot -> toResp(slot, null))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AutomationEnvironmentResourceResp> listEnvironmentResources(Long environmentId) {
        ProjectEnvironmentConfigDO environment = requireEnvironment(environmentId);
        ensureDefaultSlots(environment.getProjectId());
        ensureLegacyDefaultBindings(environment);
        List<ProjectResourceSlotDO> slots = slotMapper.selectList(Wrappers.<ProjectResourceSlotDO>lambdaQuery()
            .eq(ProjectResourceSlotDO::getProjectId, environment.getProjectId())
            .eq(ProjectResourceSlotDO::getStatus, DisEnableStatusEnum.ENABLE)
            .orderByAsc(ProjectResourceSlotDO::getResourceKind, ProjectResourceSlotDO::getResourceCode));
        Map<Long, ProjectEnvironmentResourceBindingDO> bindings = bindingMapper.selectList(Wrappers
            .<ProjectEnvironmentResourceBindingDO>lambdaQuery()
            .eq(ProjectEnvironmentResourceBindingDO::getEnvironmentId, environmentId)
            .eq(ProjectEnvironmentResourceBindingDO::getStatus, DisEnableStatusEnum.ENABLE))
            .stream()
            .collect(java.util.stream.Collectors
                .toMap(ProjectEnvironmentResourceBindingDO::getResourceSlotId, item -> item));
        return slots.stream().map(slot -> toResp(slot, bindings.get(slot.getId()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationEnvironmentResourceResp bind(Long environmentId, Long slotId, Long resourceId) {
        ProjectEnvironmentConfigDO environment = requireEnvironment(environmentId);
        ProjectResourceSlotDO slot = requireSlot(slotId, environment.getProjectId());
        validatePhysicalResource(environment.getProjectId(), slot.getResourceKind(), resourceId);
        upsertBinding(environmentId, slotId, resourceId);
        return toResp(slot, findBinding(environmentId, slotId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationEnvironmentResourceResp uploadCertificate(Long environmentId, Long slotId, MultipartFile file) {
        ProjectEnvironmentConfigDO environment = requireEnvironment(environmentId);
        ProjectResourceSlotDO slot = requireSlot(slotId, environment.getProjectId());
        if (!CERTIFICATE.equals(slot.getResourceKind())) {
            throw new BusinessException("当前资源角色不是证书类型");
        }
        AutomationCertificateWorkspaceService.CertificateAsset asset = certificateWorkspaceService
            .uploadAsset(environment.getProjectId(), environmentVersionName(environment), file);
        upsertBinding(environmentId, slotId, asset.assetId());
        return toResp(slot, findBinding(environmentId, slotId));
    }

    private String environmentVersionName(ProjectEnvironmentConfigDO environment) {
        if (environment.getVersionConfig() != null) {
            for (Object item : environment.getVersionConfig()) {
                if (item instanceof Map<?, ?> map) {
                    String name = firstText(map.get("name"), map.get("versionName"), map.get("version_name"));
                    if (!name.isBlank()) {
                        return name;
                    }
                }
            }
        }
        String lastVersion = text(environment.getLastVersion());
        if (!lastVersion.isBlank()) {
            return lastVersion;
        }
        throw new BusinessException("当前项目环境未配置版本，无法确定证书 License 目录");
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long environmentId, Long slotId) {
        ProjectEnvironmentConfigDO environment = requireEnvironment(environmentId);
        requireSlot(slotId, environment.getProjectId());
        bindingMapper.delete(Wrappers.<ProjectEnvironmentResourceBindingDO>lambdaQuery()
            .eq(ProjectEnvironmentResourceBindingDO::getEnvironmentId, environmentId)
            .eq(ProjectEnvironmentResourceBindingDO::getResourceSlotId, slotId));
    }

    @Override
    public ResolvedResource resolve(Long environmentId,
                                    Long projectId,
                                    String expectedKind,
                                    Map<String, Object> resourceRef) {
        ProjectEnvironmentConfigDO environment = requireEnvironment(environmentId);
        if (!Objects.equals(projectId, environment.getProjectId())) {
            throw new BusinessException("环境资源解析失败：环境不属于当前项目");
        }
        String kind = normalizeKind(expectedKind, true);
        Map<String, Object> ref = resourceRef == null ? Map.of() : resourceRef;
        String scope = text(ref.get("scope"));
        if ("project_environment".equals(scope)) {
            Long slotId = positiveLong(ref.get("slot_id"), "资源角色 ID");
            ProjectResourceSlotDO slot = requireSlot(slotId, projectId);
            if (!kind.equals(slot.getResourceKind())) {
                throw new BusinessException("环境资源解析失败：步骤资源类型与角色类型不一致");
            }
            ProjectEnvironmentResourceBindingDO binding = findBinding(environmentId, slotId);
            if (binding == null || binding.getStatus() != DisEnableStatusEnum.ENABLE) {
                throw new BusinessException("执行环境“" + environment.getName() + "未配置资源“" + slot
                    .getResourceName() + "”，请在项目管理-环境配置中绑定环境资源后再执行");
            }
            validatePhysicalResource(projectId, kind, binding.getResourceId());
            return new ResolvedResource(slotId, slot.getResourceCode(), kind, binding.getResourceId(), binding
                .getBindingVersion());
        }

        // 历史步骤只能在具体目标确实属于本次环境时继续执行，禁止跨环境静默复用 config_id。
        Long configId = nullablePositiveLong(ref.get("config_id"));
        if (configId == null && ref.get("binding_key") != null) {
            configId = legacyConfigIdByBindingKey(projectId, kind, text(ref.get("binding_key")));
        }
        if (configId == null) {
            throw new BusinessException("环境资源引用缺少 slot_id；请重新编辑步骤并选择资源角色");
        }
        if (!isLegacyResourceInEnvironment(environment, kind, configId)) {
            throw new BusinessException("旧步骤目标不属于环境“" + environment.getName() + "”，请迁移为环境资源角色");
        }
        validatePhysicalResource(projectId, kind, configId);
        return new ResolvedResource(null, "LEGACY_CONFIG", kind, configId, 0);
    }

    private void ensureDefaultSlots(Long projectId) {
        List<ProjectResourceSlotDO> existing = slotMapper.selectList(Wrappers.<ProjectResourceSlotDO>lambdaQuery()
            .eq(ProjectResourceSlotDO::getProjectId, projectId));
        for (DefaultSlot spec : DEFAULT_SLOTS) {
            if (existing.stream().anyMatch(item -> spec.code().equals(item.getResourceCode()))) {
                continue;
            }
            ProjectResourceSlotDO slot = new ProjectResourceSlotDO();
            slot.setProjectId(projectId);
            slot.setResourceCode(spec.code());
            slot.setResourceName(spec.name());
            slot.setResourceKind(spec.kind());
            slot.setRequired(1);
            slot.setStatus(DisEnableStatusEnum.ENABLE);
            try {
                slotMapper.insert(slot);
            } catch (DuplicateKeyException ignored) {
                // 并发首次读取可能同时初始化默认角色，唯一键保证只保留一份。
            }
        }
    }

    private void ensureLegacyDefaultBindings(ProjectEnvironmentConfigDO environment) {
        Map<String, ProjectResourceSlotDO> defaults = slotMapper.selectList(Wrappers
            .<ProjectResourceSlotDO>lambdaQuery()
            .eq(ProjectResourceSlotDO::getProjectId, environment.getProjectId()))
            .stream()
            .collect(java.util.stream.Collectors.toMap(ProjectResourceSlotDO::getResourceCode, item -> item));
        bindFirstLegacyResource(environment, defaults.get("APP_SERVER"), environment
            .getServerConfig(), ProjectServerConfigDO.class);
        bindFirstLegacyResource(environment, defaults.get("BUSINESS_DB"), environment
            .getDataBaseConfig(), ProjectDataBaseConfigDO.class);
    }

    private <T> void bindFirstLegacyResource(ProjectEnvironmentConfigDO environment,
                                             ProjectResourceSlotDO slot,
                                             List<?> snapshots,
                                             Class<T> type) {
        if (slot == null || findBinding(environment.getId(), slot.getId()) != null || snapshots == null || snapshots
            .isEmpty()) {
            return;
        }
        T snapshot = BeanUtil.toBean(snapshots.get(0), type);
        Long id = snapshot == null ? null : BeanUtil.getProperty(snapshot, "id");
        if (id == null) {
            return;
        }
        try {
            validatePhysicalResource(environment.getProjectId(), slot.getResourceKind(), id);
            upsertBinding(environment.getId(), slot.getId(), id);
        } catch (BusinessException ignored) {
            // 旧快照已失效时保持未绑定，由环境页面明确提示用户重新选择。
        }
    }

    private void upsertBinding(Long environmentId, Long slotId, Long resourceId) {
        ProjectEnvironmentResourceBindingDO binding = findBinding(environmentId, slotId);
        if (binding == null) {
            binding = new ProjectEnvironmentResourceBindingDO();
            binding.setEnvironmentId(environmentId);
            binding.setResourceSlotId(slotId);
            binding.setResourceId(resourceId);
            binding.setBindingVersion(1);
            binding.setStatus(DisEnableStatusEnum.ENABLE);
            bindingMapper.insert(binding);
            return;
        }
        binding.setResourceId(resourceId);
        binding.setBindingVersion((binding.getBindingVersion() == null ? 0 : binding.getBindingVersion()) + 1);
        binding.setStatus(DisEnableStatusEnum.ENABLE);
        bindingMapper.updateById(binding);
    }

    private ProjectEnvironmentResourceBindingDO findBinding(Long environmentId, Long slotId) {
        return bindingMapper.selectOne(Wrappers.<ProjectEnvironmentResourceBindingDO>lambdaQuery()
            .eq(ProjectEnvironmentResourceBindingDO::getEnvironmentId, environmentId)
            .eq(ProjectEnvironmentResourceBindingDO::getResourceSlotId, slotId)
            .last("LIMIT 1"));
    }

    private AutomationEnvironmentResourceResp toResp(ProjectResourceSlotDO slot,
                                                     ProjectEnvironmentResourceBindingDO binding) {
        AutomationEnvironmentResourceResp.AutomationEnvironmentResourceRespBuilder builder = AutomationEnvironmentResourceResp
            .builder()
            .slotId(slot.getId())
            .resourceCode(slot.getResourceCode())
            .resourceName(slot.getResourceName())
            .resourceKind(slot.getResourceKind())
            .required(Objects.equals(1, slot.getRequired()))
            .bound(binding != null)
            .resourceId(binding == null ? null : binding.getResourceId())
            .bindingVersion(binding == null ? null : binding.getBindingVersion());
        if (binding == null) {
            return builder.build();
        }
        if (SERVER.equals(slot.getResourceKind())) {
            ProjectServerConfigDO server = serverMapper.selectById(binding.getResourceId());
            return builder.resourceLabel(server == null ? "配置已失效" : address(server.getIp(), server.getPort(), null))
                .build();
        }
        if (DATABASE.equals(slot.getResourceKind())) {
            ProjectDataBaseConfigDO database = databaseMapper.selectById(binding.getResourceId());
            return builder.resourceLabel(database == null
                ? "配置已失效"
                : address(database.getIp(), database.getPort(), database.getDataBase())).build();
        }
        AutomationFileAssetDO asset = fileAssetMapper.selectById(binding.getResourceId());
        return builder.resourceLabel(asset == null ? "证书已失效" : asset.getOriginalName())
            .fileName(asset == null ? null : asset.getOriginalName())
            .fileSize(asset == null ? null : asset.getSize())
            .sha256(asset == null ? null : asset.getSha256())
            .build();
    }

    private ProjectEnvironmentConfigDO requireEnvironment(Long environmentId) {
        ProjectEnvironmentConfigDO environment = environmentId == null
            ? null
            : environmentMapper.selectById(environmentId);
        if (environment == null || environment.getStatus() != DisEnableStatusEnum.ENABLE) {
            throw new BusinessException("项目环境不存在或未启用");
        }
        return environment;
    }

    private ProjectResourceSlotDO requireSlot(Long slotId, Long projectId) {
        ProjectResourceSlotDO slot = slotId == null ? null : slotMapper.selectById(slotId);
        if (slot == null || !Objects.equals(projectId, slot.getProjectId()) || slot
            .getStatus() != DisEnableStatusEnum.ENABLE) {
            throw new BusinessException("资源角色不存在、不属于当前项目或未启用");
        }
        return slot;
    }

    private void validatePhysicalResource(Long projectId, String kind, Long resourceId) {
        if (resourceId == null) {
            throw new BusinessException("资源 ID 不能为空");
        }
        if (SERVER.equals(kind)) {
            ProjectServerConfigDO resource = serverMapper.selectById(resourceId);
            if (resource == null || !Objects.equals(projectId, resource.getProjectId()) || resource
                .getStatus() != DisEnableStatusEnum.ENABLE) {
                throw new BusinessException("服务器资源不存在、不属于当前项目或未启用");
            }
            return;
        }
        if (DATABASE.equals(kind)) {
            ProjectDataBaseConfigDO resource = databaseMapper.selectById(resourceId);
            if (resource == null || !Objects.equals(projectId, resource.getProjectId()) || resource
                .getStatus() != DisEnableStatusEnum.ENABLE) {
                throw new BusinessException("数据库资源不存在、不属于当前项目或未启用");
            }
            return;
        }
        AutomationFileAssetDO asset = fileAssetMapper.selectById(resourceId);
        if (asset == null || !Objects.equals(projectId, asset.getProjectId()) || !"CERTIFICATE".equals(asset
            .getAssetKind()) || !"ACTIVE".equals(asset.getStatus())) {
            throw new BusinessException("证书资产不存在、不属于当前项目或已停用");
        }
    }

    private boolean isLegacyResourceInEnvironment(ProjectEnvironmentConfigDO environment,
                                                  String kind,
                                                  Long resourceId) {
        List<?> snapshots = SERVER.equals(kind) ? environment.getServerConfig() : environment.getDataBaseConfig();
        if (snapshots != null && snapshots.stream()
            .anyMatch(item -> Objects.equals(resourceId, BeanUtil.getProperty(item, "id")))) {
            return true;
        }
        List<Long> slotIds = slotMapper.selectList(Wrappers.<ProjectResourceSlotDO>lambdaQuery()
            .eq(ProjectResourceSlotDO::getProjectId, environment.getProjectId())
            .eq(ProjectResourceSlotDO::getResourceKind, kind)).stream().map(ProjectResourceSlotDO::getId).toList();
        return !slotIds.isEmpty() && bindingMapper.selectCount(Wrappers
            .<ProjectEnvironmentResourceBindingDO>lambdaQuery()
            .eq(ProjectEnvironmentResourceBindingDO::getEnvironmentId, environment.getId())
            .in(ProjectEnvironmentResourceBindingDO::getResourceSlotId, slotIds)
            .eq(ProjectEnvironmentResourceBindingDO::getResourceId, resourceId)
            .eq(ProjectEnvironmentResourceBindingDO::getStatus, DisEnableStatusEnum.ENABLE)) > 0;
    }

    private Long legacyConfigIdByBindingKey(Long projectId, String kind, String bindingKey) {
        if (bindingKey.isBlank()) {
            return null;
        }
        if (SERVER.equals(kind)) {
            List<ProjectServerConfigDO> matches = serverMapper.selectList(Wrappers.<ProjectServerConfigDO>lambdaQuery()
                .eq(ProjectServerConfigDO::getProjectId, projectId)
                .eq(ProjectServerConfigDO::getBindingKey, bindingKey));
            return matches.size() == 1 ? matches.get(0).getId() : null;
        }
        List<ProjectDataBaseConfigDO> matches = databaseMapper.selectList(Wrappers
            .<ProjectDataBaseConfigDO>lambdaQuery()
            .eq(ProjectDataBaseConfigDO::getProjectId, projectId)
            .eq(ProjectDataBaseConfigDO::getBindingKey, bindingKey));
        return matches.size() == 1 ? matches.get(0).getId() : null;
    }

    private String normalizeKind(String kind, boolean required) {
        String normalized = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() && !required) {
            return null;
        }
        if (!List.of(SERVER, DATABASE, CERTIFICATE).contains(normalized)) {
            throw new BusinessException("资源类型仅支持 SERVER、DATABASE 或 CERTIFICATE");
        }
        return normalized;
    }

    private Long positiveLong(Object value, String name) {
        Long result = nullablePositiveLong(value);
        if (result == null) {
            throw new BusinessException(name + "必须为正数");
        }
        return result;
    }

    private Long nullablePositiveLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            long result = Long.parseLong(String.valueOf(value));
            return result > 0 ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException("项目 ID 不能为空");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String address(String ip, Integer port, String database) {
        String result = (ip == null ? "" : ip) + (port == null ? "" : ":" + port);
        return database == null || database.isBlank() ? result : result + "/" + database;
    }

    private record DefaultSlot(String code, String name, String kind) {
    }
}
