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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "创建或修改测试计划参数")
public class TestPlanReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String projectName;
    private String type;

    @NotBlank(message = "计划名称不能为空")
    @Length(max = 128, message = "计划名称长度不能超过 {max}")
    private String name;

    @Length(max = 64, message = "计划简称长度不能超过 {max}")
    private String abbreviate;

    @Length(max = 500, message = "计划描述长度不能超过 {max}")
    private String description;

    private List<Long> memberIds;
    private List<Long> principalIds;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Map<String, Object> timedTasksConfig;
    private Map<String, Object> projectConfig;
    private Map<String, Object> automationConfig;
    private List<Object> functionalScene;
    private List<Long> uiTestScene;
    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
