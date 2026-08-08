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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试定时任务实体。
 */
@Data
@TableName(value = "test_timed_task", autoResultMap = true)
public class TestTimedTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long testPlanId;
    private String testPlanName;
    private Long scheduleJobId;
    private String type;
    private String executionEngine;
    private String name;
    private String description;
    private String cronExpression;
    private String misfirePolicy;
    private Integer allowConcurrent;
    private Long projectEnvironmentId;
    private Long automationEnvironmentId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> executionConfig;

    private String executeName;
    private String executeEmail;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> notificationEmails;

    private LocalDateTime nextExecuteTime;
    private String status;
    private String scheduleSyncStatus = "PENDING";
    private String scheduleSyncError;
    private LocalDateTime scheduleSyncTime;
    private Long scheduleSyncVersion = 1L;
    private Integer scheduleSyncRetryCount = 0;
    private LocalDateTime scheduleSyncNextRetryTime;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
