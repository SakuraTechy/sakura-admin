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

import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.entity.BaseDO;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 测试定时任务实体。
 */
@Data
@TableName("test_timed_task")
public class TestTimedTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long testPlanId;
    private String testPlanName;
    private Long scheduleJobId;
    private String type;
    private String name;
    private String description;
    private String cronExpression;
    private String misfirePolicy;
    private Integer allowConcurrent;
    private Long projectEnvironmentId;
    private Long automationEnvironmentId;
    private String executeName;
    private String executeEmail;
    private LocalDateTime nextExecuteTime;
    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
