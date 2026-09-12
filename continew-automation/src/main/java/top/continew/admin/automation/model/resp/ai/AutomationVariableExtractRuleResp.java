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

package top.continew.admin.automation.model.resp.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 变量提取规则响应
 *
 * @author liuzhi
 * @since 2026-09-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "变量提取规则响应")
public class AutomationVariableExtractRuleResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提取模式，当前仅支持 "regex"
     */
    @Schema(description = "提取模式，当前仅支持 regex", example = "regex")
    private String mode;

    /**
     * 正则表达式，长度 1-300
     */
    @Schema(description = "正则表达式", example = "token=([a-zA-Z0-9]+)")
    private String pattern;

    /**
     * 捕获组索引，非负整数，0 表示整体匹配
     */
    @Schema(description = "捕获组索引", example = "1")
    private Integer group;

    /**
     * 生成此规则的原因说明
     */
    @Schema(description = "生成此规则的原因说明")
    private String reason;
}
