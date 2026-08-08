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

package top.continew.admin.automation.model.resp.infrastructure;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/** 当前任务绑定的不可变 SQL 或服务器命令定义；不包含运行时参数值或连接信息。 */
@Data
public class AutomationInfrastructureStatementResp implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String stepId;
    private String actionType;
    private Long definitionVersion;
    private String sqlMode;
    /** SQL 来自执行绑定的定义 revision，运行时变量值不会持久化或回显。 */
    private String sql;
    /** 服务器命令来自执行绑定的定义 revision，不使用场景当前值。 */
    private String command;
}
