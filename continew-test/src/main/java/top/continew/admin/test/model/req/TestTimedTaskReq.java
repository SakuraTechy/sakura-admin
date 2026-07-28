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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.model.enums.TestExecutionEngineEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "创建或修改测试定时任务参数")
public class TestTimedTaskReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "测试计划ID不能为空")
    private Long testPlanId;

    private String testPlanName;
    private String type;
    private TestExecutionEngineEnum executionEngine = TestExecutionEngineEnum.SELENIUM;

    @NotBlank(message = "任务名称不能为空")
    @Length(max = 128, message = "任务名称长度不能超过 {max}")
    private String name;

    @Length(max = 500, message = "任务描述长度不能超过 {max}")
    private String description;

    @NotBlank(message = "Cron 表达式不能为空")
    private String cronExpression;

    private String misfirePolicy;
    private Integer allowConcurrent;
    private Long projectEnvironmentId;
    private Long automationEnvironmentId;
    private Map<String, Object> executionConfig;
    private String executeName;
    private String executeEmail;

    @NotEmpty(message = "通知邮箱不能为空")
    @Size(max = 20, message = "通知邮箱不能超过 {max} 个")
    private List<@Email(message = "通知邮箱格式不正确") String> notificationEmails;

    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
