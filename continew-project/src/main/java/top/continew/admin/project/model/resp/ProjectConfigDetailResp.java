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

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

/**
 * 项目配置详情信息
 *
 * @author hagyao520
 * @since 2025/04/17 16:28
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "项目配置详情信息")
@EqualsAndHashCode(callSuper = true)
public class ProjectConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    @ExcelProperty(value = "项目名称", order = 2)
    private String name;

    /**
     * 项目简称
     */
    @Schema(description = "项目简称")
    @ExcelProperty(value = "项目简称", order = 3)
    private String abbreviate;

    /**
     * 项目成员
     */
    @Schema(description = "项目成员")
    @Assemble(prop = ":memberNames", container = ContainerConstants.USER_NICKNAME, handlerType = ManyToManyAssembleOperationHandler.class)
    private List<String> member;

    @ExcelProperty(value = "项目成员", converter = ExcelListConverter.class, order = 4)
    private List<String> memberNames;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    @ExcelProperty(value = "项目描述", order = 5)
    private String description;

    /**
     * 项目域名
     */
    @Schema(description = "项目域名")
    @ExcelProperty(value = "项目域名", order = 6)
    private String lastDomain;

    /**
     * 主线版本
     */
    @Schema(description = "主线版本")
    @ExcelProperty(value = "主线版本", order = 7)
    private String lastVersion;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 8)
    private DisEnableStatusEnum status;

    /**
     * 创建部门
     */
    @Schema(description = "创建部门")
    @ExcelProperty(value = "创建部门", order = 9)
    private Long deptId;

    /**
     * 更新IP
     */
    @Schema(description = "更新IP")
    @ExcelProperty(value = "更新IP", order = 10)
    private String updateIp;

    /**
     * 备注
     */
    @Schema(description = "备注")
    @ExcelProperty(value = "备注", order = 11)
    private String remark;

    /**
     * 版本
     */
    @Schema(description = "版本")
    @ExcelProperty(value = "版本", order = 12)
    private String version;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 13)
    private StatusTypeEnum delFlag;
}