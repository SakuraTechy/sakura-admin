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
 * 项目管理-数据库配置实体
 *
 * @author hagyao520
 * @since 2025/05/08 18:00
 */
@Data
@TableName(value = "project_data_base_config", autoResultMap = true)
public class ProjectDataBaseConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    private Long projectId;

    /** 环境中引用数据库的稳定逻辑键，基础设施步骤只保存该键。 */
    private String bindingKey;

    /**
     * 数据库类型
     */
    private String type;

    /**
     * 数据库版本
     */
    private String version;

    /**
     * 数据库驱动
     */
    private String driver;

    /**
     * 数据库IP
     */
    private String ip;

    /**
     * 数据库端口
     */
    private Integer port;

    /**
     * 数据库/模式
     */
    private String dataBase;

    /**
     * 数据库用户名
     */
    private String userName;

    /**
     * 数据库密码
     */
    // 数据库连接凭据必须加密落库，查询时由统一字段加密拦截器解密。
    @FieldEncrypt
    private String passWord;

    /**
     * 数据库连接串
     */
    private String url;

    /**
     * 数据库描述
     */
    private String description;

    /**
     * 数据库参数配置
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
