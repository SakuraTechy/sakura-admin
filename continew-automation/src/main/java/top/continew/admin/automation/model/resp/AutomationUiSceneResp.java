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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.time.*;

/**
 * 自动化管理-UI自动化场景信息
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Data
@Schema(description = "自动化管理-UI自动化场景信息")
public class AutomationUiSceneResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景ID
     */
    @Schema(description = "场景ID")
    private String sceneId;

    /**
     * 场景名称
     */
    @Schema(description = "场景名称")
    private String name;

    /**
     * 场景描述
     */
    @Schema(description = "场景描述")
    private String description;

    /**
     * 所属项目ID
     */
    @Schema(description = "所属项目ID")
    private Long projectId;

    /**
     * 所属项目名称
     */
    @Schema(description = "所属项目名称")
    private String projectName;

    /**
     * 所属项目版本ID
     */
    @Schema(description = "所属项目版本ID")
    private Long versionId;

    /**
     * 所属项目版本名称
     */
    @Schema(description = "所属项目版本名称")
    private String versionName;

    /**
     * 所属模块ID
     */
    @Schema(description = "所属模块ID")
    private Long moduleId;

    /**
     * 所属模块路径
     */
    @Schema(description = "所属模块路径")
    private String modulePath;

    /**
     * 场景等级
     */
    @Schema(description = "场景等级")
    private String level;

    /**
     * 场景状态
     */
    @Schema(description = "场景状态")
    private StatusTypeEnum status;

    /**
     * 场景标签
     */
    @Schema(description = "场景标签")
    private List<Object> tags;

    /**
     * 场景用例信息
     */
    @Schema(description = "场景用例信息")
    private List<Object> caseList;

    @Schema(description = "场景定义版本")
    private Long definitionVersion;

    /**
     * 关联的测试计划
     */
    @Schema(description = "关联的测试计划")
    private List<Object> testPlanId;

    /**
     * 所属测试报告ID
     */
    @Schema(description = "所属测试报告ID")
    private Long reportId;

    /**
     * 调试记录
     */
    @Schema(description = "调试记录")
    @JsonPropertyOrder(alphabetic = false)
    private List<Object> debugRecord;

    /**
     * 测试记录
     */
    @Schema(description = "测试记录")
    @JsonPropertyOrder(alphabetic = false)
    private List<Object> testRecord;

    /**
     * 执行状态
     */
    @Schema(description = "执行状态")
    private String executeStatus;

    /**
     * 执行结果
     */
    @Schema(description = "执行结果")
    private String executeResult;

    /**
     * Jenkins构建编号
     */
    @Schema(description = "Jenkins构建编号")
    private Integer buildNumber;

    /**
     * Jenkins控制台日志地址
     */
    @Schema(description = "Jenkins控制台日志地址")
    private String consoleUrl;

    /**
     * Jenkins测试报告地址
     */
    @Schema(description = "Jenkins测试报告地址")
    private String testReportUrl;

    /**
     * 场景用例总数
     */
    @Schema(description = "场景用例总数")
    private Integer caseTotal;

    /**
     * 场景用例通过数
     */
    @Schema(description = "场景用例通过数")
    private Integer casePass;

    /**
     * 场景用例失败数
     */
    @Schema(description = "场景用例失败数")
    private Integer caseFail;

    /**
     * 场景用例跳过数
     */
    @Schema(description = "场景用例跳过数")
    private Integer caseSkip;

    /**
     * 场景用例通过率（场景用例通过数/场景用例总数）
     */
    @Schema(description = "场景用例通过率（场景用例通过数/场景用例总数）")
    private String passRate;

    /**
     * 最后执行结果
     */
    @Schema(description = "最后执行结果")
    private String lastResult;

    /**
     * 场景用例步骤总数
     */
    @Schema(description = "场景用例步骤总数")
    private Integer stepTotal;

    /**
     * 场景用例步骤成功数
     */
    @Schema(description = "场景用例步骤成功数")
    private Integer stepPass;

    /**
     * 场景用例步骤失败数
     */
    @Schema(description = "场景用例步骤失败数")
    private Integer stepFail;

    /**
     * 场景用例步骤跳过数
     */
    @Schema(description = "场景用例步骤跳过数")
    private Integer stepSkip;

    /**
     * 修改人
     */
    @Schema(description = "修改人")
    private Long updateUser;

    /**
     * 修改时间
     */
    @Schema(description = "修改时间")
    private LocalDateTime updateTime;

    /**
     * 执行状态窄表的单调版本号。
     */
    @Schema(description = "执行版本号")
    private Long executionRevision;

    /**
     * 修改人IP
     */
    @Schema(description = "修改人IP")
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag;
}
