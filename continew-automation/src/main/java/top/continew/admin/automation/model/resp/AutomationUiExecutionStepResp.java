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

/** 步骤执行分页项，不包含 diagnostics 正文。 */
@Data
public class AutomationUiExecutionStepResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stepExecutionDbId;
    private Long caseExecutionDbId;
    private String definitionStepId;
    private String sourceStepId;
    private Integer stepIndex;
    private Integer attemptNo;
    private String actionType;
    private String stepName;
    private String description;
    private String status;
    private Long durationMs;
    private String locatorSource;
    private String locatorType;
    private String errorCode;
    private String errorMessage;
    private Long eventSequence;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
