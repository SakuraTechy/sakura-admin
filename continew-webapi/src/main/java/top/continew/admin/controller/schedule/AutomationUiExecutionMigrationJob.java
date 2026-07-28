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

import java.util.List;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;

/**
 * 旧 UI 场景执行历史迁移任务。
 *
 * <p>每次只处理一个小批次；单场景迁移和旧列清理由同一事务保护，可安全重复执行。</p>
 */
@Component
@RequiredArgsConstructor
public class AutomationUiExecutionMigrationJob {

    private static final String EXECUTOR_NAME = "MigrateAutomationUiExecutionHistory";

    private final AutomationUiSceneMapper sceneMapper;
    private final AutomationUiExecutionRecordService executionRecordService;

    @Value("${automation.execution-migration.batch-size:20}")
    private int batchSize;

    @JobExecutor(name = EXECUTOR_NAME)
    public void migrate() {
        int safeBatchSize = Math.max(1, Math.min(100, batchSize));
        List<AutomationUiSceneDO> scenes = sceneMapper.selectLegacyHistoryBatch(0L, safeBatchSize);
        int migratedRecords = 0;
        for (AutomationUiSceneDO scene : scenes) {
            migratedRecords += executionRecordService.migrateLegacyScene(scene);
        }
        SnailJobLog.REMOTE.info("UI 自动化旧执行历史迁移批次完成，scenes={}, records={}, remaining={}", scenes
            .size(), migratedRecords, scenes.size() >= safeBatchSize);
    }
}
