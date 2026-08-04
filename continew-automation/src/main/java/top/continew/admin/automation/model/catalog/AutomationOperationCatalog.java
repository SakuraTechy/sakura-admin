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

package top.continew.admin.automation.model.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * UI 自动化操作能力目录。
 *
 * <p>目录是跨前端、Selenium、Playwright Runner 和 CueCast/CDP 的版本化契约，
 * 字典只允许覆盖展示信息，不能单独声明执行能力。</p>
 *
 * @author Codex
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AutomationOperationCatalog {

    private String catalogVersion;

    /** 项目级灰度开关；关闭时前端明确使用旧表单，不调用 v2 保存协议。 */
    private Boolean v2Enabled = Boolean.TRUE;

    private List<OperationType> types = new ArrayList<>();

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class OperationType {

        private String typeCode;

        private String label;

        private Integer sort;

        private List<OperationMethod> methods = new ArrayList<>();
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class OperationMethod {

        private String methodCode;

        private Integer methodVersion;

        private String label;

        private String legacyAction;

        private String actionType;

        private List<String> aliases = new ArrayList<>();

        private List<Map<String, Object>> formSchema = new ArrayList<>();

        private Map<String, String> capabilities = new LinkedHashMap<>();

        private Map<String, Object> requirements = new LinkedHashMap<>();

        private Boolean authoringEnabled;

        private Boolean implemented;

        private Boolean runtimeReady;

        private Boolean permissionGranted;

        private Boolean enabled;

        private String disabledCode;

        private String disabledReason;
    }
}
