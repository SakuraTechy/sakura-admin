package ${packageName}.${subPackageName};

import java.util.List;

import top.continew.starter.extension.crud.service.BaseService;
import ${packageName}.model.query.${classNamePrefix}Query;
import ${packageName}.model.req.${classNamePrefix}Req;
import ${packageName}.model.resp.${classNamePrefix}DetailResp;
import ${packageName}.model.resp.${classNamePrefix}Resp;

/**
 * ${businessName}业务接口
 *
 * @author ${author}
 * @since ${datetime}
 */
public interface ${className} extends BaseService<${classNamePrefix}Resp, ${classNamePrefix}DetailResp, ${classNamePrefix}Query, ${classNamePrefix}Req> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<${classNamePrefix}DetailResp> selectByIds(List<Long> ids);

    /**
     * 根据 ID 删除
     *
     * @param ids ID 列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据参数一和参数二，判断项目是否存在
     *
     * @param param1 项目名称
     * @param param2 项目简称
     * @param id   ID
     * @return true：存在；false：不存在
     */
    boolean isExists(Long id, Object param1, Object param2, Object... param3);
}