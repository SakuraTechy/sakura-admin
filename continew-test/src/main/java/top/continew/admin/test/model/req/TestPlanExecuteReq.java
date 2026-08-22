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

package top.continew.admin.test.model.req;

import jakarta.validation.Valid;
import lombok.Data;
import top.continew.admin.automation.model.req.playwright.AutomationPlaywrightRunnerOptionsReq;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class TestPlanExecuteReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectEnvironmentId;

    private Long automationEnvironmentId;

    /** 未传时保持原 Selenium/Jenkins 执行链路。 */
    private TestExecutionEngineEnum executionEngine = TestExecutionEngineEnum.SELENIUM;

    @Valid
    private AutomationPlaywrightRunnerOptionsReq runnerOptions = new AutomationPlaywrightRunnerOptionsReq();

    private Map<String, Object> cdpOptions;

    /** 仅由定时任务写入 SCHEDULE；普通接口默认 MANUAL。 */
    private String triggerMode = "MANUAL";

    private String executeName;
    private String executeEmail;

    /**
     * 执行范围。缺省表示计划全部关联场景；传值时必须是计划关联场景的非空子集。
     */
    private List<Long> sceneIds;

    /**
     * 单场景执行时的用例范围。缺省表示执行场景内全部可执行用例。
     */
    private List<String> caseIds;
}
