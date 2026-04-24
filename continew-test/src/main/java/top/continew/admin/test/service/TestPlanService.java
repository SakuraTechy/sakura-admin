package top.continew.admin.test.service;

import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.test.model.query.TestPlanQuery;
import top.continew.admin.test.model.req.TestPlanExecuteReq;
import top.continew.admin.test.model.req.TestPlanReq;
import top.continew.admin.test.model.req.TestPlanSceneRelationReq;
import top.continew.admin.test.model.resp.TestPlanDetailResp;
import top.continew.admin.test.model.resp.TestPlanResp;
import top.continew.starter.extension.crud.service.BaseService;

import java.util.List;

public interface TestPlanService extends BaseService<TestPlanResp, TestPlanDetailResp, TestPlanQuery, TestPlanReq> {

    List<TestPlanDetailResp> selectByIds(List<Long> ids);

    void deleteByIds(List<Long> ids);

    boolean isExists(String name, Long projectId, Long id);

    void relateScenes(Long id, TestPlanSceneRelationReq req);

    void removeScenes(Long id, TestPlanSceneRelationReq req);

    AutomationUiSceneExecResp execute(Long id, TestPlanExecuteReq req);
}
