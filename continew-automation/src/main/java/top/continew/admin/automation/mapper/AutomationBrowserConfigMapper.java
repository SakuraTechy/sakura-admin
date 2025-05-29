package top.continew.admin.automation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.automation.model.entity.AutomationBrowserConfigDO;

/**
* 自动化管理-浏览器配置 Mapper
*
* @author hagyao520
* @since 2025/05/29 15:41
*/
@Mapper
public interface AutomationBrowserConfigMapper extends BaseMapper<AutomationBrowserConfigDO> {
    AutomationBrowserConfigDO getAutomationBrowserConfigById(Long id);
}