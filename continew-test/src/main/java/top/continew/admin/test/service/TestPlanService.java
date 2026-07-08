/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
