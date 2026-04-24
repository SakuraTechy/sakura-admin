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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试计划实体。
 */
@Data
@TableName(value = "test_plan", autoResultMap = true)
public class TestPlanDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private String projectName;
    private String type;
    private String name;
    private String abbreviate;
    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> memberIds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> principalIds;

    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> timedTasksConfig;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> projectConfig;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> automationConfig;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> functionalScene;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> uiTestScene;

    private Integer sceneCount;
    private Integer executedCount;
    private Integer passedCount;
    private BigDecimal testProgress;
    private Long runTime;
    private String status;
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
