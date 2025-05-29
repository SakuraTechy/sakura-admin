package top.continew.admin.automation.service.impl;

import java.util.List;
import java.util.ArrayList;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.mapper.AutomationJenkinsConfigMapper;
import top.continew.admin.automation.model.entity.AutomationJenkinsConfigDO;
import top.continew.admin.automation.model.query.AutomationJenkinsConfigQuery;
import top.continew.admin.automation.model.req.AutomationJenkinsConfigReq;
import top.continew.admin.automation.model.resp.AutomationJenkinsConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationJenkinsConfigResp;
import top.continew.admin.automation.service.AutomationJenkinsConfigService;

/**
 * 自动化管理-Jenkins配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Service
@RequiredArgsConstructor
public class AutomationJenkinsConfigServiceImpl extends BaseServiceImpl<AutomationJenkinsConfigMapper, AutomationJenkinsConfigDO, AutomationJenkinsConfigResp, AutomationJenkinsConfigDetailResp, AutomationJenkinsConfigQuery, AutomationJenkinsConfigReq> implements AutomationJenkinsConfigService {
    @Override
    public List<AutomationJenkinsConfigDetailResp> selectByIds(List<Long> ids) {
        List<AutomationJenkinsConfigDetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), AutomationJenkinsConfigDetailResp.class);
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
                .eq(AutomationJenkinsConfigDO::getIp, param[0])
                .eq(AutomationJenkinsConfigDO::getPort, param[1])
                .eq(AutomationJenkinsConfigDO::getDelFlag, 3)
                .ne(null != id, AutomationJenkinsConfigDO::getId, id)
                .exists();
    }
}