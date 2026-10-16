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

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 整次任务或未分组旧批次的有界摘要；runId 不是可执行的子 batchId。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AutomationUiExecutionRunResp extends AutomationUiExecutionSummaryResp {
    private String runId;
    private String sceneName;
    private Integer sceneOrder;
    private Integer sceneTotal;
    private Integer sceneCompleted;
    private Integer scenePass;
    private Integer sceneFail;
    private Integer sceneCancelled;
    private Integer sceneBlocked;
    private Integer sceneSkip;
}
