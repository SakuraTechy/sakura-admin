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

package top.continew.admin.controller.schedule;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.service.AutomationUiRecordSourceMigrationService;

/** execution record_source 小批回填与只读核验任务。 */
@Component
@RequiredArgsConstructor
public class AutomationUiRecordSourceMigrationJob {

    private static final int BACKFILL_BATCH_SIZE = 500;

    private final AutomationUiRecordSourceMigrationService migrationService;

    @JobExecutor(name = "BackfillAutomationUiExecutionRecordSource")
    public void backfill() {
        // 已完成行会退出 NULL 候选集，因此每次从 0 开始仍可暂停、重试且不会重复改写。
        AutomationUiRecordSourceMigrationService.BackfillResult backfill = migrationService
            .backfillBatch(0, BACKFILL_BATCH_SIZE);
        AutomationUiRecordSourceMigrationService.VerificationResult verification = migrationService.verify();
        SnailJobLog.REMOTE.info("UI 执行来源回填批次完成，selected={}, updated={}, remaining={}, invalid={}, mismatch={}", backfill
            .selected(), backfill.updated(), verification.nullCount(), verification.invalidCount(), verification
                .mismatchCount());
    }

    @JobExecutor(name = "AuditAutomationUiExecutionRecordSource")
    public void audit() {
        AutomationUiRecordSourceMigrationService.AuditResult audit = migrationService.audit(100);
        AutomationUiRecordSourceMigrationService.VerificationResult verification = migrationService.verify();
        // 常规日志只输出计数；需要人工定位时由受控运维入口读取 ID-only samples。
        SnailJobLog.REMOTE
            .info("UI 执行来源核验完成，conflicts=[{},{},{},{}], remaining={}, invalid={}, mismatch={}, closureReady={}", audit
                .internalWithPlanOrReport(), audit.planTriggerWithoutPlanOrReport(), audit
                    .nonPlanTriggerWithReport(), audit.splitLegacyRecordType(), verification.nullCount(), verification
                        .invalidCount(), verification.mismatchCount(), !audit.blocksConstraintClosure() && verification
                            .readyForConstraintClosure());
    }
}
