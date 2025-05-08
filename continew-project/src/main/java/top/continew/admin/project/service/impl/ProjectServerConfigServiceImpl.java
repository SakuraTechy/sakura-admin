package top.continew.admin.project.service.impl;

import java.util.List;
import java.util.ArrayList;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectServerConfigMapper;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.admin.project.model.query.ProjectServerConfigQuery;
import top.continew.admin.project.model.req.ProjectServerConfigReq;
import top.continew.admin.project.model.resp.ProjectServerConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectServerConfigResp;
import top.continew.admin.project.service.ProjectServerConfigService;
import top.continew.admin.project.service.ProjectConfigService;

/**
 * 项目管理-服务器配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Service
@RequiredArgsConstructor
public class ProjectServerConfigServiceImpl extends BaseServiceImpl<ProjectServerConfigMapper, ProjectServerConfigDO, ProjectServerConfigResp, ProjectServerConfigDetailResp, ProjectServerConfigQuery, ProjectServerConfigReq> implements ProjectServerConfigService {

    private final ProjectConfigService projectConfigService;

    @Override
    public List<ProjectServerConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectServerConfigDetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), ProjectServerConfigDetailResp.class);
        for (ProjectServerConfigDetailResp item : list) {
            String projectName = projectConfigService.get(item.getProjectId()).getName();
            item.setProjectName(projectName);
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        }
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object param1, Object param2, Object... param3) {
        return baseMapper.lambdaQuery()
                .eq(ProjectServerConfigDO::getProjectId, param1)
                .eq(ProjectServerConfigDO::getIp, param2)
                .eq(ProjectServerConfigDO::getPort, param3)
                .eq(ProjectServerConfigDO::getDelFlag, 1)
                .ne(null != id, ProjectServerConfigDO::getId, id)
                .exists();
    }
}