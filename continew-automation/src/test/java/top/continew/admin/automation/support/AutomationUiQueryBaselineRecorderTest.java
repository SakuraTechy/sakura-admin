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

package top.continew.admin.automation.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationUiQueryBaselineRecorderTest {

    @AfterEach
    void tearDown() {
        AutomationUiQueryBaselineRecorder.clear();
    }

    @Test
    void shouldRecordOnlyBoundedCountersAndTimings() {
        AutomationUiQueryBaselineRecorder.begin("scene-detail");
        AutomationUiQueryBaselineRecorder.recordSql();
        AutomationUiQueryBaselineRecorder.recordSql(2);
        AutomationUiQueryBaselineRecorder.recordHistoryLimit(100);
        AutomationUiQueryBaselineRecorder.recordExecutionRows(2);
        AutomationUiQueryBaselineRecorder.recordCaseRows(3);
        AutomationUiQueryBaselineRecorder.recordStepRows(4);
        long sceneQueryStartedNanos = AutomationUiQueryBaselineRecorder.startTimedSection();
        AutomationUiQueryBaselineRecorder
            .recordTiming(AutomationUiQueryBaselineRecorder.Phase.SCENE_QUERY, sceneQueryStartedNanos);
        AutomationUiQueryBaselineRecorder.recordExternalCall(AutomationUiQueryBaselineRecorder.startExternalCall());
        long usedHeapBeforeBytes = AutomationUiQueryBaselineRecorder.startHeapSample();
        byte[] payload = new byte[128];
        AutomationUiQueryBaselineRecorder.recordInMemoryPayloadBytes(payload.length);
        AutomationUiQueryBaselineRecorder.recordHeapSample(usedHeapBeforeBytes);
        AutomationUiQueryBaselineRecorder.markBodyReady();

        AutomationUiQueryBaselineRecorder.Snapshot snapshot = AutomationUiQueryBaselineRecorder.finish(200, 4096);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.operation()).isEqualTo("scene-detail");
        assertThat(snapshot.status()).isEqualTo(200);
        assertThat(snapshot.responseBytes()).isEqualTo(4096);
        assertThat(snapshot.instrumentedSqlCount()).isEqualTo(3);
        assertThat(snapshot.executionRows()).isEqualTo(2);
        assertThat(snapshot.caseRows()).isEqualTo(3);
        assertThat(snapshot.stepRows()).isEqualTo(4);
        assertThat(snapshot.historyLimit()).isEqualTo(100);
        assertThat(snapshot.externalCallCount()).isEqualTo(1);
        assertThat(snapshot.externalCallMs()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.sceneQueryMs()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.unaccountedControllerMs()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.inMemoryPayloadBytes()).isEqualTo(128);
        assertThat(snapshot.heapDeltaBytes()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.elapsedMs()).isGreaterThanOrEqualTo(snapshot.controllerMs());
        assertThat(AutomationUiQueryBaselineRecorder.isActive()).isFalse();
    }

    @Test
    void shouldIgnoreCallsOutsideRequestProbe() {
        AutomationUiQueryBaselineRecorder.recordSql();
        AutomationUiQueryBaselineRecorder.recordExecutionRows(10);

        assertThat(AutomationUiQueryBaselineRecorder.finish(200, 1)).isNull();
    }
}
