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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;

/**
 * UI 自动化规范化执行记录服务。
 *
 * <p>数据库使用 execution/case/step 表保存事实；对现有页面暂时重建旧 Map DTO，避免把存储迁移扩散到前端。</p>
 */
public interface AutomationUiExecutionRecordService {

    /**
     * 以独立事务保存外部执行记录。
     *
     * <p>调用方必须在触发外部副作用前先写入 queued 记录，确保 execution 与不可变 revision 已提交。</p>
     */
    void saveExternalExecutionRecord(AutomationUiSceneDO scene, Map<String, Object> record);

    void saveRecord(AutomationUiSceneDO scene, Map<String, Object> record, String changedCaseId);

    Map<String, Object> findBatch(Long sceneId, String batchId);

    /**
     * 从执行创建时绑定的 revision 读取用例，禁止回读场景当前 caseList。
     */
    FrozenExecutionCase findFrozenCase(Long sceneId, String batchId, String caseId);

    /**
     * 校验短期 capability 是否仍匹配指定场景批次；数据库只保存摘要和过期时间。
     */
    boolean matchesExecutionCapability(Long sceneId, String batchId, String executionCapability);

    Map<String, Object> findReportRecord(Long sceneId, String testReportId);

    List<Object> listRecords(Long sceneId, boolean testRecord, int limit);

    Map<Long, Map<String, Object>> findReportRecords(Collection<Long> sceneIds, String testReportId);

    void clearScene(Long sceneId);

    /**
     * 删除场景关联的执行事实和定义版本。
     *
     * <p>artifact 引用会保留为立即过期状态，交给统一清理任务删除真实文件，避免形成对象存储孤儿。</p>
     */
    void deleteScene(Long sceneId);

    void removeTestPlanRecords(Long sceneId, String testPlanId);

    int markReportIncompleteFailed(String testReportId, String errorMessage);

    /**
     * 把单个场景的旧 debug_record/test_record 迁移到执行事实表。
     *
     * @return 已处理的旧记录数；所有记录验证成功后才会清空旧 JSON 列
     */
    int migrateLegacyScene(AutomationUiSceneDO scene);

    record FrozenExecutionCase(CaseDO caseDO, Long definitionRevisionId, Long projectEnvironmentId,
                               Map<String, Object> effectiveExecutionConfig) {
    }
}
