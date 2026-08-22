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

package top.continew.admin.automation.model.req.playwright;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Playwright/CDP 批次创建请求。
 */
@Data
public class AutomationPlaywrightBatchCreateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "场景标识不能为空")
    private String sceneKey;

    @NotBlank(message = "执行方式不能为空")
    private String executionType;

    private List<String> caseIds;

    /**
     * 由服务端按当前冻结定义选择全部可执行用例。
     *
     * <p>超大场景不能要求浏览器先下载完整用例 ID 集合；该字段与 caseIds 互斥。</p>
     */
    private Boolean selectAllCases;

    /** 浏览器读取到的定义版本；全选时必须一致，避免场景修改后执行了未确认的范围。 */
    private Long expectedDefinitionVersion;

    @NotNull(message = "产品环境不能为空")
    private Long projectEnvironmentId;

    /** 计划异步调度时显式透传执行人，避免后台线程丢失用户上下文。 */
    private String executeName;

    /** 计划异步调度时显式透传执行邮箱。 */
    private String executeEmail;

    /** 测试计划 ID；存在时批次结果写入对应计划的 testRecord。 */
    private String testPlanId;

    /** 正式报告 ID；存在时必须同时携带测试计划 ID。 */
    private String testReportId;

    /** 执行配置快照，仅用于历史诊断，不回写场景主数据。 */
    private Map<String, Object> executionConfig;

    /** 扩展 CDP 专用配置；为空表示旧客户端的 current-profile 兼容回放。 */
    @Valid
    private AutomationCdpPlaybackOptionsReq cdpOptions;
}
