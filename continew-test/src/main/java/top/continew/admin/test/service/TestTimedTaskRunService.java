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

import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.model.query.TestTimedTaskRunQuery;
import top.continew.admin.test.model.resp.TestPlanExecuteResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunResp;
import top.continew.admin.test.model.resp.TestTimedTaskRunSummaryResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.Collection;
import java.util.Map;

public interface TestTimedTaskRunService {

    StartResult start(Long taskId, String triggerMode);

    void attachExecution(Long runId, TestPlanExecuteResp executeResp);

    void fail(Long runId, String reason);

    void completeByReport(TestReportDO report);

    PageResp<TestTimedTaskRunResp> page(Long taskId, TestTimedTaskRunQuery query, PageQuery pageQuery);

    Map<Long, TestTimedTaskRunSummaryResp> latestByTaskIds(Collection<Long> taskIds);

    record StartResult(TestTimedTaskRunDO run, boolean skipped) {
    }
}
