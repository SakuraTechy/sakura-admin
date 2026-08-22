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

/** 执行历史有界摘要，不包含 case、step、诊断、配置正文或原始结果。 */
@Data
public class AutomationUiExecutionSummaryResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long executionDbId;
    private String executionKey;
    private Long sceneDbId;
    private String sceneKey;
    private String batchId;
    private String recordSource;
    private Long testPlanId;
    private Long testReportId;
    private Integer buildNumber;
    private String recordType;
    private String triggerType;
    private String executionEngine;
    private String status;
    private String result;
    private Long executeUserId;
    private String executeUsername;
    private String executeName;
    private String executeEmail;
    private Long projectEnvironmentId;
    private String projectEnvironmentName;
    private Integer caseTotal;
    private Integer casePass;
    private Integer caseFail;
    private Integer caseSkip;
    private Integer caseCancelled;
    private Integer stepTotal;
    private Integer stepPass;
    private Integer stepFail;
    private Integer stepSkip;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
