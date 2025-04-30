package top.continew.admin.project.service;

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.project.model.query.ProjectVersionConfigQuery;
import top.continew.admin.project.model.req.ProjectVersionConfigReq;
import top.continew.admin.project.model.resp.ProjectVersionConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectVersionConfigResp;

/**
 * 项目管理-版本配置业务接口
 *
 * @author hagyao520
 * @since 2025/04/28 15:33
 */
public interface ProjectVersionConfigService extends BaseService<ProjectVersionConfigResp, ProjectVersionConfigDetailResp, ProjectVersionConfigQuery, ProjectVersionConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<ProjectVersionConfigDetailResp> selectByIds(List<Long> ids);

    /**
     * 根据 ID 删除
     *
     * @param ids ID 列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据参数一和参数二，判断项目是否存在
     *
     * @param param1 参数一
     * @param param2 参数二
     * @param id   ID
     * @return true：存在；false：不存在
     */
    boolean isExists(Object param1, Object param2, Long id);
}