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
import top.continew.admin.automation.converter.AutomationUiCaseFingerprint;
import top.continew.admin.automation.model.entity.ui.CaseDO;

class AutomationUiCaseReviewGovernanceServiceImplTest {

    @Test
    void completionMetricsMustUseDecisionFactsInsteadOfMutableWorkflowStatus() {
        assertThat(AutomationUiCaseReviewGovernanceServiceImpl.REVIEW_COMPLETION_PREDICATE)
            .contains("r.completed_at IS NOT NULL")
            .contains("decision = 'APPROVED'")
            .contains(">= r.required_approvals")
            .contains("decision IN ('CHANGES_REQUESTED','REJECTED')")
            .doesNotContain("r.status = 'WITHDRAWN'")
            .doesNotContain("r.status = 'OUTDATED'")
            .doesNotContain("r.status = 'APPROVED'");
    }

    @Test
    void queueStatusMustExpireDeletedOrHashSchemaMismatchedCase() {
        CaseDO currentCase = new CaseDO();
        currentCase.setId("CASE-1");
        currentCase.setName("登录");
        AutomationUiCaseFingerprint.Fingerprint fingerprint = AutomationUiCaseFingerprint.compute(currentCase);

        assertThat(AutomationUiCaseReviewGovernanceServiceImpl.effectiveQueueStatus("APPROVED", fingerprint
            .hash(), fingerprint.schemaVersion(), currentCase)).isEqualTo("APPROVED");
        assertThat(AutomationUiCaseReviewGovernanceServiceImpl.effectiveQueueStatus("APPROVED", fingerprint
            .hash(), "old-schema", currentCase)).isEqualTo("OUTDATED");
        assertThat(AutomationUiCaseReviewGovernanceServiceImpl.effectiveQueueStatus("IN_REVIEW", fingerprint
            .hash(), fingerprint.schemaVersion(), null)).isEqualTo("OUTDATED");
        assertThat(AutomationUiCaseReviewGovernanceServiceImpl.effectiveQueueStatus("WITHDRAWN", fingerprint
            .hash(), "old-schema", currentCase)).isEqualTo("WITHDRAWN");
    }
}
