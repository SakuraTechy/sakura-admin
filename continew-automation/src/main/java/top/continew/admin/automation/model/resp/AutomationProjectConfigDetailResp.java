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

package top.continew.admin.automation.model.resp;

import lombok.Data;

import java.io.Serial;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import top.continew.admin.common.config.excel.DictExcelProperty;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;

import java.time.*;

/**
 * 自动化管理-项目配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "自动化管理-项目配置详情信息")
public class AutomationProjectConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目类型
     */
    @Schema(description = "项目类型")
    @ExcelProperty(value = "项目类型", order = 2)
    @DictExcelProperty("automation_type")
    private String type;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    @ExcelProperty(value = "项目名称", order = 3)
    private String name;

    /**
     * 项目地址
     */
    @Schema(description = "项目地址")
    @ExcelProperty(value = "项目地址", order = 4)
    private String url;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    @ExcelProperty(value = "项目描述", order = 5)
    private String description;

    /**
     * 脚本路径
     */
    @Schema(description = "脚本路径")
    @ExcelProperty(value = "脚本路径", order = 6)
    private String scriptPath;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 7)
    private DisEnableStatusEnum status;

    /**
     * 更新IP
     */
    @Schema(description = "更新IP")
    @ExcelProperty(value = "更新IP", order = 8)
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 9)
    private StatusTypeEnum delFlag;
}
