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

package top.continew.admin.automation.model.resp;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/** 用例执行分页项，不包含步骤数组和原始结果。 */
@Data
public class AutomationUiExecutionCaseResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long caseExecutionDbId;
    private Long executionDbId;
    private String definitionCaseId;
    private String caseKey;
    private String caseExecutionKey;
    private String caseName;
    private Integer caseIndex;
    private Integer attemptNo;
    private String jobId;
    private String status;
    private String result;
    private String executeStatus;
    private String executeResult;
    private Integer stepTotal;
    private Integer stepPass;
    private Integer stepFail;
    private Integer stepSkip;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Long stepDurationMs;
    private Long wallClockDurationMs;
    private String errorCode;
    private String errorMessage;
    private Long eventSequence;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
