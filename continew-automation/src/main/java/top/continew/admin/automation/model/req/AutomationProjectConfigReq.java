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

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改自动化管理-项目配置参数
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
@Data
@Schema(description = "创建或修改自动化管理-项目配置参数")
public class AutomationProjectConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目类型
     */
    @Schema(description = "项目类型")
    @NotBlank(message = "项目类型不能为空")
    @Length(max = 30, message = "项目类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    @NotBlank(message = "项目名称不能为空")
    @Length(max = 30, message = "项目名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 项目地址
     */
    @Schema(description = "项目地址")
    @NotBlank(message = "项目地址不能为空")
    @Length(max = 255, message = "项目地址长度不能超过 {max} 个字符")
    private String url;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    @Length(max = 255, message = "项目描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 脚本路径
     */
    @Schema(description = "脚本路径")
    @NotBlank(message = "脚本路径不能为空")
    @Length(max = 500, message = "脚本路径长度不能超过 {max} 个字符")
    private String scriptPath;

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
