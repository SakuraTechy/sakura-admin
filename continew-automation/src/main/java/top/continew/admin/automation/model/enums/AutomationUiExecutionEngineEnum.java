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

package top.continew.admin.automation.model.enums;

/**
 * UI 自动化场景执行引擎。
 */
public enum AutomationUiExecutionEngineEnum {

    /**
     * 保持现有 Jenkins 执行链路。
     */
    JENKINS,

    /**
     * Playwright Runner 执行链路，阶段 5 接入测试计划和报告闭环时启用。
     */
    PLAYWRIGHT
}
