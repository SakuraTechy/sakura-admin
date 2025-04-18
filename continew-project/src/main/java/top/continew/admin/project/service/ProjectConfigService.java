package top.continew.admin.project.service;

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
public interface ProjectConfigService extends BaseService<ProjectConfigResp, ProjectConfigDetailResp, ProjectConfigQuery, ProjectConfigReq> {}