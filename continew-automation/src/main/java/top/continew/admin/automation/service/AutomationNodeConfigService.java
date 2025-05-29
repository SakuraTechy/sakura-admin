package top.continew.admin.automation.service;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.automation.model.query.AutomationNodeConfigQuery;
import top.continew.admin.automation.model.req.AutomationNodeConfigReq;
import top.continew.admin.automation.model.resp.AutomationNodeConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationNodeConfigResp;

/**
 * 自动化管理-节点配置业务接口
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
public interface AutomationNodeConfigService extends BaseService<AutomationNodeConfigResp, AutomationNodeConfigDetailResp, AutomationNodeConfigQuery, AutomationNodeConfigReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<AutomationNodeConfigDetailResp> selectByIds(List<Long> ids);

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

    /**
     * 同步所有节点配置信息
     * @param jenkinsId 所属Jenkins
     * @return 结果
     */
    boolean syncAllNode(Long jenkinsId);

    /**
     * 同步单个节点配置信息
     * @param ids ID 列表
     * @return 结果
     */
    boolean syncNode(List<Long> ids);

    /**
     * 添加节点配置信息
     * @param automationNodeConfigDO 节点配置信息
     * @return 节点配置信息
     */
    boolean addNode(AutomationNodeConfigDO automationNodeConfigDO);

    /**
     * 更新节点配置信息
     * @param automationNodeConfigDO 节点配置信息
     * @return 节点配置信息
     */
    boolean updateNode(AutomationNodeConfigDO automationNodeConfigDO);

    /**
     * 删除节点配置信息
     * @param automationNodeConfigDetailResp 节点配置详情信息
     * @return 节点配置信息
     */
    boolean delNode(AutomationNodeConfigDetailResp automationNodeConfigDetailResp);
}