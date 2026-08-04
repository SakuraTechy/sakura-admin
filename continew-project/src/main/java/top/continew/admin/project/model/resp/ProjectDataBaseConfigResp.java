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

package top.continew.admin.project.model.resp;

import cn.crane4j.annotation.AssembleMethod;
import cn.crane4j.annotation.ContainerMethod;
import cn.crane4j.annotation.Mapping;
import cn.crane4j.annotation.condition.ConditionOnExpression;
import lombok.Data;

import java.io.Serial;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.admin.project.service.ProjectConfigService;

import java.time.*;

/**
 * 项目管理-数据库配置信息
 *
 * @author hagyao520
 * @since 2025/05/08 18:00
 */
@Data
@Schema(description = "项目管理-数据库配置信息")
public class ProjectDataBaseConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    @Schema(description = "所属项目")
    @ConditionOnExpression("#target.projectName == null")
    @AssembleMethod(props = @Mapping(src = "name", ref = "projectName"), targetType = ProjectConfigService.class, method = @ContainerMethod(bindMethod = "get", resultType = ProjectConfigDetailResp.class))
    private Long projectId;
    private String projectName;

    @Schema(description = "基础设施步骤绑定键")
    private String bindingKey;

    /**
     * 数据库类型
     */
    @Schema(description = "数据库类型")
    private String type;

    /**
     * 数据库版本
     */
    @Schema(description = "数据库版本")
    private String version;

    /**
     * 数据库驱动
     */
    @Schema(description = "数据库驱动")
    private String driver;

    /**
     * 数据库IP
     */
    @Schema(description = "数据库IP")
    private String ip;

    /**
     * 数据库端口
     */
    @Schema(description = "数据库端口")
    private Integer port;

    /**
     * 数据库/模式
     */
    @Schema(description = "数据库/模式")
    private String dataBase;

    /**
     * 数据库用户名
     */
    @Schema(description = "数据库用户名")
    private String userName;

    /**
     * 数据库密码
     */
    @Schema(description = "数据库密码")
    private String passWord;

    /**
     * 数据库连接串
     */
    @Schema(description = "数据库连接串")
    private String url;

    /**
     * 数据库描述
     */
    @Schema(description = "数据库描述")
    private String description;

    /**
     * 数据库参数配置
     */
    @Schema(description = "数据库参数配置")
    private List<Object> configList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

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
     * 更新人IP
     */
    @Schema(description = "更新人IP")
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag;
}
