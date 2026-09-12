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

package top.continew.admin.automation.model.req.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 变量提取规则请求参数
 *
 * @author Codex
 * @since 2026/09/12
 */
@Data
@Schema(description = "变量提取规则请求参数")
public class AutomationVariableExtractRuleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原始值，用于提取变量的源数据
     */
    @Schema(description = "原始值")
    @NotBlank(message = "原始值不能为空")
    @Length(max = 4096, message = "原始值长度不能超过 {max} 个字符")
    @JsonProperty("raw_value")
    private String rawValue;

    /**
     * 提取指令，描述如何从原始值中提取变量
     */
    @Schema(description = "提取指令")
    @NotBlank(message = "提取指令不能为空")
    @Length(max = 512, message = "提取指令长度不能超过 {max} 个字符")
    private String instruction;

    @Override
    public String toString() {
        return "AutomationVariableExtractRuleReq{" + "rawValue='[REDACTED]'" + ", instruction='" + instruction + '\'' + '}';
    }
}
