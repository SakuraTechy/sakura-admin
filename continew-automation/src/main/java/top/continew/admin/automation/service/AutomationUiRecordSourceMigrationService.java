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

import java.util.List;

/** execution record_source 的存量迁移与核验服务。 */
public interface AutomationUiRecordSourceMigrationService {

    AuditResult audit(int sampleLimit);

    BackfillResult backfillBatch(long afterId, int batchSize);

    VerificationResult verify();

    record ConflictSample(long executionId, String conflictType) {
    }

    record AuditResult(long internalWithPlanOrReport, long planTriggerWithoutPlanOrReport,
                       long nonPlanTriggerWithReport, long splitLegacyRecordType, List<ConflictSample> samples) {

        public boolean blocksConstraintClosure() {
            return internalWithPlanOrReport > 0 || planTriggerWithoutPlanOrReport > 0 || nonPlanTriggerWithReport > 0 || splitLegacyRecordType > 0;
        }
    }

    record BackfillResult(long lastExecutionId, int selected, int updated) {
    }

    record VerificationResult(long nullCount, long invalidCount, long mismatchCount) {

        public boolean readyForConstraintClosure() {
            return nullCount == 0 && invalidCount == 0 && mismatchCount == 0;
        }
    }
}
