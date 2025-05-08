package top.continew.admin.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;

/**
* 项目管理-服务器配置 Mapper
*
* @author hagyao520
* @since 2025/05/06 15:09
*/
@Mapper
public interface ProjectServerConfigMapper extends BaseMapper<ProjectServerConfigDO> {
    ProjectServerConfigDO getProjectServerConfigById(Long id);
}