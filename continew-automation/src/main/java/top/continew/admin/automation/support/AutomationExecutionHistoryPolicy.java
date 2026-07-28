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

package top.continew.admin.automation.support;

import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.hutool.json.JSONUtil;

/**
 * UI 自动化执行历史保留策略。
 */
public final class AutomationExecutionHistoryPolicy {

    static final int MAX_RECORD_COUNT = 100;
    static final int MAX_JSON_BYTES = 5 * 1024 * 1024;

    private AutomationExecutionHistoryPolicy() {
    }

    /**
     * 同时按条数和序列化字节数保留最新记录，防止 JSON 列无限增长。
     * 第一条为刚写入的最新结果，即使单条超限也保留，便于定位本次执行。
     */
    public static void trim(List<Object> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        int retainedCount = 0;
        int retainedBytes = 0;
        for (Object record : records) {
            int recordBytes = JSONUtil.toJsonStr(record).getBytes(StandardCharsets.UTF_8).length;
            if (retainedCount > 0 && (retainedCount >= MAX_RECORD_COUNT || retainedBytes + recordBytes > MAX_JSON_BYTES)) {
                break;
            }
            retainedBytes += recordBytes;
            retainedCount++;
        }
        if (retainedCount < records.size()) {
            records.subList(retainedCount, records.size()).clear();
        }
    }
}
