package top.continew.admin.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;

/**
* 项目配置 Mapper
*
* @author hagyao520
* @since 2025/04/11 18:11
*/
@Mapper
public interface ProjectConfigMapper extends BaseMapper<ProjectConfigDO> {
    ProjectConfigDO getProjectConfigById(Long id);
}