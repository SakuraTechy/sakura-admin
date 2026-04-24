package top.continew.admin.test.service;

import top.continew.admin.test.model.query.TestTimedTaskQuery;
import top.continew.admin.test.model.req.TestTimedTaskReq;
import top.continew.admin.test.model.resp.TestTimedTaskDetailResp;
import top.continew.admin.test.model.resp.TestTimedTaskLogResp;
import top.continew.admin.test.model.resp.TestTimedTaskResp;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.extension.crud.service.BaseService;

import java.util.List;

public interface TestTimedTaskService extends BaseService<TestTimedTaskResp, TestTimedTaskDetailResp, TestTimedTaskQuery, TestTimedTaskReq> {

    List<TestTimedTaskDetailResp> selectByIds(List<Long> ids);

    void deleteByIds(List<Long> ids);

    boolean isExists(String name, Long planId, Long id);

    void updateStatus(Long id, String status);

    void trigger(Long id);

    PageResp<TestTimedTaskLogResp> pageLogs(Long id, Integer page, Integer size);
}
