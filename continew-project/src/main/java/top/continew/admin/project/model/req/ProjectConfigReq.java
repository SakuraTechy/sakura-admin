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

package top.continew.admin.project.model.req;

import lombok.Data;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;

/**
 * 创建或修改项目配置参数
 *
 * @author hagyao520
 * @since 2025/04/15 11:56
 */
@Data
@Schema(description = "创建或修改项目配置参数")
public class ProjectConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    @NotBlank(message = "项目名称不能为空")
    @Length(max = 30, message = "项目名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 项目简称
     */
    @Schema(description = "项目简称")
    @NotBlank(message = "项目简称不能为空")
    @Length(max = 30, message = "项目简称长度不能超过 {max} 个字符")
    private String abbreviate;

    /**
     * 项目成员
     */
    @Schema(description = "项目成员")
    @NotEmpty(message = "项目成员不能为空")
    @Size(max = 10, message = "项目成员最多支持 {max} 人")
    private List<String> member;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    @Length(max = 255, message = "项目描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志")
    private Integer delFlag = 1;
}