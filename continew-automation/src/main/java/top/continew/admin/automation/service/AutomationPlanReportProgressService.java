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

package top.continew.admin.automation.service;

/**
 * 测试计划报告进度回调。
 *
 * <p>接口放在 automation 模块，由 test 模块实现，避免 automation 反向依赖测试报告。</p>
 */
public interface AutomationPlanReportProgressService {

    void validateBinding(String testPlanId, String testReportId, String sceneKey, String executionType);

    void onProgressChanged(String testPlanId, String testReportId);
}
