package top.continew.admin.automation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.automation.model.entity.AutomationEnvironmentConfigDO;

/**
* 自动化管理-环境配置 Mapper
*
* @author hagyao520
* @since 2025/05/29 17:41
*/
@Mapper
public interface AutomationEnvironmentConfigMapper extends BaseMapper<AutomationEnvironmentConfigDO> {
    AutomationEnvironmentConfigDO getAutomationEnvironmentConfigById(Long id);
}