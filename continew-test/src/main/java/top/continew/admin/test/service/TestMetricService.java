package top.continew.admin.test.service;

import top.continew.admin.test.model.query.TestMetricQuery;
import top.continew.admin.test.model.resp.TestMetricResp;

public interface TestMetricService {

    TestMetricResp getOverview(TestMetricQuery query);
}
