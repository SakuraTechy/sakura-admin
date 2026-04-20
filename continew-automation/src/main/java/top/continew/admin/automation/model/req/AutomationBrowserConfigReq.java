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
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改自动化管理-浏览器配置参数
 *
 * @author hagyao520
 * @since 2025/05/29 15:41
 */
@Data
@Schema(description = "创建或修改自动化管理-浏览器配置参数")
public class AutomationBrowserConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 浏览器ID
     */
    @Schema(description = "浏览器ID")
    @Length(max = 255, message = "浏览器ID长度不能超过 {max} 个字符")
    private String id;

    /**
     * 浏览器类型
     */
    @Schema(description = "浏览器类型")
    @NotBlank(message = "浏览器类型不能为空")
    @Length(max = 30, message = "浏览器类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 浏览器版本
     */
    @Schema(description = "浏览器版本")
    @NotBlank(message = "浏览器版本不能为空")
    @Length(max = 30, message = "浏览器版本长度不能超过 {max} 个字符")
    private String version;

    /**
     * 浏览器名称
     */
    @Schema(description = "浏览器名称")
    @NotBlank(message = "浏览器名称不能为空")
    @Length(max = 30, message = "浏览器名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 浏览器程序下载地址
     */
    @Schema(description = "浏览器程序下载地址")
    @Length(max = 255, message = "浏览器程序下载地址长度不能超过 {max} 个字符")
    private String officialDownload;

    /**
     * 浏览器驱动下载地址
     */
    @Schema(description = "浏览器驱动下载地址")
    @Length(max = 255, message = "浏览器驱动下载地址长度不能超过 {max} 个字符")
    private String driverDownload;

    /**
     * 浏览器程序路径
     */
    @Schema(description = "浏览器程序路径")
    @NotBlank(message = "浏览器程序路径不能为空")
    @Length(max = 255, message = "浏览器程序路径长度不能超过 {max} 个字符")
    private String exePath;

    /**
     * 浏览器驱动路径
     */
    @Schema(description = "浏览器驱动路径")
    @NotBlank(message = "浏览器驱动路径不能为空")
    @Length(max = 255, message = "浏览器驱动路径长度不能超过 {max} 个字符")
    private String driverPath;

    /**
     * 浏览器配置文件路径
     */
    @Schema(description = "浏览器配置文件路径")
    @Length(max = 255, message = "浏览器配置文件路径长度不能超过 {max} 个字符")
    private String profilePath;

    /**
     * 浏览器描述
     */
    @Schema(description = "浏览器描述")
    @Length(max = 255, message = "浏览器描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private StatusTypeEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}