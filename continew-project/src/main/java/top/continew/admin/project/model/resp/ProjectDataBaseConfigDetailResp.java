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
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import top.continew.admin.common.config.excel.DictExcelProperty;
import top.continew.admin.common.config.excel.ExcelDictConverter;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

import java.time.*;

/**
 * 项目管理-数据库配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/08 18:00
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "项目管理-数据库配置详情信息")
public class ProjectDataBaseConfigDetailResp extends BaseDetailResp {

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
     * 数据库类型
     */
    @Schema(description = "数据库类型")
    @ExcelProperty(value = "数据库类型", converter = ExcelDictConverter.class, order = 3)
    @DictExcelProperty("database_type")
    private String type;

    /**
     * 数据库版本
     */
    @Schema(description = "数据库版本")
    @ExcelProperty(value = "数据库版本", order = 4)
    private String version;

    /**
     * 数据库驱动
     */
    @Schema(description = "数据库驱动")
    @ExcelProperty(value = "数据库驱动", order = 5)
    private String driver;

    /**
     * 数据库IP
     */
    @Schema(description = "数据库IP")
    @ExcelProperty(value = "数据库IP", order = 6)
    private String ip;

    /**
     * 数据库端口
     */
    @Schema(description = "数据库端口")
    @ExcelProperty(value = "数据库端口", order = 7)
    private Integer port;

    /**
     * 数据库/模式
     */
    @Schema(description = "数据库/模式")
    @ExcelProperty(value = "数据库/模式", order = 8)
    private String dataBase;

    /**
     * 数据库用户名
     */
    @Schema(description = "数据库用户名")
    @ExcelProperty(value = "数据库用户名", order = 9)
    private String userName;

    /**
     * 数据库密码
     */
    @Schema(description = "数据库密码")
    @ExcelProperty(value = "数据库密码", order = 10)
    private String passWord;

    /**
     * 数据库连接串
     */
    @Schema(description = "数据库连接串")
    @ExcelProperty(value = "数据库连接串", order = 11)
    private String url;

    /**
     * 数据库描述
     */
    @Schema(description = "数据库描述")
    @ExcelProperty(value = "数据库描述", order = 12)
    private String description;

    /**
     * 数据库参数配置
     */
    @Schema(description = "数据库参数配置")
    @ExcelProperty(value = "服务器参数配置", converter = ExcelListConverter.class, order = 13)
    private List<Object> configList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 14)
    private DisEnableStatusEnum status;

    /**
     * 更新人IP
     */
    @Schema(description = "更新人IP")
    @ExcelProperty(value = "更新人IP", order = 15)
    private String updateIp;

    /**
     * 删除标志（0删除 1存在）
     */
    @Schema(description = "删除标志（0删除 1存在）")
    @ExcelProperty(value = "删除标志（0删除 1存在）", order = 16)
    private Integer delFlag;
}