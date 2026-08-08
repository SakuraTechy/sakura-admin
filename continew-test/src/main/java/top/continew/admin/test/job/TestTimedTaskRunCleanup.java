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

package top.continew.admin.test.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.test.service.TestTimedTaskRunService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestTimedTaskRunCleanup {

    private final TestTimedTaskRunService runService;

    @Value("${test.timed-task.cleanup.retention-days:180}")
    private int retentionDays;

    @Value("${test.timed-task.cleanup.batch-size:500}")
    private int batchSize;

    @Value("${test.timed-task.cleanup.max-batches-per-run:20}")
    private int maxBatchesPerRun;

    @Scheduled(fixedDelayString = "${test.timed-task.cleanup.fixed-delay-ms:86400000}", initialDelayString = "${test.timed-task.cleanup.initial-delay-ms:300000}")
    public void cleanupExpiredRuns() {
        int deleted = runService.cleanupExpiredRuns(retentionDays, batchSize, maxBatchesPerRun);
        if (deleted > 0) {
            log.info("已清理过期的测试定时任务运行记录，count={}，retentionDays={}", deleted, retentionDays);
        }
    }
}
