package top.continew.admin.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;

/**
* 项目管理-版本配置 Mapper
*
* @author hagyao520
* @since 2025/04/28 15:33
*/
@Mapper
public interface ProjectVersionConfigMapper extends BaseMapper<ProjectVersionConfigDO> {
    ProjectVersionConfigDO getProjectVersionConfigById(Long id);
}