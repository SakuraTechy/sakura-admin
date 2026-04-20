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

package top.continew.admin.project.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.admin.project.model.entity.ProjectModuleConfigDO;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建或修改项目管理-模块配置参数
 *
 * @author hagyao520
 * @since 2025/06/06 17:44
 */
@Data
@Schema(description = "拖拽项目管理-模块配置参数")
public class ProjectModuleDragReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原始节点
     */
    @Schema(description = "原始节点")
    private ProjectModuleConfigDO dragNode;

    /**
     * 目标节点
     */
    @Schema(description = "目标节点")
    private ProjectModuleConfigDO dropNode;

    /**
     * 目标位置
     */
    @Schema(description = "目标位置")
    private Integer dropPosition;
}