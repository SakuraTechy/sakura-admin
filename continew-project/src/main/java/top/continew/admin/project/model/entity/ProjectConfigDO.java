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

/**
 * 项目管理-项目配置实体
 *
 * @author hagyao520
 * @since 2025/04/15 11:56
 */
@Data
@TableName(value = "project_config", autoResultMap = true)
public class ProjectConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目简称
     */
    private String abbreviate;

    /**
     * 项目成员
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> member;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目域名
     */
    private String lastDomain;

    /**
     * 主线版本
     */
    private String lastVersion;

    /**
     * 状态
     */
    private DisEnableStatusEnum status;

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

    /** UI 自动化操作目录 v2 灰度开关；历史项目默认开启，关闭时前端走旧表单。 */
    private Boolean automationOperationCatalogV2 = Boolean.TRUE;
}
