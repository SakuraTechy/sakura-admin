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

/** 全部场景按场景统计，指定场景按各用例最新调试记录统计，均与历史分页无关。 */
@Data
public class AutomationUiExecutionHistorySummaryResp {
    private String dimension = "scene";
    private long total;
    private long completed;
    private long passed;
    private long failed;
    private long blocked;
    private long cancelled;
    private long skipped;
    private long notExecuted;
}
