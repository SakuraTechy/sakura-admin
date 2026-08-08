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

package top.continew.admin.automation.model.req.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/** 执行器注册请求；节点和产品环境关联均可为空。 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AutomationExecutorRegistrationReq {

    @NotBlank(message = "执行器类型不能为空")
    @Size(max = 32, message = "执行器类型长度不能超过 32")
    private String executorType;

    @NotBlank(message = "执行器实例不能为空")
    @Size(max = 128, message = "执行器实例长度不能超过 128")
    private String executorInstanceId;

    /** 可选的开放应用 Access Key；外部 Runner 建议首次注册时绑定。 */
    @Size(max = 255, message = "应用 Access Key 长度不能超过 255")
    private String applicationAccessKey;

    private Long nodeConfigId;

    private Long projectEnvironmentId;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
