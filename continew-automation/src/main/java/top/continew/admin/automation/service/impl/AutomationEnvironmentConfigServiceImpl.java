package top.continew.admin.automation.service.impl;

import java.util.List;
import java.util.Objects;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.admin.automation.mapper.*;
import top.continew.admin.automation.model.entity.*;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.model.query.AutomationEnvironmentConfigQuery;
import top.continew.admin.automation.model.req.AutomationEnvironmentConfigReq;
import top.continew.admin.automation.model.resp.AutomationEnvironmentConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationEnvironmentConfigResp;
import top.continew.admin.automation.service.AutomationEnvironmentConfigService;

/**
 * 自动化管理-环境配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Service
@RequiredArgsConstructor
public class AutomationEnvironmentConfigServiceImpl extends BaseServiceImpl<AutomationEnvironmentConfigMapper, AutomationEnvironmentConfigDO, AutomationEnvironmentConfigResp, AutomationEnvironmentConfigDetailResp, AutomationEnvironmentConfigQuery, AutomationEnvironmentConfigReq> implements AutomationEnvironmentConfigService {

    private final AutomationProjectConfigMapper automationProjectConfigMapper;
    private final AutomationJenkinsConfigMapper automationJenkinsConfigMapper;
    private final AutomationNodeConfigMapper automationNodeConfigMapper;
    private final AutomationBrowserConfigMapper automationBrowserConfigMapper;

    @Override
    public List<AutomationEnvironmentConfigDetailResp> selectByIds(List<Long> ids) {
        List<AutomationEnvironmentConfigDetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), AutomationEnvironmentConfigDetailResp.class);
        list.forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        });
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
                .eq(AutomationEnvironmentConfigDO::getType, param[0])
                .eq(AutomationEnvironmentConfigDO::getName, param[1])
                .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                .ne(null != id, AutomationEnvironmentConfigDO::getId, id)
                .exists();
    }

    @Override
    public boolean updateProjectConfig(String type, Long id) {
        try  {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                    .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                    .list();
            AutomationProjectConfigDO automationProjectConfigDO = automationProjectConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationProjectConfigDO> projectConfigList = automationEnvironmentConfigDO.getProjectConfig();
                if(type.equals("delete")){
                    projectConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                }else{
                    for (int i = 0; i < projectConfigList.size(); i++) {
                        if (Objects.equals(projectConfigList.get(i).getId(), id)) {
                            projectConfigList.set(i, automationProjectConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setProjectConfig(projectConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
                return true;
            }
        } catch (Exception e){
            log.error("更新环境项目配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateJenkinsConfig(String type, Long id) {
        try  {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                    .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                    .list();
            AutomationJenkinsConfigDO automationJenkinsConfigDO = automationJenkinsConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationJenkinsConfigDO> jenkinsConfigList = automationEnvironmentConfigDO.getJenkinsConfig();
                if(type.equals("delete")){
                    jenkinsConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                }else{
                    for (int i = 0; i < jenkinsConfigList.size(); i++) {
                        if (Objects.equals(jenkinsConfigList.get(i).getId(), id)) {
                            jenkinsConfigList.set(i, automationJenkinsConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setJenkinsConfig(jenkinsConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
                return true;
            }
        } catch (Exception e) {
            log.error("更新环境Jenkins配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateNodeConfig(String type, Long id) {
        try  {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                    .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                    .list();
            AutomationNodeConfigDO automationNodeConfigDO = automationNodeConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationNodeConfigDO> nodeConfigList = automationEnvironmentConfigDO.getNodeConfig();
                if(type.equals("delete")){
                    nodeConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                }else{
                    for (int i = 0; i < nodeConfigList.size(); i++) {
                        if (Objects.equals(nodeConfigList.get(i).getId(), id)) {
                            nodeConfigList.set(i, automationNodeConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setNodeConfig(nodeConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
                return true;
            }
        } catch (Exception e) {
            log.error("更新环境节点配置失败，{}", e);
        }
        return false;
    }

    @Override
    public boolean updateBrowserConfig(String type, Long id) {
        try  {
            List<AutomationEnvironmentConfigDO> automationEnvironmentConfigDOList = baseMapper.lambdaQuery()
                    .eq(AutomationEnvironmentConfigDO::getDelFlag, 3)
                    .list();
            AutomationBrowserConfigDO automationBrowserConfigDO = automationBrowserConfigMapper.selectById(id);
            for (AutomationEnvironmentConfigDO automationEnvironmentConfigDO : automationEnvironmentConfigDOList) {
                List<AutomationBrowserConfigDO> browserConfigList = automationEnvironmentConfigDO.getBrowserConfig();
                if(type.equals("delete")){
                    browserConfigList.removeIf(a -> Objects.equals(a.getId(), id));
                }else{
                    for (int i = 0; i < browserConfigList.size(); i++) {
                        if (Objects.equals(browserConfigList.get(i).getId(), id)) {
                            browserConfigList.set(i, automationBrowserConfigDO);
                            break;
                        }
                    }
                }
                automationEnvironmentConfigDO.setBrowserConfig(browserConfigList);
                baseMapper.updateById(automationEnvironmentConfigDO);
                return true;
            }
        } catch (Exception e) {
            log.error("更新环境浏览器配置失败，{}", e);
        }
        return false;
    }
}