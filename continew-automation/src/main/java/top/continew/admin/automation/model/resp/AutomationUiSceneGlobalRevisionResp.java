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

package top.continew.admin.automation.model.resp;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/** 场景执行状态的全局单调版本，仅用于判断当前作用域是否需要刷新。 */
@Data
public class AutomationUiSceneGlobalRevisionResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long sceneDbId;
    private Long globalExecutionRevision;
    private LocalDateTime updateTime;
}
