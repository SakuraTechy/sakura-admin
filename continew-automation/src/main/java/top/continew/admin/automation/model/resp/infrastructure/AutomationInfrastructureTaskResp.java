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

package top.continew.admin.automation.model.resp.infrastructure;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/** 对 Runner、CDP 和执行节点公开的任务状态；不包含敏感执行内容。 */
@Data
public class AutomationInfrastructureTaskResp implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;
    /** 当前日志最大序号，调用方可作为下一次 afterSequence。 */
    private Long nextSequence;
    private String caseKey;
    private String stepId;
    private String actionType;
    private String status;
    private String executor;
    private Integer exitCode;
    private Long affectedRows;
    private String errorCode;
    private String errorMessage;
    private String resultSummary;
    /**
     * Agent 当次返回的安全结果快照。只承载变量和值的受限元数据，绝不落库，避免命令输出或凭据经任务 API 泄露。
     */
    private Map<String, Object> result;
    private String startedAt;
    private String finishedAt;
    private boolean cancelRequested;
    private List<Log> logs;

    @Data
    public static class Log implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long sequence;
        private String level;
        private String message;
    }
}
