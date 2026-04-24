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

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.time.*;

/**
 * 自动化管理-项目配置信息
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
@Data
@Schema(description = "自动化管理-项目配置信息")
public class AutomationProjectConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目类型
     */
    @Schema(description = "项目类型")
    private String type;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    private String name;

    /**
     * 项目地址
     */
    @Schema(description = "项目地址")
    private String url;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    private String description;

    /**
     * 脚本路径
     */
    @Schema(description = "脚本路径")
    private String scriptPath;

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
