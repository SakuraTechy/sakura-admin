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

package top.continew.admin.automation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;

class AutomationUiSceneServiceImplTest {

    @Test
    void shouldNotHideScenesWhenOnlyRecordTypeIsRequested() {
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setExecuteResultType("debug");

        assertThat(AutomationUiSceneServiceImpl.shouldSelectExecutionRecords(query)).isFalse();
        assertThat(AutomationUiSceneServiceImpl.requiresExecutionRecordMatch(query)).isFalse();
    }

    @Test
    void shouldSelectPlanRecordsWithoutRequiringExecutedScene() {
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setExecuteResultType("report");
        query.setTestPlanId("1001");

        assertThat(AutomationUiSceneServiceImpl.shouldSelectExecutionRecords(query)).isTrue();
        assertThat(AutomationUiSceneServiceImpl.requiresExecutionRecordMatch(query)).isFalse();
    }

    @Test
    void shouldRequireMatchingRecordForReportInstance() {
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setExecuteResultType("report");
        query.setTestPlanId("1001");
        query.setTestReportId("2001");

        assertThat(AutomationUiSceneServiceImpl.shouldSelectExecutionRecords(query)).isTrue();
        assertThat(AutomationUiSceneServiceImpl.requiresExecutionRecordMatch(query)).isTrue();
    }
}
