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

package top.continew.admin.automation.model.req;

import lombok.Data;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改自动化管理-Jenkins配置参数
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Data
@Schema(description = "创建或修改自动化管理-Jenkins配置参数")
public class AutomationJenkinsConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本
     */
    @Schema(description = "版本")
    @NotBlank(message = "版本不能为空")
    @Length(max = 30, message = "版本长度不能超过 {max} 个字符")
    private String version;

    /**
     * IP
     */
    @Schema(description = "IP")
    @NotBlank(message = "IP不能为空")
    @Length(max = 30, message = "IP长度不能超过 {max} 个字符")
    private String ip;

    /**
     * 端口
     */
    @Schema(description = "端口")
    @NotNull(message = "端口不能为空")
    private Integer port;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    @NotBlank(message = "用户名不能为空")
    @Length(max = 30, message = "用户名长度不能超过 {max} 个字符")
    private String userName;

    /**
     * 密码
     */
    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    @Length(max = 30, message = "密码长度不能超过 {max} 个字符")
    private String passWord;

    /**
     * 地址
     */
    @Schema(description = "地址")
    @Length(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String url;

    /**
     * 关联项目
     */
    @Schema(description = "关联项目")
    @NotEmpty(message = "关联项目不能为空")
    @Size(max = 60, message = "关联项目最多支持 {max} 个")
    private List<Object> jobList;

    /**
     * 描述
     */
    @Schema(description = "描述")
    @Length(max = 255, message = "描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}