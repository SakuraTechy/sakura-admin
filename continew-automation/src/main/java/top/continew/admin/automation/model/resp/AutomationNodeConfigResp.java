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

import cn.crane4j.annotation.AssembleMethod;
import cn.crane4j.annotation.ContainerMethod;
import cn.crane4j.annotation.Mapping;
import cn.crane4j.annotation.condition.ConditionOnExpression;
import lombok.Data;

import java.io.Serial;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.service.AutomationJenkinsConfigService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.time.*;

/**
 * 自动化管理-节点配置信息
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Data
@Schema(description = "自动化管理-节点配置信息")
public class AutomationNodeConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属Jenkins
     */
    @Schema(description = "所属Jenkins")
    @ConditionOnExpression("#target.jenkinsName == null")
    @AssembleMethod(props = @Mapping(src = "ip", ref = "jenkinsName"), targetType = AutomationJenkinsConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = AutomationJenkinsConfigResp.class))
    private Long jenkinsId;
    private String jenkinsName;

    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    private String name;

    /**
     * 节点类型
     */
    @Schema(description = "节点类型")
    private String type;

    /**
     * 节点json配置
     */
    @Schema(description = "节点json配置")
    private String json;

    /**
     * 节点xml配置
     */
    @Schema(description = "节点xml配置")
    private String xml;

    /**
     * 节点地址
     */
    @Schema(description = "节点地址")
    private String url;

    /**
     * 节点描述
     */
    @Schema(description = "节点描述")
    private AutomationNodeConfigDO.Description description;

    /**
     * 节点环境状态
     */
    @Schema(description = "节点环境状态")
    private AutomationNodeConfigDO.Active active;

    /**
     * 节点参数列表
     */
    @Schema(description = "节点参数列表")
    private List<AutomationNodeConfigDO.Config> configList;

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
     * 更新IP
     */
    @Schema(description = "更新IP")
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag;
}