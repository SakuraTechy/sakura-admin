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

package top.continew.admin.automation.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.model.entity.BaseDO;

/**
 * 基础设施步骤任务记录。
 *
 * <p>该表只保存可审计的身份和脱敏结果。命令、SQL、连接串、用户名和密码不得入库，
 * 执行节点必须根据任务身份从受控快照取得执行事实。</p>
 */
@Data
@TableName("automation_infrastructure_task")
public class AutomationInfrastructureTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String caseKey;
    private String stepId;
    private String actionType;
    private String executionId;
    private Long projectEnvironmentId;
    private Long sceneId;
    private Long definitionVersion;
    private String targetKind;
    /** 当前步骤引用的服务器或数据库配置 ID。 */
    private Long targetConfigId;
    /** 旧版步骤的逻辑绑定键，仅用于兼容历史场景。 */
    private String targetBindingKey;
    private Integer attempt;
    private String idempotencyKey;
    private String executorNode;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Integer exitCode;
    private Long affectedRows;
    private String resultSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime cancelRequestedAt;
}
