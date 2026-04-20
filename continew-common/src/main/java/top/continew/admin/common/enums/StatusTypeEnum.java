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

package top.continew.admin.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.continew.admin.common.constant.UiConstants;
import top.continew.starter.core.enums.BaseEnum;

/**
 * 状态类型枚举
 *
 * @author hagyao520
 * @since 2025-05-21 16:47:32
 */
@Getter
@RequiredArgsConstructor
public enum StatusTypeEnum implements BaseEnum<Integer> {

    /**
     * 启用
     */
    ENABLE(1, "启用", UiConstants.COLOR_SUCCESS),

    /**
     * 禁用
     */
    DISABLE(2, "禁用", UiConstants.COLOR_ERROR),

    /**
     * 正常
     */
    NORMAL(3, "正常", UiConstants.COLOR_SUCCESS),

    /**
     * 异常
     */
    ABNORMAL(4, "异常", UiConstants.COLOR_ERROR),

    /**
     * 在线
     */
    ONLINE(5, "在线", UiConstants.COLOR_SUCCESS),

    /**
     * 离线
     */
    OFFLINE(6, "离线", UiConstants.COLOR_DEFAULT),

    /**
     * 空闲
     */
    IDLE(7, "空闲", UiConstants.COLOR_SUCCESS),

    /**
     * 使用中
     */
    IN_USE(8, "使用中", UiConstants.COLOR_ERROR),

    /**
     * 未使用
     */
    UNUSED(9, "未使用", UiConstants.COLOR_DEFAULT);

    private final Integer value;
    private final String description;
    private final String color;
}
