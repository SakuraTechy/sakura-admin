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

package top.continew.admin.automation.model.resp.ui;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;
import top.continew.admin.common.enums.StatusTypeEnum;

/** 统一手工/录制步骤详情；执行快照修改前由服务端保留原始录制副本。 */
@Data
public class AutomationUiStepDetailResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pid;
    private String id;
    private String name;
    private String remark;
    private String type;
    private Integer order;
    private StatusTypeEnum status;
    private String operationType;
    private String operationName;
    private String operationValue;
    private Boolean continueOnFailure;
    private String methodCode;
    private Integer methodVersion;
    private Map<String, Object> methodConfig;
    private String targetSummary;
    private String source;
    private String recordingId;
    private boolean recording;
    private boolean valueMasked;
    private boolean editable;
    private List<String> warnings;
    private List<AutomationUiStepConfigResp> configList;
}
