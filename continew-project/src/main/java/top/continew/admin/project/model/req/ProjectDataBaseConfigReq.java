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

import java.time.*;

/**
 * 创建或修改项目管理-数据库配置参数
 *
 * @author hagyao520
 * @since 2025/05/08 18:00
 */
@Data
@Schema(description = "创建或修改项目管理-数据库配置参数")
public class ProjectDataBaseConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    /**
     * 数据库类型
     */
    @Schema(description = "数据库类型")
    @NotBlank(message = "数据库类型不能为空")
    @Length(max = 30, message = "数据库类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 数据库版本
     */
    @Schema(description = "数据库版本")
    @NotBlank(message = "数据库版本不能为空")
    @Length(max = 30, message = "数据库版本长度不能超过 {max} 个字符")
    private String version;

    /**
     * 数据库驱动
     */
    @Schema(description = "数据库驱动")
    @NotBlank(message = "数据库驱动不能为空")
    @Length(max = 255, message = "数据库驱动长度不能超过 {max} 个字符")
    private String driver;

    /**
     * 数据库IP
     */
    @Schema(description = "数据库IP")
    @NotBlank(message = "数据库IP不能为空")
    @Length(max = 30, message = "数据库IP长度不能超过 {max} 个字符")
    private String ip;

    /**
     * 数据库端口
     */
    @Schema(description = "数据库端口")
    @NotNull(message = "数据库端口不能为空")
    @Min(value = 0, message = "端口号不能小于 0")
    @Max(value = 65535, message = "端口号不能超过 65535")
    private Integer port;

    /**
     * 数据库/模式
     */
    @Schema(description = "数据库/模式")
    //    @NotBlank(message = "数据库/模式不能为空")
    @Length(max = 30, message = "数据库用户名长度不能超过 {max} 个字符")
    private String dataBase;

    /**
     * 数据库用户名
     */
    @Schema(description = "数据库用户名")
    @NotBlank(message = "数据库用户名不能为空")
    @Length(max = 30, message = "数据库用户名长度不能超过 {max} 个字符")
    private String userName;

    /**
     * 数据库密码
     */
    @Schema(description = "数据库密码")
    @NotBlank(message = "数据库密码不能为空")
    @Length(max = 255, message = "数据库密码长度不能超过 {max} 个字符")
    private String passWord;

    /**
     * 数据库连接串
     */
    @Schema(description = "数据库连接串")
    @NotBlank(message = "数据库连接串不能为空")
    @Length(max = 255, message = "数据库连接串长度不能超过 {max} 个字符")
    private String url;

    /**
     * 数据库描述
     */
    @Schema(description = "数据库描述")
    @Length(max = 255, message = "数据库描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 数据库参数配置
     */
    @Schema(description = "数据库参数配置")
    @Size(max = 10, message = "服务器参数配置最多支持 {max} 组")
    private List<Object> configList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    private Integer delFlag = 1;
}