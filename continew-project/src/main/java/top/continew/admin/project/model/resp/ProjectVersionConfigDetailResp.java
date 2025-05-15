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

package top.continew.admin.project.model.resp;

import cn.crane4j.annotation.AssembleMethod;
import cn.crane4j.annotation.ContainerMethod;
import cn.crane4j.annotation.Mapping;
import cn.crane4j.annotation.condition.ConditionOnExpression;
import lombok.Data;

import java.io.Serial;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.admin.project.service.ProjectConfigService;

import java.time.*;

/**
 * 项目管理-版本配置详情信息
 *
 * @author hagyao520
 * @since 2025/04/28 15:33
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "项目管理-版本配置详情信息")
public class ProjectVersionConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @ConditionOnExpression("#target.projectName == null")
    @AssembleMethod(props = @Mapping(src = "name", ref = "projectName"), targetType = ProjectConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectConfigDetailResp.class))
    private Long projectId;

    @ExcelProperty(value = "所属项目", order = 2)
    private String projectName;

    /**
     * 版本名称
     */
    @Schema(description = "版本名称")
    @ExcelProperty(value = "版本名称", order = 3)
    private String name;

    /**
     * 版本描述
     */
    @Schema(description = "版本描述")
    @ExcelProperty(value = "版本描述", order = 4)
    private String description;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 5)
    private DisEnableStatusEnum status;

    /**
     * 更新人IP
     */
    @Schema(description = "更新人IP")
    @ExcelProperty(value = "更新人IP", order = 6)
    private String updateIp;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    @ExcelProperty(value = "删除标志（0删除 1存在）", order = 7)
    private Integer delFlag;
}