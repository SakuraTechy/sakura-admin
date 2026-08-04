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

package top.continew.admin.automation.model.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.model.entity.BaseDO;

/** 基础设施任务日志。日志内容必须由执行节点脱敏并截断后再上传。 */
@Data
@TableName("automation_infrastructure_task_log")
public class AutomationInfrastructureTaskLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;
    private Long sequence;
    private String level;
    private String message;
}
