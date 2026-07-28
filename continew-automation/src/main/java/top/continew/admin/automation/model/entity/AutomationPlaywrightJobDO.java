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
 * Playwright Runner 任务持久化记录。
 *
 * <p>仅保存可恢复的运行事实；进程、实时日志和实时画面仍属于当前执行节点的短期内存状态。</p>
 *
 * @author Codex
 */
@Data
@TableName("automation_playwright_job")
public class AutomationPlaywrightJobDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String jobId;

    private String sceneKey;

    private String caseId;

    private String caseKey;

    /** Runner 启动时绑定的场景定义版本，读取前必须再次核验。 */
    private Long definitionVersion;

    private String batchId;

    private String executionId;

    private String executionType;

    private Long projectEnvironmentId;

    private String executorNode;

    private String status;

    private Integer exitCode;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime heartbeatAt;

    private String artifactFileIds;

    private String artifactUrls;
}
