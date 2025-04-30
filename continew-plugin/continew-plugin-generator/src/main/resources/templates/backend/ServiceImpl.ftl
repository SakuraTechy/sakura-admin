package ${packageName}.${subPackageName};

import java.util.List;
import java.util.ArrayList;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import ${packageName}.mapper.${classNamePrefix}Mapper;
import ${packageName}.model.entity.${classNamePrefix}DO;
import ${packageName}.model.query.${classNamePrefix}Query;
import ${packageName}.model.req.${classNamePrefix}Req;
import ${packageName}.model.resp.${classNamePrefix}DetailResp;
import ${packageName}.model.resp.${classNamePrefix}Resp;
import ${packageName}.service.${classNamePrefix}Service;

/**
 * ${businessName}业务实现
 *
 * @author ${author}
 * @since ${datetime}
 */
@Service
@RequiredArgsConstructor
public class ${className} extends BaseServiceImpl<${classNamePrefix}Mapper, ${classNamePrefix}DO, ${classNamePrefix}Resp, ${classNamePrefix}DetailResp, ${classNamePrefix}Query, ${classNamePrefix}Req> implements ${classNamePrefix}Service {
    @Override
    public List<${classNamePrefix}DetailResp> selectByIds(List<Long> ids) {
        List<${classNamePrefix}DetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), ${classNamePrefix}DetailResp.class);
        list.forEach(item -> {
            List<String> memberNames = new ArrayList<>();
            item.getMembers().forEach(memberId -> {
//                String memberName1 = ExceptionUtils.exToNull(() -> SpringUtil.getBean(CommonUserService.class).getNicknameById(Long.valueOf(memberId)));
                String memberName = UserContextHolder.getNickname(Long.valueOf(memberId));
                memberNames.add(memberName);
            });
            item.setMembers(memberNames);
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
    public boolean isExists(Object param1, Object param2, Long id) {
        return baseMapper.lambdaQuery()
                .eq(${classNamePrefix}DO::getName, param1)
                .eq(${classNamePrefix}DO::getAbbreviate, param2)
                .eq(${classNamePrefix}DO::getDelFlag, 1)
                .ne(null != id, ${classNamePrefix}DO::getId, id)
                .exists();
    }
}