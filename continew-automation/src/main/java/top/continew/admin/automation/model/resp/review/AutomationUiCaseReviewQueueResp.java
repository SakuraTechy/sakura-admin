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

package top.continew.admin.automation.model.resp.review;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutomationUiCaseReviewQueueResp {
    private List<Item> pending;
    private List<Item> submitted;
    private List<Item> outdated;
    private List<Item> dueSoon;

    @Data
    @Builder
    public static class Item {
        private Long reviewId;
        private Long sceneId;
        private String sceneName;
        private Long projectId;
        private String projectName;
        private String caseId;
        private String caseName;
        private Integer roundNo;
        private String status;
        private Long submitterId;
        private String submitterName;
        private LocalDateTime submittedAt;
        private LocalDateTime dueAt;
        private boolean overdue;
        private Integer requiredApprovals;
        private Integer approvedCount;
        private Long version;
    }
}
