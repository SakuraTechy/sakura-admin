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

package top.continew.admin.project.model.entity;

import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.starter.security.crypto.annotation.FieldEncrypt;

/**
 * 项目管理-服务器配置实体
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Data
@TableName(value = "project_server_config", autoResultMap = true)
public class ProjectServerConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    private Long projectId;

    /** 环境中引用服务器的稳定逻辑键，基础设施步骤只保存该键。 */
    private String bindingKey;

    /**
     * 服务器类型
     */
    private String type;

    /**
     * 服务器版本
     */
    private String version;

    /**
     * 服务器IP
     */
    private String ip;

    /**
     * 服务器端口
     */
    private Integer port;

    /**
     * 服务器用户名
     */
    private String userName;

    /**
     * 服务器密码
     */
    @FieldEncrypt
    private String passWord;

    /**
     * 服务器描述
     */
    private String description;

    /**
     * 服务器参数配置
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> configList;

    /**
     * 状态
     */
    private DisEnableStatusEnum status;

    /**
     * 更新人IP
     */
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
