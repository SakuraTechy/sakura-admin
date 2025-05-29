package top.continew.admin.automation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.automation.model.entity.AutomationProjectConfigDO;

/**
* 自动化管理-项目配置 Mapper
*
* @author hagyao520
* @since 2025/05/19 15:14
*/
@Mapper
public interface AutomationProjectConfigMapper extends BaseMapper<AutomationProjectConfigDO> {
    AutomationProjectConfigDO getAutomationProjectConfigById(Long id);
}