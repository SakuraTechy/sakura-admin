package top.continew.admin.automation.service;

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.automation.model.query.AutomationProjectConfigQuery;
import top.continew.admin.automation.model.req.AutomationProjectConfigReq;
import top.continew.admin.automation.model.resp.AutomationProjectConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationProjectConfigResp;

/**
 * 自动化管理-项目配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
public interface AutomationProjectConfigService extends BaseService<AutomationProjectConfigResp, AutomationProjectConfigDetailResp, AutomationProjectConfigQuery, AutomationProjectConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<AutomationProjectConfigDetailResp> selectByIds(List<Long> ids);

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