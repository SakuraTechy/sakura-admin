package top.continew.admin.project.controller;

import top.continew.starter.extension.crud.enums.Api;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;

import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.admin.common.controller.BaseController;
import top.continew.admin.project.model.query.ProjectConfigQuery;
import top.continew.admin.project.model.req.ProjectConfigReq;
import top.continew.admin.project.model.resp.ProjectConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectConfigResp;
import top.continew.admin.project.service.ProjectConfigService;

/**
 * 项目配置管理 API
 *
 * @author hagyao520
 * @since 2025/04/11 18:11
 */
@Tag(name = "项目配置管理 API")
@RestController
@CrudRequestMapping(value = "/project/projectConfig", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class ProjectConfigController extends BaseController<ProjectConfigService, ProjectConfigResp, ProjectConfigDetailResp, ProjectConfigQuery, ProjectConfigReq> {}