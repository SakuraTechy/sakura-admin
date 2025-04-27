package ${packageName}.${subPackageName};

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import top.continew.starter.data.mp.base.BaseMapper;
import ${packageName}.model.entity.${classNamePrefix}DO;

/**
* ${businessName} Mapper
*
* @author ${author}
* @since ${datetime}
*/
@Mapper
public interface ${className} extends BaseMapper<${classNamePrefix}DO> {
    List<${classNamePrefix}DO> get${classNamePrefix}ById(Long id);
}