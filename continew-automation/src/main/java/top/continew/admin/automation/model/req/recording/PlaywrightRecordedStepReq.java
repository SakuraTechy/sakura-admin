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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Playwright 录制端上报的原始步骤。
 *
 * @author Codex
 */
@Data
public class PlaywrightRecordedStepReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Object id;

    @JsonProperty("step_index")
    private Integer stepIndex;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("target_selector")
    private String targetSelector;

    @JsonProperty("target_xpath")
    private String targetXpath;

    @JsonProperty("locator_meta")
    private Object locatorMeta;

    private Object value;

    @JsonProperty("value_masked")
    private Object valueMasked;

    private String url;

    private String description;

    @JsonProperty("wait_before")
    private Object waitBefore;

    @JsonProperty("is_overlay")
    private Object isOverlay;

    private String screenshot;

    @JsonProperty("screenshot_focus")
    private Object screenshotFocus;

    @JsonProperty("screenshot_focus_rect")
    private Object screenshotFocusRect;

    private Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void addExtra(String key, Object value) {
        this.extra.put(key, value);
    }
}
