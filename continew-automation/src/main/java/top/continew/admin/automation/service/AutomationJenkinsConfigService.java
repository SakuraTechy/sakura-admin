package top.continew.admin.automation.service;

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.automation.model.query.AutomationJenkinsConfigQuery;
import top.continew.admin.automation.model.req.AutomationJenkinsConfigReq;
import top.continew.admin.automation.model.resp.AutomationJenkinsConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationJenkinsConfigResp;

/**
 * 自动化管理-Jenkins配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
public interface AutomationJenkinsConfigService extends BaseService<AutomationJenkinsConfigResp, AutomationJenkinsConfigDetailResp, AutomationJenkinsConfigQuery, AutomationJenkinsConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<AutomationJenkinsConfigDetailResp> selectByIds(List<Long> ids);

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