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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.test.service.TestTimedTaskRunService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestTimedTaskRunWatchdog {

    private final TestTimedTaskRunService runService;

    @Scheduled(fixedDelayString = "${test.timed-task.watchdog.fixed-delay-ms:600000}", initialDelayString = "${test.timed-task.watchdog.initial-delay-ms:60000}")
    public void recoverStaleRuns() {
        int recovered = runService.recoverStaleRuns();
        if (recovered > 0) {
            log.warn("已回收超时的测试定时任务运行记录，count={}", recovered);
        }
    }
}
