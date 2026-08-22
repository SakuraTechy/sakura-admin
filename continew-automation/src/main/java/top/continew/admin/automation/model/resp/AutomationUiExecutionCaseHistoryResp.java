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
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 单用例历史项，同时携带构造前端批次上下文所需的轻量父级字段。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AutomationUiExecutionCaseHistoryResp extends AutomationUiExecutionCaseResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private String executionKey;
    private String batchId;
    private Long sceneDbId;
    private String sceneKey;
    private String recordSource;
    private Long testPlanId;
    private Long testReportId;
    private Integer buildNumber;
    private String executionEngine;
    private String executeUsername;
    private String executeName;
    private String projectEnvironmentName;
    private Long projectEnvironmentId;
}
