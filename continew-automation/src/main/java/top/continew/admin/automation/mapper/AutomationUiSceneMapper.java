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

package top.continew.admin.automation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.continew.starter.data.mp.base.BaseMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.resp.AutomationUiSceneRevisionResp;

import java.util.Collection;
import java.util.List;

/**
 * 自动化管理-UI自动化场景 Mapper
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Mapper
public interface AutomationUiSceneMapper extends BaseMapper<AutomationUiSceneDO> {
    AutomationUiSceneDO getAutomationUiSceneById(Long id);

    /** 锁定场景定义，避免 JSON 全量写入相互覆盖。 */
    AutomationUiSceneDO selectByIdForUpdate(Long id);

    /**
     * 只写场景定义字段，并以定义版本实现乐观并发控制。
     */
    int updateDefinition(@Param("id") Long id,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("caseList") List<top.continew.admin.automation.model.entity.ui.CaseDO> caseList,
                         @Param("caseTotal") Integer caseTotal,
                         @Param("stepTotal") Integer stepTotal);

    /** 初始化新建空场景的定义容器，不改变定义版本。 */
    int initializeEmptyDefinition(@Param("id") Long id);

    /** 查询场景节点 ID 的持久化高水位，删除节点后也不会回退。 */
    Long selectNodeIdSequence(@Param("sceneId") Long sceneId,
                              @Param("scopeKey") String scopeKey,
                              @Param("idPrefix") String idPrefix);

    /** 推进场景节点 ID 高水位；场景行锁保证同一场景内分配串行。 */
    int upsertNodeIdSequence(@Param("sceneId") Long sceneId,
                             @Param("scopeKey") String scopeKey,
                             @Param("idPrefix") String idPrefix,
                             @Param("lastValue") Long lastValue);

    /**
     * 只更新执行状态和执行历史，避免把体积很大的 case_list 反复写入 InnoDB 和 binlog。
     */
    int updateExecutionState(AutomationUiSceneDO scene);

    /**
     * 把最新摘要写入独立窄表并推进单调版本号，为停止旧 JSON 写入和增量刷新提供迁移基础。
     */
    int upsertExecutionState(AutomationUiSceneDO scene);

    /**
     * 清空执行结果时显式写 NULL，同时不触碰场景定义字段。
     */
    int clearExecutionState(Long id);

    int clearExecutionStateRecord(Long id);

    int updateTestPlanIds(@Param("id") Long id, @Param("testPlanIds") List<Object> testPlanIds);

    List<AutomationUiSceneDO> selectLegacyHistoryBatch(@Param("afterId") Long afterId, @Param("limit") int limit);

    List<AutomationUiSceneRevisionResp> selectRevisions(@Param("ids") Collection<Long> ids);
}
