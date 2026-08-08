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

package top.continew.admin.automation.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.model.entity.BaseDO;

/**
 * 执行器独立注册信息。
 *
 * <p>执行器身份不再复用 Jenkins 节点名称或 IP；节点配置仅作为可选的部署位置关联。</p>
 */
@Data
@TableName("automation_executor_registration")
public class AutomationExecutorRegistrationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String executorType;

    private String executorInstanceId;

    /** 可选的开放应用 Access Key；绑定后能力上报必须使用该应用签名。 */
    private String applicationAccessKey;

    /** 可选的 Jenkins 节点配置 ID，不参与执行器身份匹配。 */
    private Long nodeConfigId;

    /** 为空表示允许用于所有产品环境。 */
    private Long projectEnvironmentId;

    private String description;

    /** 1 启用，0 禁用。 */
    private Integer status;

    private String lastExecutorVersion;

    private String lastCatalogVersion;

    /** 最近一次上报的 action JSON 清单，便于管理员核对实际能力。 */
    private String lastActions;

    /** 最近一次上报的运行特性 JSON 清单。 */
    private String lastFeatures;

    private LocalDateTime lastReportedAt;
}
