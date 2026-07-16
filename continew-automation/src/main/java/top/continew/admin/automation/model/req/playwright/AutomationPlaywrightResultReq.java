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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Playwright Runner 结果回传请求。
 *
 * @author Codex
 */
@Data
public class AutomationPlaywrightResultReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String status;

    private Boolean success;

    @JsonProperty("duration_ms")
    private Long durationMs;

    private String error;

    private Map<String, Object> raw;
}
