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
import java.util.List;

import lombok.Data;

/** 执行历史 page/cursor 严格联合响应。 */
public sealed interface AutomationUiExecutionPageResp extends Serializable permits AutomationUiExecutionPageResp.Offset,
    AutomationUiExecutionPageResp.Cursor {

    String getMode();

    List<AutomationUiExecutionSummaryResp> getList();

    Long getGlobalExecutionRevision();

    /** 浅分页响应，显式包含精确总数。 */
    @Data
    final class Offset implements AutomationUiExecutionPageResp {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String mode = "page";
        private List<AutomationUiExecutionSummaryResp> list;
        private Long total;
        private Integer page;
        private Integer size;
        private Long globalExecutionRevision;
    }

    /** 深分页响应，不提供高成本精确总数。 */
    @Data
    final class Cursor implements AutomationUiExecutionPageResp {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String mode = "cursor";
        private List<AutomationUiExecutionSummaryResp> list;
        private String nextCursor;
        private Boolean hasMore;
        private Long globalExecutionRevision;
    }
}
