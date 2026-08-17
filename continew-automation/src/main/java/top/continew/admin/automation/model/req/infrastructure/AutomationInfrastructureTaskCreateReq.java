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

package top.continew.admin.automation.model.req.infrastructure;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 创建基础设施步骤任务；请求不允许携带命令、SQL 或凭据。 */
@Data
public class AutomationInfrastructureTaskCreateReq implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "caseKey 不能为空")
    private String caseKey;
    @NotBlank(message = "stepId 不能为空")
    private String stepId;
    /** Runner 任务标识，仅用于审计关联；CDP 交互回放可不传。 */
    private String jobId;
    private String batchId;
    /** 为空时服务端为交互式回放生成执行标识。 */
    private String executionId;
    /** Runner/CueCast 服务主体访问既有 execution 时使用的短时 capability。 */
    private String executionCapability;
    private Integer stepIndex;
    /** 可选的乐观锁版本；提供时必须与场景当前定义一致。 */
    private Long definitionVersion;
    @NotNull(message = "产品环境不能为空")
    private Long projectEnvironmentId;
    /** 旧客户端兼容字段；服务端不采用该值，执行尝试序号由 StepExecution 状态机分配。 */
    @Min(value = 0, message = "attempt 不能小于 0")
    private Integer attempt = 0;
    /**
     * 本次执行的运行时变量。优先使用 {{var}}，同时兼容旧 ${var}；服务端不得持久化或记录值。
     */
    @JsonAlias("runtime_bindings")
    private Map<String, Object> runtimeBindings;
    /**
     * 仅供当前任务使用的非持久化输入，例如浏览器裁剪出的验证码截图。
     * 不允许包含命令、SQL、凭据或文件原文，服务端只向对应 action 的 Agent payload 转发白名单字段。
     */
    @JsonAlias("runtime_input")
    private Map<String, Object> runtimeInput;
}
