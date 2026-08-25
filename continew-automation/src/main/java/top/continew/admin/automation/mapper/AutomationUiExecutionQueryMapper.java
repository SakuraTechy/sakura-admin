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

package top.continew.admin.automation.mapper;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.continew.admin.automation.model.query.AutomationUiExecutionAccessRow;
import top.continew.admin.automation.model.query.AutomationUiExecutionCaseHistoryQuery;
import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.resp.AutomationUiExecutionArtifactResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionCaseHistoryResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionStepResp;
import top.continew.admin.automation.model.resp.AutomationUiExecutionSummaryResp;

/** UI 自动化执行事实专用只读 Mapper。 */
@Mapper
public interface AutomationUiExecutionQueryMapper {

    AutomationUiExecutionAccessRow selectSceneAccess(@Param("sceneDbId") Long sceneDbId,
                                                     @Param("userId") Long userId,
                                                     @Param("admin") boolean admin);

    AutomationUiExecutionSummaryResp selectScopedLatest(@Param("sceneDbId") Long sceneDbId,
                                                        @Param("scope") AutomationUiExecutionScopeReq scope,
                                                        @Param("userId") Long userId,
                                                        @Param("admin") boolean admin);

    List<AutomationUiExecutionSummaryResp> selectScopedLatestBatch(@Param("sceneDbIds") Collection<Long> sceneDbIds,
                                                                   @Param("scope") AutomationUiExecutionScopeReq scope,
                                                                   @Param("userId") Long userId,
                                                                   @Param("admin") boolean admin);

    AutomationUiExecutionAccessRow selectExecutionPageCount(@Param("query") AutomationUiExecutionQuery query,
                                                            @Param("scope") AutomationUiExecutionScopeReq scope,
                                                            @Param("userId") Long userId,
                                                            @Param("admin") boolean admin);

    List<AutomationUiExecutionSummaryResp> selectExecutionPage(@Param("query") AutomationUiExecutionQuery query,
                                                               @Param("scope") AutomationUiExecutionScopeReq scope,
                                                               @Param("userId") Long userId,
                                                               @Param("admin") boolean admin,
                                                               @Param("offset") long offset,
                                                               @Param("limit") int limit,
                                                               @Param("ascending") boolean ascending);

    List<AutomationUiExecutionSummaryResp> selectExecutionCursor(@Param("query") AutomationUiExecutionQuery query,
                                                                 @Param("scope") AutomationUiExecutionScopeReq scope,
                                                                 @Param("userId") Long userId,
                                                                 @Param("admin") boolean admin,
                                                                 @Param("cursorTime") LocalDateTime cursorTime,
                                                                 @Param("cursorId") Long cursorId,
                                                                 @Param("limit") int limit,
                                                                 @Param("ascending") boolean ascending);

    AutomationUiExecutionDetailResp selectExecutionDetail(@Param("executionDbId") Long executionDbId,
                                                          @Param("userId") Long userId,
                                                          @Param("admin") boolean admin);

    AutomationUiExecutionAccessRow selectCasePageCount(@Param("executionDbId") Long executionDbId,
                                                       @Param("userId") Long userId,
                                                       @Param("admin") boolean admin);

    List<AutomationUiExecutionCaseResp> selectCasePage(@Param("executionDbId") Long executionDbId,
                                                       @Param("userId") Long userId,
                                                       @Param("admin") boolean admin,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);

    AutomationUiExecutionAccessRow selectCaseHistoryPageCount(@Param("query") AutomationUiExecutionCaseHistoryQuery query,
                                                              @Param("scope") top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq scope,
                                                              @Param("userId") Long userId,
                                                              @Param("admin") boolean admin);

    List<AutomationUiExecutionCaseHistoryResp> selectCaseHistoryPage(@Param("query") AutomationUiExecutionCaseHistoryQuery query,
                                                                     @Param("scope") top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq scope,
                                                                     @Param("userId") Long userId,
                                                                     @Param("admin") boolean admin,
                                                                     @Param("offset") long offset,
                                                                     @Param("limit") int limit);

    AutomationUiExecutionAccessRow selectStepPageCount(@Param("caseExecutionDbId") Long caseExecutionDbId,
                                                       @Param("userId") Long userId,
                                                       @Param("admin") boolean admin);

    List<AutomationUiExecutionStepResp> selectStepPage(@Param("caseExecutionDbId") Long caseExecutionDbId,
                                                       @Param("userId") Long userId,
                                                       @Param("admin") boolean admin,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);

    AutomationUiExecutionStepDetailResp selectStepDetail(@Param("stepExecutionDbId") Long stepExecutionDbId,
                                                         @Param("userId") Long userId,
                                                         @Param("admin") boolean admin);

    AutomationUiExecutionAccessRow selectArtifactPageCount(@Param("executionDbId") Long executionDbId,
                                                           @Param("userId") Long userId,
                                                           @Param("admin") boolean admin);

    List<AutomationUiExecutionArtifactResp> selectArtifactPage(@Param("executionDbId") Long executionDbId,
                                                               @Param("userId") Long userId,
                                                               @Param("admin") boolean admin,
                                                               @Param("offset") long offset,
                                                               @Param("limit") int limit);

    Long selectArtifactFileId(@Param("executionDbId") Long executionDbId,
                              @Param("artifactDbId") Long artifactDbId,
                              @Param("userId") Long userId,
                              @Param("admin") boolean admin);
}
