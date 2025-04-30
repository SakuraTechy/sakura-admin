package top.continew.admin.project.service.impl;

import java.util.List;
import java.util.ArrayList;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.admin.project.model.query.ProjectVersionConfigQuery;
import top.continew.admin.project.model.req.ProjectVersionConfigReq;
import top.continew.admin.project.model.resp.ProjectVersionConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectVersionConfigResp;
import top.continew.admin.project.service.ProjectVersionConfigService;

/**
 * 项目管理-版本配置业务实现
 *
 * @author hagyao520
 * @since 2025/04/28 15:33
 */
@Service
@RequiredArgsConstructor
public class ProjectVersionConfigServiceImpl extends BaseServiceImpl<ProjectVersionConfigMapper, ProjectVersionConfigDO, ProjectVersionConfigResp, ProjectVersionConfigDetailResp, ProjectVersionConfigQuery, ProjectVersionConfigReq> implements ProjectVersionConfigService {

    private final ProjectConfigService projectConfigService;

    @Override
    public List<ProjectVersionConfigDetailResp> selectByIds(List<Long> ids) {
        List<ProjectVersionConfigDetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), ProjectVersionConfigDetailResp.class);
        for (ProjectVersionConfigDetailResp item : list) {
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
    public boolean isExists(Object param1, Object param2, Long id) {
        return baseMapper.lambdaQuery()
                .eq(ProjectVersionConfigDO::getName, param1)
                .eq(ProjectVersionConfigDO::getProjectId, param2)
                .eq(ProjectVersionConfigDO::getDelFlag, 1)
                .ne(null != id, ProjectVersionConfigDO::getId, id)
                .exists();
    }
}