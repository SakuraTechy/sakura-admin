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
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutomationUiCaseReviewResp {
    private Long id;
    private Long sceneId;
    private String caseId;
    private String caseName;
    private Long definitionRevisionId;
    private Long definitionVersion;
    private String caseContentHash;
    private String hashSchemaVersion;
    private Integer roundNo;
    private String status;
    private Long submitterId;
    private String submitterName;
    private LocalDateTime submittedAt;
    private Integer requiredApprovals;
    private String summary;
    private LocalDateTime completedAt;
    private Long version;
    private boolean outdated;
    private String currentCaseContentHash;
    private String checkRunStatus;
    private Policy policy;
    private Metrics metrics;
    private Evidence evidence;
    private List<Reviewer> reviewers;
    private List<Check> checks;
    private List<Comment> comments;
    private List<ChecklistItem> checklist;
    private List<Event> events;

    @Data
    @Builder
    public static class Policy {
        private String mode;
        private Integer requiredApprovals;
        private boolean executionEvidenceRequired;
        private Integer executionEvidenceMaxAgeHours;
        private Integer reviewSlaHours;
    }

    @Data
    @Builder
    public static class Metrics {
        private Integer checkPassed;
        private Integer checkTotal;
        private Integer blockerCount;
        private Integer openCommentCount;
        private Integer approvedCount;
    }

    @Data
    @Builder
    public static class Reviewer {
        private Long id;
        private String name;
        private String role;
        private String decision;
        private String summary;
        private LocalDateTime decisionAt;
    }

    @Data
    @Builder
    public static class Check {
        private Long id;
        private String ruleCode;
        private String result;
        private String severity;
        private String effectiveSeverity;
        private String message;
        private List<Map<String, Object>> anchors;
        private Map<String, Object> evidence;
        private LocalDateTime checkedAt;
    }

    @Data
    @Builder
    public static class Comment {
        private Long id;
        private Long threadId;
        private Long parentId;
        private String nodeType;
        private String stepId;
        private String fieldPath;
        private String severity;
        private String resolution;
        private String resolutionType;
        private String content;
        private Long authorId;
        private String authorName;
        private LocalDateTime createTime;
        private Long resolvedBy;
        private LocalDateTime resolvedAt;
        private String resolutionReason;
    }

    @Data
    @Builder
    public static class ChecklistItem {
        private String code;
        private String label;
        private boolean checked;
        private LocalDateTime checkedAt;
    }

    @Data
    @Builder
    public static class Evidence {
        private Long executionId;
        private String triggerType;
        private String environmentName;
        private String result;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private String reportUrl;
        private boolean exactVersion;
    }

    @Data
    @Builder
    public static class Event {
        private Long id;
        private String type;
        private Long actorId;
        private String actorName;
        private Map<String, Object> payload;
        private LocalDateTime createTime;
    }
}
