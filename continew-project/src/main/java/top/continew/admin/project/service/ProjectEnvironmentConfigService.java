package top.continew.admin.project.service;

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.project.model.query.ProjectEnvironmentConfigQuery;
import top.continew.admin.project.model.req.ProjectEnvironmentConfigReq;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectEnvironmentConfigResp;

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
}