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
import top.continew.admin.common.enums.StatusTypeEnum;

import java.time.*;

/**
 * 创建或修改自动化管理-UI自动化场景参数
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Data
@Schema(description = "创建或修改自动化管理-UI自动化场景参数")
public class AutomationUiSceneReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景ID
     */
    @Schema(description = "场景ID")
    @NotBlank(message = "场景ID不能为空")
    @Length(max = 64, message = "场景ID长度不能超过 {max} 个字符")
    private String sceneId;

    /**
     * 场景名称
     */
    @Schema(description = "场景名称")
    @NotBlank(message = "场景名称不能为空")
    @Length(max = 64, message = "场景名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 场景描述
     */
    @Schema(description = "场景描述")
    @Length(max = 255, message = "场景描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 所属项目ID
     */
    @Schema(description = "所属项目ID")
    @NotNull(message = "所属项目ID不能为空")
    private Long projectId;
    private String projectName;

    /**
     * 所属项目版本ID
     */
    @Schema(description = "所属项目版本ID")
    @NotNull(message = "所属项目版本ID不能为空")
    private Long versionId;
    private String versionName;

    /**
     * 所属模块ID
     */
    @Schema(description = "所属模块ID")
    @NotNull(message = "所属模块ID不能为空")
    private Long moduleId;
    private String modulePath;

    /**
     * 场景等级
     */
    @Schema(description = "场景等级")
    @Length(max = 64, message = "场景等级长度不能超过 {max} 个字符")
    private String level;

    /**
     * 场景状态
     */
    @Schema(description = "场景状态")
    private StatusTypeEnum status;

    /**
     * 场景标签
     */
    @Schema(description = "场景标签")
    //    @NotEmpty(message = "场景标签不能为空")
    @Size(max = 10, message = "场景标签最多支持 {max} 人")
    private List<Object> tags;

    /**
     * 删除标志（3正常 4异常）
     */
    @Schema(description = "删除标志（3正常 4异常）")
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;

    //    /**
    //     * 场景用例拖拽-原始节点
    //     */
    //    @Schema(description = "原始节点")
    //    private CaseDO dragNode;
    //
    //    /**
    //     * 场景用例拖拽-目标节点
    //     */
    //    @Schema(description = "目标节点")
    //    private CaseDO dropNode;
    //
    //    /**
    //     * 场景用例拖拽-目标位置
    //     */
    //    @Schema(description = "目标位置")
    //    private Integer dropPosition;
}