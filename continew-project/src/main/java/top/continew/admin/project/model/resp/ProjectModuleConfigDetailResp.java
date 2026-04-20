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

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.admin.project.service.ProjectModuleConfigService;
import top.continew.admin.project.service.ProjectVersionConfigService;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;

import java.time.*;

/**
 * 项目管理-模块配置详情信息
 *
 * @author hagyao520
 * @since 2025/06/06 17:44
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "项目管理-模块配置详情信息")
public class ProjectModuleConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目ID
     */
    @Schema(description = "项目ID")
    //    @ConditionOnExpression("#target.projectName == null")
    //    @AssembleMethod(props = @Mapping(src = "name", ref = "projectName"), targetType = ProjectConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectConfigDetailResp.class))
    //    private Long projectId;
    //
    //    @ExcelProperty(value = "所属项目", order = 2)
    //    private String projectName;

    @ExcelProperty(value = "所属项目", order = 2)
    private Long projectId;
    /**
     * 版本ID
     */
    @Schema(description = "版本ID")
    @ConditionOnExpression("#target.versionName == null")
    @AssembleMethod(props = @Mapping(src = "name", ref = "versionName"), targetType = ProjectVersionConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectVersionConfigDetailResp.class))
    private Long versionId;

    @ExcelProperty(value = "所属版本", order = 3)
    private String versionName;

    /**
     * 父模块ID
     */
    @Schema(description = "父模块ID")
    @ConditionOnExpression("#target.parentName == null")
    @AssembleMethod(props = @Mapping(src = "name", ref = "parentName"), targetType = ProjectModuleConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectModuleConfigDetailResp.class))
    private Long parentId;

    @ExcelProperty(value = "所属模块", order = 4)
    private String parentName;

    /**
     * 模块名称
     */
    @Schema(description = "模块名称")
    @ExcelProperty(value = "模块名称", order = 5)
    private String name;

    /**
     * 模块描述
     */
    @Schema(description = "模块描述")
    @ExcelProperty(value = "模块描述", order = 6)
    private String description;

    /**
     * 模块排序
     */
    @Schema(description = "模块排序")
    @ExcelProperty(value = "模块排序", order = 7)
    private Integer sort;

    /**
     * 模块路径
     */
    @Schema(description = "模块路径")
    @ExcelProperty(value = "模块路径", order = 8)
    private String path;

    /**
     * 模块下数据总数
     */
    @Schema(description = "模块下数据总数")
    @ExcelProperty(value = "模块下数据总数", order = 9)
    private Long count;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 10)
    private StatusTypeEnum status;

    /**
     * 更新人IP
     */
    @Schema(description = "更新人IP")
    @ExcelProperty(value = "更新人IP", order = 11)
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 12)
    private StatusTypeEnum delFlag;
}