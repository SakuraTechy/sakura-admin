package top.continew.admin.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.starter.data.mp.base.BaseMapper;

@Mapper
public interface TestTimedTaskMapper extends BaseMapper<TestTimedTaskDO> {
}
