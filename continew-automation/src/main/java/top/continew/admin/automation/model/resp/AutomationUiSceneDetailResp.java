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

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

import java.time.*;

/**
 * 自动化管理-UI自动化场景详情信息
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "自动化管理-UI自动化场景详情信息")
public class AutomationUiSceneDetailResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景ID
     */
    @Schema(description = "场景ID")
    @ExcelProperty(value = "场景ID", order = 2)
    private String sceneId;

    /**
     * 场景名称
     */
    @Schema(description = "场景名称")
    @ExcelProperty(value = "场景名称", order = 3)
    private String name;

    /**
     * 场景描述
     */
    @Schema(description = "场景描述")
    @ExcelProperty(value = "场景描述", order = 4)
    private String description;

    /**
     * 所属项目ID
     */
    @Schema(description = "所属项目ID")
    @ExcelProperty(value = "所属项目ID", order = 5)
    private Long projectId;

    /**
     * 所属项目名称
     */
    @Schema(description = "所属项目名称")
    @ExcelProperty(value = "所属项目名称", order = 6)
    private String projectName;

    /**
     * 所属项目版本ID
     */
    @Schema(description = "所属项目版本ID")
    @ExcelProperty(value = "所属项目版本ID", order = 7)
    private Long versionId;

    /**
     * 所属项目版本名称
     */
    @Schema(description = "所属项目版本名称")
    @ExcelProperty(value = "所属项目版本名称", order = 8)
    private String versionName;

    /**
     * 所属模块ID
     */
    @Schema(description = "所属模块ID")
    @ExcelProperty(value = "所属模块ID", order = 9)
    private Long moduleId;

    /**
     * 所属模块路径
     */
    @Schema(description = "所属模块路径")
    @ExcelProperty(value = "所属模块路径", order = 10)
    private String modulePath;

    /**
     * 场景等级
     */
    @Schema(description = "场景等级")
    @ExcelProperty(value = "场景等级", order = 11)
    private String level;

    /**
     * 场景状态
     */
    @Schema(description = "场景状态")
    @ExcelProperty(value = "场景状态", converter = ExcelBaseEnumConverter.class, order = 12)
    private StatusTypeEnum status;

    /**
     * 场景标签
     */
    @Schema(description = "场景标签")
    @ExcelProperty(value = "场景标签", converter = ExcelListConverter.class, order = 13)
    private List<Object> tags;

    /**
     * 场景用例信息
     */
    @Schema(description = "场景用例信息")
    @ExcelProperty(value = "场景用例信息", converter = ExcelListConverter.class, order = 14)
    private List<Object> caseList;

    /**
     * 关联的测试计划
     */
    @Schema(description = "关联的测试计划")
    @ExcelProperty(value = "关联的测试计划", converter = ExcelListConverter.class, order = 15)
    private List<Object> testPlanId;

    /**
     * 所属测试报告ID
     */
    @Schema(description = "所属测试报告ID")
    @ExcelProperty(value = "所属测试报告ID", order = 16)
    private Long reportId;

    /**
     * 调试记录
     */
    @Schema(description = "调试记录")
    @ExcelProperty(value = "调试记录", converter = ExcelListConverter.class, order = 17)
    private List<Object> debugRecord;

    /**
     * 执行状态
     */
    @Schema(description = "执行状态")
    @ExcelProperty(value = "执行状态", order = 18)
    private String executeStatus;

    /**
     * 执行结果
     */
    @Schema(description = "执行结果")
    @ExcelProperty(value = "执行结果", order = 19)
    private String executeResult;

    /**
     * 测试记录
     */
    @Schema(description = "测试记录")
    @ExcelProperty(value = "测试记录", converter = ExcelListConverter.class, order = 19)
    private List<Object> testRecord;

    /**
     * Jenkins构建编号
     */
    @Schema(description = "Jenkins构建编号")
    @ExcelProperty(value = "Jenkins构建编号", order = 19)
    private Integer buildNumber;

    /**
     * Jenkins控制台日志地址
     */
    @Schema(description = "Jenkins控制台日志地址")
    @ExcelProperty(value = "Jenkins控制台日志地址", order = 20)
    private String consoleUrl;

    /**
     * Jenkins测试报告地址
     */
    @Schema(description = "Jenkins测试报告地址")
    @ExcelProperty(value = "Jenkins测试报告地址", order = 21)
    private String testReportUrl;

    /**
     * 场景用例总数
     */
    @Schema(description = "场景用例总数")
    @ExcelProperty(value = "场景用例总数", order = 22)
    private Integer caseTotal;

    /**
     * 场景用例通过数
     */
    @Schema(description = "场景用例通过数")
    @ExcelProperty(value = "场景用例通过数", order = 23)
    private Integer casePass;

    /**
     * 场景用例失败数
     */
    @Schema(description = "场景用例失败数")
    @ExcelProperty(value = "场景用例失败数", order = 24)
    private Integer caseFail;

    /**
     * 场景用例跳过数
     */
    @Schema(description = "场景用例跳过数")
    @ExcelProperty(value = "场景用例跳过数", order = 25)
    private Integer caseSkip;

    /**
     * 场景用例通过率（场景用例通过数/场景用例总数）
     */
    @Schema(description = "场景用例通过率（场景用例通过数/场景用例总数）")
    @ExcelProperty(value = "场景用例通过率（场景用例通过数/场景用例总数）", order = 26)
    private String passRate;

    /**
     * 最后执行结果
     */
    @Schema(description = "最后执行结果")
    @ExcelProperty(value = "最后执行结果", order = 27)
    private String lastResult;

    /**
     * 场景用例步骤总数
     */
    @Schema(description = "场景用例步骤总数")
    @ExcelProperty(value = "场景用例步骤总数", order = 28)
    private Integer stepTotal;

    /**
     * 场景用例步骤成功数
     */
    @Schema(description = "场景用例步骤成功数")
    @ExcelProperty(value = "场景用例步骤成功数", order = 29)
    private Integer stepPass;

    /**
     * 场景用例步骤失败数
     */
    @Schema(description = "场景用例步骤失败数")
    @ExcelProperty(value = "场景用例步骤失败数", order = 30)
    private Integer stepFail;

    /**
     * 场景用例步骤跳过数
     */
    @Schema(description = "场景用例步骤跳过数")
    @ExcelProperty(value = "场景用例步骤跳过数", order = 31)
    private Integer stepSkip;

    /**
     * 修改人IP
     */
    @Schema(description = "修改人IP")
    @ExcelProperty(value = "修改人IP", order = 32)
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    @ExcelProperty(value = "删除标志（3正常 4异常）", converter = ExcelBaseEnumConverter.class, order = 33)
    private StatusTypeEnum delFlag;
}