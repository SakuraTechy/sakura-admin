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

import top.continew.admin.test.model.query.TestMetricScopeQuery;
import top.continew.admin.test.model.resp.TestMetricBreakdownResp;
import top.continew.admin.test.model.resp.TestMetricFailureResp;
import top.continew.admin.test.model.resp.TestMetricSummaryResp;
import top.continew.admin.test.model.resp.TestMetricTrendResp;

public interface TestMetricQueryService {

    TestMetricSummaryResp getSummary(TestMetricScopeQuery query);

    TestMetricTrendResp getTrends(TestMetricScopeQuery query);

    TestMetricBreakdownResp getBreakdown(TestMetricScopeQuery query, String dimension);

    TestMetricFailureResp getFailures(TestMetricScopeQuery query, Integer limit);
}
