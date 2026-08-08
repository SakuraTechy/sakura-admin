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

package top.continew.admin.automation.model.entity.ui;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 用例级执行默认值；浏览器步骤和纯基础设施步骤共享同一配置来源。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaseExecutionConfigDO {

    private String startUrl;
    private String windowSizeMode;
    private Integer viewportWidth;
    private Integer viewportHeight;
    private String screenshotMode;
    private Integer pageErrorCheckEnabled;
}
