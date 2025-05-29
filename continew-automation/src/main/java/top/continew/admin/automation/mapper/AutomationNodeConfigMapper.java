package top.continew.admin.automation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;

/**
* 自动化管理-节点配置 Mapper
*
* @author hagyao520
* @since 2025/05/20 11:21
*/
@Mapper
public interface AutomationNodeConfigMapper extends BaseMapper<AutomationNodeConfigDO> {
    AutomationNodeConfigDO getAutomationNodeConfigById(Long id);
}