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

package top.continew.admin.automation.model.req;

import lombok.Data;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.automation.model.entity.AutomationBrowserConfigDO;
import top.continew.admin.automation.model.entity.AutomationJenkinsConfigDO;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.model.entity.AutomationProjectConfigDO;
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改自动化管理-环境配置参数
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Data
@Schema(description = "创建或修改自动化管理-环境配置参数")
public class AutomationEnvironmentConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境类型
     */
    @Schema(description = "环境类型")
    @NotBlank(message = "环境类型不能为空")
    @Length(max = 30, message = "环境类型长度不能超过 {max} 个字符")
    private String type;

    /**
     * 环境名称
     */
    @Schema(description = "环境名称")
    @NotBlank(message = "环境名称不能为空")
    @Length(max = 30, message = "环境名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 环境描述
     */
    @Schema(description = "环境描述")
    @Length(max = 255, message = "环境描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 环境项目信息
     */
    @Schema(description = "环境项目信息")
    @NotEmpty(message = "环境项目信息不能为空")
    @Size(max = 10, message = "环境项目信息最多支持 {max} 个")
    private List<AutomationProjectConfigDO> projectConfig;

    /**
     * 环境Jenkins信息
     */
    @Schema(description = "环境Jenkins信息")
    @NotEmpty(message = "环境Jenkins信息不能为空")
    @Size(max = 10, message = "环境Jenkins信息最多支持 {max} 个")
    private List<AutomationJenkinsConfigDO> jenkinsConfig;

    /**
     * 环境节点信息
     */
    @Schema(description = "环境节点信息")
    @NotEmpty(message = "环境节点信息不能为空")
    @Size(max = 10, message = "环境节点信息最多支持 {max} 个")
    private List<AutomationNodeConfigDO> nodeConfig;

    /**
     * 环境节点信息
     */
    @Schema(description = "环境浏览器信息")
    @NotEmpty(message = "环境浏览器信息不能为空")
    @Size(max = 10, message = "环境浏览器信息最多支持 {max} 个")
    private List<AutomationBrowserConfigDO> browserConfig;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private StatusTypeEnum status;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}