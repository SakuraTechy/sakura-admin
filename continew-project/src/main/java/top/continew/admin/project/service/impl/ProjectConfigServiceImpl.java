package top.continew.admin.project.service.impl;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.query.ProjectConfigQuery;
import top.continew.admin.project.model.req.ProjectConfigReq;
import top.continew.admin.project.model.resp.ProjectConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectConfigResp;
import top.continew.admin.project.service.ProjectConfigService;

/**
 * 项目配置业务实现
 *
 * @author hagyao520
 * @since 2025/04/11 18:11
 */
@Service
@RequiredArgsConstructor
public class ProjectConfigServiceImpl extends BaseServiceImpl<ProjectConfigMapper, ProjectConfigDO, ProjectConfigResp, ProjectConfigDetailResp, ProjectConfigQuery, ProjectConfigReq> implements ProjectConfigService {}