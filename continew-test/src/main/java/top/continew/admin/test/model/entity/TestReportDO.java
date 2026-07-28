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

package top.continew.admin.test.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.entity.BaseDO;

import java.io.Serial;
import java.util.Map;

/**
 * 测试报告实体。
 */
@Data
@TableName(value = "test_report", autoResultMap = true)
public class TestReportDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private String projectName;
    private String versionName;
    private Long testPlanId;
    private String testPlanName;
    private String name;
    private String description;
    private String triggerMode;
    private String executeMode;
    private String reportType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> projectConfig;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> automationConfig;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> runtimeEnvironment;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> statisticAnalysis;

    private Long runTime;
    private String buildNumber;
    private String consoleUrl;
    private String reportUrl;
    private String videoUrl;
    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
