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

/**
 * 基础设施步骤可选目标。
 *
 * <p>仅返回用于下拉展示和保存引用的非敏感字段，绝不向浏览器返回用户名、密码或连接串。</p>
 */
@Data
public class AutomationInfrastructureTargetResp implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String kind;
    private String type;
    private String ip;
    private Integer port;
    private String dataBase;
    private String description;
    /** 仅表示从 Admin 执行节点到目标 IP:端口的 TCP 可达性，不代表 SSH/JDBC 凭据认证成功。 */
    private Boolean online;
}
