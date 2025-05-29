package top.continew.admin.automation.service.impl;

import java.util.List;
import java.util.ArrayList;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.mapper.AutomationBrowserConfigMapper;
import top.continew.admin.automation.model.entity.AutomationBrowserConfigDO;
import top.continew.admin.automation.model.query.AutomationBrowserConfigQuery;
import top.continew.admin.automation.model.req.AutomationBrowserConfigReq;
import top.continew.admin.automation.model.resp.AutomationBrowserConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationBrowserConfigResp;
import top.continew.admin.automation.service.AutomationBrowserConfigService;

/**
 * 自动化管理-浏览器配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/29 15:41
 */
@Service
@RequiredArgsConstructor
public class AutomationBrowserConfigServiceImpl extends BaseServiceImpl<AutomationBrowserConfigMapper, AutomationBrowserConfigDO, AutomationBrowserConfigResp, AutomationBrowserConfigDetailResp, AutomationBrowserConfigQuery, AutomationBrowserConfigReq> implements AutomationBrowserConfigService {
    @Override
    public List<AutomationBrowserConfigDetailResp> selectByIds(List<Long> ids) {
        List<AutomationBrowserConfigDetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), AutomationBrowserConfigDetailResp.class);
        list.forEach(item -> {
            List<String> memberNames = new ArrayList<>();
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
                .eq(AutomationBrowserConfigDO::getType, param[0])
                .eq(AutomationBrowserConfigDO::getName, param[1])
                .eq(AutomationBrowserConfigDO::getVersion, param[2])
                .eq(AutomationBrowserConfigDO::getDelFlag, 3)
                .ne(null != id, AutomationBrowserConfigDO::getId, id)
                .exists();
    }
}