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

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.service.AutomationUiDefinitionProjectionService;

/** UI 场景定义只读投影任务；调度周期在 SnailJob 中配置，建议构建每分钟、对账每五分钟。 */
@Component
@RequiredArgsConstructor
public class AutomationUiDefinitionProjectionJob {

    private static final int BUILD_BATCH_SIZE = 20;
    private static final int RECONCILE_BATCH_SIZE = 100;
    private static final int BACKFILL_BATCH_SIZE = 100;

    private final AutomationUiDefinitionProjectionService projectionService;

    @JobExecutor(name = "BuildAutomationUiDefinitionProjection")
    public void build() {
        int reconciled = projectionService.reconcile(RECONCILE_BATCH_SIZE);
        int built = 0;
        String leaseOwner = leaseOwner();
        while (built < BUILD_BATCH_SIZE && projectionService.buildNext(leaseOwner)) {
            built++;
        }
        int cleaned = projectionService.cleanupOrphanedProjections(RECONCILE_BATCH_SIZE);
        SnailJobLog.REMOTE.info("UI 定义投影批次完成，reconciled={}, built={}, cleaned={}", reconciled, built, cleaned);
    }

    @JobExecutor(name = "BackfillAutomationUiDefinitionMetrics")
    public void backfillMetrics() {
        // 每次从最小未知 ID 开始；已回填行自动退出候选集，不在周期对账中解析全表 case_list。
        Long lastSceneId = projectionService.backfillMetricsBatch(0L, BACKFILL_BATCH_SIZE);
        SnailJobLog.REMOTE.info("UI 定义度量回填批次完成，lastSceneId={}", lastSceneId);
    }

    private String leaseOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown-host";
        }
        String owner = host + ":" + ManagementFactory.getRuntimeMXBean().getName();
        return owner.length() <= 128 ? owner : owner.substring(0, 128);
    }
}
