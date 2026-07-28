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

/**
 * UI 自动化场景轻量版本信息。
 */
@Data
public class AutomationUiSceneRevisionResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private LocalDateTime updateTime;

    /**
     * 执行状态窄表的单调版本号；旧数据无状态行时返回 0。
     */
    private Long executionRevision;
}
