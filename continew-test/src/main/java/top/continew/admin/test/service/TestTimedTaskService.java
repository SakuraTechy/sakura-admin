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

import top.continew.admin.test.model.query.TestTimedTaskQuery;
import top.continew.admin.test.model.query.TestTimedTaskLogQuery;
import top.continew.admin.test.model.query.TestTimedTaskRunQuery;
import top.continew.admin.test.model.req.TestTimedTaskReq;
import top.continew.admin.test.model.resp.TestTimedTaskDetailResp;
import top.continew.admin.test.model.resp.TestTimedTaskLogResp;
import top.continew.admin.test.model.resp.TestTimedTaskResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.extension.crud.service.BaseService;

import java.util.List;

public interface TestTimedTaskService extends BaseService<TestTimedTaskResp, TestTimedTaskDetailResp, TestTimedTaskQuery, TestTimedTaskReq> {

    @Override
    PageResp<TestTimedTaskResp> page(TestTimedTaskQuery query, PageQuery pageQuery);

    List<TestTimedTaskDetailResp> selectByIds(List<Long> ids);

    void deleteByIds(List<Long> ids);

    void deleteByPlanIds(List<Long> planIds);

    boolean isExists(String name, Long planId, Long id);

    void updateStatus(Long id, String status);

    void retrySync(Long id);

    void trigger(Long id);

    PageResp<TestTimedTaskLogResp> pageLogs(Long id, TestTimedTaskLogQuery query, PageQuery pageQuery);

    PageResp<TestTimedTaskRunResp> pageRuns(Long id, TestTimedTaskRunQuery query, PageQuery pageQuery);
}
