package top.continew.admin.test.service;

import top.continew.admin.test.model.query.TestReportQuery;
import top.continew.admin.test.model.req.TestReportReq;
import top.continew.admin.test.model.req.TestReportUploadReq;
import top.continew.admin.test.model.resp.TestReportDetailResp;
import top.continew.admin.test.model.resp.TestReportResp;
import top.continew.starter.extension.crud.service.BaseService;

import java.util.List;

public interface TestReportService extends BaseService<TestReportResp, TestReportDetailResp, TestReportQuery, TestReportReq> {

    List<TestReportDetailResp> selectByIds(List<Long> ids);

    void deleteByIds(List<Long> ids);

    boolean isExists(String name, Long projectId, Long id);

    void uploadResult(TestReportUploadReq req);
}
