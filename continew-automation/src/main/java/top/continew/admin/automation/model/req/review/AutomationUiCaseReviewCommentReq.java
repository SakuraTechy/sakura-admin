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

package top.continew.admin.automation.model.req.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AutomationUiCaseReviewCommentReq {
    private Long parentId;
    @Pattern(regexp = "CASE|STEP")
    private String nodeType = "CASE";
    @Size(max = 128)
    private String stepId;
    @Size(max = 255)
    private String fieldPath;
    @Pattern(regexp = "BLOCKER|MAJOR|MINOR|SUGGESTION")
    private String severity;
    @NotBlank
    @Size(max = 4000)
    private String content;
}
