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

package top.continew.admin.automation.model.resp.playwright;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Playwright Runner 可执行用例响应。
 *
 * @author Codex
 */
@Data
public class AutomationPlaywrightCaseResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private Long sceneDbId;

    private String sceneId;

    private String sceneName;

    private String scene_name;

    private String caseId;

    private String name;

    /** 执行批次绑定的不可变定义 revision；非批次只读请求为空。 */
    private Long definitionRevisionId;

    /** Admin 已合并并审计来源的最终配置，Runner/CueCast 不得自行覆盖。 */
    private Map<String, Object> effectiveExecutionConfig;

    private String projectShortName;

    private String project_short_name;

    private String versionName;

    private String version_name;

    private Long projectEnvironmentId;

    private Long project_environment_id;

    private String projectEnvironmentName;

    private String project_environment_name;

    private String environmentOrigin;

    private String environment_origin;

    private String startUrl;

    private String start_url;

    private String windowSizeMode;

    private String window_size_mode;

    private String screenshotMode;

    private String screenshot_mode;

    private Integer pageErrorCheckEnabled;

    private Integer page_error_check_enabled;

    private Integer viewportWidth;

    private Integer viewport_width;

    private Integer viewportHeight;

    private Integer viewport_height;

    private List<Map<String, Object>> steps;
}
