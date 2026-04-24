package top.continew.admin.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.starter.data.mp.base.BaseMapper;

@Mapper
public interface TestReportMapper extends BaseMapper<TestReportDO> {
}
