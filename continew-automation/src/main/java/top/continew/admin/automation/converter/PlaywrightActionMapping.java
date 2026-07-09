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

package top.continew.admin.automation.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Playwright action 与 admin 展示字段映射。
 *
 * @author Codex
 */
public final class PlaywrightActionMapping {

    public static final String OPERATION_VALUE_CUSTOM = "pw-custom";

    private static final ActionDisplay CUSTOM = new ActionDisplay("Playwright 操作", "自定义动作",
        OPERATION_VALUE_CUSTOM);

    private static final Map<String, ActionDisplay> ACTIONS = new LinkedHashMap<>();

    static {
        put("navigate", "浏览器操作", "打开页面", "pw-navigate");
        put("click", "浏览器操作", "点击", "pw-click");
        put("double_click", "浏览器操作", "双击", "pw-double-click");
        put("right_click", "浏览器操作", "右键点击", "pw-right-click");
        put("input", "浏览器操作", "输入", "pw-input");
        put("key", "浏览器操作", "按键", "pw-key");
        put("hover", "浏览器操作", "悬停", "pw-hover");
        put("wait", "浏览器操作", "等待", "pw-wait");
        put("scroll", "浏览器操作", "滚动", "pw-scroll");
        put("assert_text", "断言操作", "文本断言", "pw-assert-text");
        put("assert_json", "断言操作", "JSON 断言", "pw-assert-json");
        put("assert_request", "网络断言", "请求断言", "pw-assert-request");
        put("assert_response", "网络断言", "响应断言", "pw-assert-response");
        put("assert_request_count", "网络断言", "请求数量断言", "pw-assert-request-count");
        put("network_mock", "网络操作", "网络 Mock", "pw-network-mock");
        put("network_replay", "网络操作", "网络回放", "pw-network-replay");
        put("file_upload", "文件操作", "文件上传", "pw-file-upload");
        put("assert_download", "文件操作", "下载断言", "pw-assert-download");
        put("click_open_page", "浏览器操作", "点击并打开新窗口", "pw-click-open-page");
        put("switch_page", "浏览器操作", "切换窗口", "pw-switch-page");
        put("close_page", "浏览器操作", "关闭窗口", "pw-close-page");
    }

    private PlaywrightActionMapping() {
    }

    public static ActionDisplay resolve(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return CUSTOM;
        }
        ActionDisplay display = ACTIONS.get(actionType.trim());
        return display == null ? CUSTOM : display;
    }

    public static boolean isKnown(String actionType) {
        return actionType != null && ACTIONS.containsKey(actionType.trim());
    }

    private static void put(String actionType, String operationType, String operationName, String operationValue) {
        ACTIONS.put(actionType, new ActionDisplay(operationType, operationName, operationValue));
    }

    public record ActionDisplay(String operationType, String operationName, String operationValue) {
    }
}
