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

import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.StatusTypeEnum;

/**
 * 自动化管理-环境配置实体
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Data
@TableName(value = "automation_environment_config", autoResultMap = true)
public class AutomationEnvironmentConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境类型
     */
    private String type;

    /**
     * 环境名称
     */
    private String name;

    /**
     * 环境描述
     */
    private String description;

    /**
     * 环境项目信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationProjectConfigDO> projectConfig;

    /**
     * 环境Jenkins信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationJenkinsConfigDO> jenkinsConfig;

    /**
     * 环境节点信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationNodeConfigDO> nodeConfig;

    /**
     * 环境浏览器信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationBrowserConfigDO> browserConfig;

    /**
     * 状态
     */
    private StatusTypeEnum status;

    /**
     * 创建部门
     */
    private Long deptId;

    /**
     * 更新IP
     */
    private String updateIp;

    /**
     * 备注
     */
    private String remark;

    /**
     * 版本
     */
    private String version;

    /**
     * 删除标志（3正常 4异常）
     */
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
