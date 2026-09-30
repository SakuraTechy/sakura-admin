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

package top.continew.admin.system.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 邮件配置测试请求参数
 *
 * @author Codex
 */
@Data
@Schema(description = "邮件配置测试请求参数")
public class MailTestReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "收件人不能为空")
    @Email(message = "收件人邮箱格式不正确")
    @Schema(description = "收件人邮箱", example = "admin@example.com")
    private String recipient;

    @NotBlank(message = "邮件协议不能为空")
    private String protocol;

    @NotBlank(message = "邮件服务器地址不能为空")
    private String host;

    @NotNull(message = "邮件服务器端口不能为空")
    @Min(value = 1, message = "邮件服务器端口必须大于 0")
    private Integer port;

    @NotBlank(message = "邮箱账号不能为空")
    private String username;

    @NotBlank(message = "邮箱密码不能为空")
    private String password;

    @NotNull(message = "SSL 配置不能为空")
    private Boolean sslEnabled;

    private Integer sslPort;
}
