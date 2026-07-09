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

package top.continew.admin.automation.model.req.recording;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Playwright 录制端上报的用例。
 *
 * @author Codex
 */
@Data
public class PlaywrightRecordedCaseReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Object id;

    @NotBlank(message = "录制用例名称不能为空")
    private String name;

    private String status;

    @JsonProperty("start_url")
    private String startUrl;

    private String description;

    @JsonProperty("screenshot_mode")
    private String screenshotMode;

    @JsonProperty("page_error_check_enabled")
    private Integer pageErrorCheckEnabled;

    @JsonProperty("window_size_mode")
    private String windowSizeMode;

    @JsonProperty("viewport_width")
    private Integer viewportWidth;

    @JsonProperty("viewport_height")
    private Integer viewportHeight;

    @Valid
    @NotEmpty(message = "录制步骤不能为空")
    private List<PlaywrightRecordedStepReq> steps;

    private Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void addExtra(String key, Object value) {
        this.extra.put(key, value);
    }
}
