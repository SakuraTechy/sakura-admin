package top.continew.admin.automation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.automation.model.entity.AutomationJenkinsConfigDO;

/**
* 自动化管理-Jenkins配置 Mapper
*
* @author hagyao520
* @since 2025/05/19 16:59
*/
@Mapper
public interface AutomationJenkinsConfigMapper extends BaseMapper<AutomationJenkinsConfigDO> {
    AutomationJenkinsConfigDO getAutomationJenkinsConfigById(Long id);
}