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
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改项目管理-服务器配置参数
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Data
@Schema(description = "创建或修改项目管理-服务器配置参数")
public class ProjectServerConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    /**
     * 服务器类型
     */
    @Schema(description = "服务器类型")
    @NotBlank(message = "服务器类型不能为空")
    @Length(max = 30, message = "服务器类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 服务器版本
     */
    @Schema(description = "服务器版本")
    @NotBlank(message = "服务器版本不能为空")
    @Length(max = 30, message = "服务器版本长度不能超过 {max} 个字符")
    private String version;

    /**
     * 服务器IP
     */
    @Schema(description = "服务器IP")
    @NotBlank(message = "服务器IP不能为空")
    @Length(max = 30, message = "服务器IP长度不能超过 {max} 个字符")
    private String ip;

    /**
     * 服务器端口
     */
    @Schema(description = "服务器端口")
    @NotNull(message = "服务器端口不能为空")
    @Min(value = 0, message = "端口号不能小于 0")
    @Max(value = 65535, message = "端口号不能超过 65535")
    private Integer port;

    /**
     * 服务器用户名
     */
    @Schema(description = "服务器用户名")
    @NotBlank(message = "服务器用户名不能为空")
    @Length(max = 30, message = "服务器用户名长度不能超过 {max} 个字符")
    private String userName;

    /**
     * 服务器密码
     */
    @Schema(description = "服务器密码", hidden = true)
    @NotBlank(message = "服务器密码不能为空")
    @Length(max = 30, message = "服务器密码长度不能超过 {max} 个字符")
    private String passWord;

    /**
     * 服务器描述
     */
    @Schema(description = "服务器描述")
    @Length(max = 255, message = "服务器描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 服务器参数配置
     */
    @Schema(description = "服务器参数配置")
    //    @NotEmpty(message = "服务器参数配置不能为空")
    @Size(max = 10, message = "服务器参数配置最多支持 {max} 组")
    private List<Object> configList;

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