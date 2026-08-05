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

package top.continew.admin.test.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "测试报告信息")
public class TestReportResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long versionId;
    private String projectName;
    private String versionName;
    private Long testPlanId;
    private String testPlanName;
    private String name;
    private String description;
    private String triggerMode;
    private String executeMode;
    private String reportType;
    private Long runTime;
    private String buildNumber;
    private String consoleUrl;
    private String reportUrl;
    private String videoUrl;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
