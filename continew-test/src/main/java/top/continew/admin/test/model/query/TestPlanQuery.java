package top.continew.admin.test.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "测试计划查询条件")
public class TestPlanQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Query(type = QueryType.LIKE)
    private String id;

    @Query(type = QueryType.EQ)
    private Long projectId;

    @Query(type = QueryType.LIKE)
    private String name;

    @Query(type = QueryType.LIKE)
    private String type;

    @Query(type = QueryType.EQ)
    private String status;

    @Query(type = QueryType.EQ)
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
