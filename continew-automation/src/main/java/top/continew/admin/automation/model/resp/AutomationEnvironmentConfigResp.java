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

import top.continew.admin.automation.model.entity.AutomationBrowserConfigDO;
import top.continew.admin.automation.model.entity.AutomationJenkinsConfigDO;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.time.*;

/**
 * 自动化管理-环境配置信息
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Data
@Schema(description = "自动化管理-环境配置信息")
public class AutomationEnvironmentConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境类型
     */
    @Schema(description = "环境类型")
    private String type;

    /**
     * 环境名称
     */
    @Schema(description = "环境名称")
    private String name;

    /**
     * 环境描述
     */
    @Schema(description = "环境描述")
    private String description;

    /**
     * 环境项目信息
     */
    @Schema(description = "环境项目信息")
    private List<Object> projectConfig;

    /**
     * 环境Jenkins信息
     */
    @Schema(description = "环境Jenkins信息")
    private List<AutomationJenkinsConfigDO> jenkinsConfig;

    /**
     * 环境节点信息
     */
    @Schema(description = "环境节点信息")
    private List<AutomationNodeConfigDO> nodeConfig;

    /**
     * 环境浏览器信息
     */
    @Schema(description = "环境浏览器信息")
    private List<AutomationBrowserConfigDO> browserConfig;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private StatusTypeEnum status;

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
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}