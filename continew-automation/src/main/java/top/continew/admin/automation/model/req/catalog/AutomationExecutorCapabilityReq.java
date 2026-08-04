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

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 执行器能力上报请求。
 *
 * @author Codex
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AutomationExecutorCapabilityReq {

    /** 仅为兼容旧客户端接收；服务端身份以受控接口路径为准。 */
    private String executor;

    @NotBlank(message = "执行器实例不能为空")
    private String executorInstanceId;

    @NotBlank(message = "执行器版本不能为空")
    private String executorVersion;

    @NotBlank(message = "目录版本不能为空")
    private String catalogVersion;

    @NotNull(message = "项目环境不能为空")
    @Positive(message = "项目环境必须为正数 ID")
    private Long projectEnvironmentId;

    /** CueCast 必须提供扩展会话；Runner 可为空。 */
    private String sessionId;

    @NotEmpty(message = "执行器 action 集合不能为空")
    private List<String> actions = new ArrayList<>();

    private List<String> features = new ArrayList<>();
}
