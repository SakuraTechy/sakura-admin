package top.continew.admin.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;

/**
* 项目管理-环境配置 Mapper
*
* @author hagyao520
* @since 2025/05/15 09:47
*/
@Mapper
public interface ProjectEnvironmentConfigMapper extends BaseMapper<ProjectEnvironmentConfigDO> {
    ProjectEnvironmentConfigDO getProjectEnvironmentConfigById(Long id);
}