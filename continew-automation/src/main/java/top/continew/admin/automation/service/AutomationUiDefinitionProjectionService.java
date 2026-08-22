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
import top.continew.admin.automation.model.entity.ui.CaseDO;

/** 场景定义只读投影的写入度量、持久化队列和构建入口。 */
public interface AutomationUiDefinitionProjectionService {

    /**
     * 必须在定义写事务内调用，使版本、度量和持久化队列一起提交或回滚。
     */
    void recordDefinitionWrite(Long sceneId, Long definitionVersion, List<CaseDO> caseList);

    /** 仅用于迁移期未知度量的单场景受控回填，并在超阈值时确认持久化入队。 */
    DefinitionMetrics ensureMetrics(Long sceneId, Long definitionVersion);

    /** 抢占并构建一个待处理版本；没有任务时返回 false。 */
    boolean buildNext(String leaseOwner);

    /** 只扫描已有度量，不解析 case_list，补齐缺失的当前版本队列。 */
    int reconcile(int limit);

    /** 按主键小批回填存量未知度量；返回本批最后一个场景 ID，没有数据时返回 null。 */
    Long backfillMetricsBatch(Long afterSceneId, int limit);

    /** 清理超过宽限期且未被任何状态引用的孤立节点。 */
    int cleanupOrphanedProjections(int limit);

    record DefinitionMetrics(long sizeBytes, int caseCount, int stepCount, boolean projectionQueued) {
    }
}
