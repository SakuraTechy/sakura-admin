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

/**
 * 测试定时任务业务执行记录。
 */
@Data
@TableName(value = "test_timed_task_run", autoResultMap = true)
public class TestTimedTaskRunDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long timedTaskId;
    private String taskName;
    private Long testPlanId;
    private String testPlanName;
    private Long testReportId;
    private String triggerMode;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> notificationEmails;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long runTime;
    private String buildNumber;
    private String consoleUrl;
    private String reportUrl;
    private String failureReason;
    private String notificationStatus;
    private String notificationError;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
