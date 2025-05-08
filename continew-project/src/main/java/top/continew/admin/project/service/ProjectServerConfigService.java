package top.continew.admin.project.service;

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.project.model.query.ProjectServerConfigQuery;
import top.continew.admin.project.model.req.ProjectServerConfigReq;
import top.continew.admin.project.model.resp.ProjectServerConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectServerConfigResp;

/**
 * 项目管理-服务器配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
public interface ProjectServerConfigService extends BaseService<ProjectServerConfigResp, ProjectServerConfigDetailResp, ProjectServerConfigQuery, ProjectServerConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<ProjectServerConfigDetailResp> selectByIds(List<Long> ids);

    /**
     * 根据 ID 删除
     *
     * @param ids ID 列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据参数一和参数二，判断项目是否存在
     *
     * @param param1 项目名称
     * @param param2 项目简称
     * @param id   ID
     * @return true：存在；false：不存在
     */
    boolean isExists(Long id, Object param1, Object param2, Object... param3);
}