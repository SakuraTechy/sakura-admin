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
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import top.continew.admin.automation.model.entity.AutomationBrowserConfigDO;
import top.continew.admin.automation.model.entity.AutomationJenkinsConfigDO;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.model.entity.AutomationProjectConfigDO;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

import java.time.*;

/**
 * 自动化管理-环境配置详情信息
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "自动化管理-环境配置详情信息")
public class AutomationEnvironmentConfigDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境类型
     */
    @Schema(description = "环境类型")
    @ExcelProperty(value = "环境类型", order = 2)
    private String type;

    /**
     * 环境名称
     */
    @Schema(description = "环境名称")
    @ExcelProperty(value = "环境名称", order = 3)
    private String name;

    /**
     * 环境描述
     */
    @Schema(description = "环境描述")
    @ExcelProperty(value = "环境描述", order = 4)
    private String description;

    /**
     * 环境项目信息
     */
    @Schema(description = "环境项目信息")
    @ExcelProperty(value = "环境项目信息", converter = ExcelListConverter.class, order = 5)
    private List<AutomationProjectConfigDO> projectConfig;

    /**
     * 环境Jenkins信息
     */
    @Schema(description = "环境Jenkins信息")
    @ExcelProperty(value = "环境Jenkins信息", converter = ExcelListConverter.class, order = 6)
    private List<AutomationJenkinsConfigDO> jenkinsConfig;

    /**
     * 环境节点信息
     */
    @Schema(description = "环境节点信息")
    @ExcelProperty(value = "环境节点信息", converter = ExcelListConverter.class, order = 7)
    private List<AutomationNodeConfigDO> nodeConfig;

    /**
     * 环境浏览器信息
     */
    @Schema(description = "环境浏览器信息")
    @ExcelProperty(value = "环境浏览器信息", converter = ExcelListConverter.class, order = 8)
    private List<AutomationBrowserConfigDO> browserConfig;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ExcelProperty(value = "状态", converter = ExcelBaseEnumConverter.class, order = 9)
    private StatusTypeEnum status;

    /**
     * 创建部门
     */
    @Schema(description = "创建部门")
    @ExcelProperty(value = "创建部门", order = 10)
    private Long deptId;

    /**
     * 更新IP
     */
    @Schema(description = "更新IP")
    @ExcelProperty(value = "更新IP", order = 11)
    private String updateIp;

    /**
     * 备注
     */
    @Schema(description = "备注")
    @ExcelProperty(value = "备注", order = 12)
    private String remark;

    /**
     * 版本
     */
    @Schema(description = "版本")
    @ExcelProperty(value = "版本", order = 13)
    private String version;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 14)
    private StatusTypeEnum delFlag;
}