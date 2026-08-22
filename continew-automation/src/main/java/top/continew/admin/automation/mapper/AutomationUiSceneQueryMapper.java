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

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.continew.admin.automation.model.query.AutomationUiSceneDefinitionRow;
import top.continew.admin.automation.model.query.AutomationUiSceneInlineDefinitionRow;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.query.AutomationUiDefinitionProjectionStateRow;
import top.continew.admin.automation.model.query.AutomationUiDefinitionCaseReadRow;
import top.continew.admin.automation.model.query.AutomationUiDefinitionStepReadRow;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneGlobalRevisionResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneSummaryResp;

/** UI 自动化场景轻量读模型 Mapper。 */
@Mapper
public interface AutomationUiSceneQueryMapper {

    Long selectAuthorizedProjectId(@Param("sceneDbId") Long sceneDbId,
                                   @Param("userId") Long userId,
                                   @Param("admin") boolean admin);

    Long selectAuthorizedSceneDbIdByKey(@Param("sceneKey") String sceneKey,
                                        @Param("userId") Long userId,
                                        @Param("admin") boolean admin);

    long countSummaries(@Param("query") AutomationUiSceneQuery query,
                        @Param("userId") Long userId,
                        @Param("admin") boolean admin);

    List<AutomationUiSceneSummaryResp> selectSummaryPage(@Param("query") AutomationUiSceneQuery query,
                                                         @Param("userId") Long userId,
                                                         @Param("admin") boolean admin,
                                                         @Param("offset") long offset,
                                                         @Param("limit") int limit,
                                                         @Param("sortField") String sortField,
                                                         @Param("ascending") boolean ascending);

    long countScopedSummaries(@Param("query") AutomationUiSceneQuery query,
                              @Param("executionScope") AutomationUiExecutionScopeReq executionScope,
                              @Param("userId") Long userId,
                              @Param("admin") boolean admin);

    List<AutomationUiSceneSummaryResp> selectScopedSummaryPage(@Param("query") AutomationUiSceneQuery query,
                                                               @Param("executionScope") AutomationUiExecutionScopeReq executionScope,
                                                               @Param("userId") Long userId,
                                                               @Param("admin") boolean admin,
                                                               @Param("offset") long offset,
                                                               @Param("limit") int limit,
                                                               @Param("sortField") String sortField,
                                                               @Param("ascending") boolean ascending);

    List<AutomationUiSceneSummaryResp> selectSummaries(@Param("sceneDbIds") Collection<Long> sceneDbIds,
                                                       @Param("userId") Long userId,
                                                       @Param("admin") boolean admin);

    List<AutomationUiSceneGlobalRevisionResp> selectRevisions(@Param("sceneDbIds") Collection<Long> sceneDbIds,
                                                              @Param("userId") Long userId,
                                                              @Param("admin") boolean admin);

    AutomationUiSceneDefinitionRow selectDefinitionMetadata(@Param("sceneDbId") Long sceneDbId,
                                                            @Param("userId") Long userId,
                                                            @Param("admin") boolean admin);

    AutomationUiSceneInlineDefinitionRow selectInlineDefinition(@Param("sceneDbId") Long sceneDbId,
                                                                @Param("definitionVersion") Long definitionVersion,
                                                                @Param("userId") Long userId,
                                                                @Param("admin") boolean admin);

    AutomationUiDefinitionProjectionStateRow selectProjectionState(@Param("sceneDbId") Long sceneDbId,
                                                                   @Param("userId") Long userId,
                                                                   @Param("admin") boolean admin);

    long countProjectedCases(@Param("sceneDbId") Long sceneDbId,
                             @Param("projectionId") Long projectionId,
                             @Param("definitionVersion") Long definitionVersion,
                             @Param("keyword") String keyword,
                             @Param("userId") Long userId,
                             @Param("admin") boolean admin);

    List<AutomationUiDefinitionCaseReadRow> selectProjectedCases(@Param("sceneDbId") Long sceneDbId,
                                                                 @Param("projectionId") Long projectionId,
                                                                 @Param("definitionVersion") Long definitionVersion,
                                                                 @Param("keyword") String keyword,
                                                                 @Param("userId") Long userId,
                                                                 @Param("admin") boolean admin,
                                                                 @Param("offset") long offset,
                                                                 @Param("limit") int limit);

    AutomationUiDefinitionCaseReadRow selectProjectedCase(@Param("sceneDbId") Long sceneDbId,
                                                          @Param("projectionId") Long projectionId,
                                                          @Param("definitionVersion") Long definitionVersion,
                                                          @Param("caseId") String caseId,
                                                          @Param("userId") Long userId,
                                                          @Param("admin") boolean admin);

    long countProjectedSteps(@Param("sceneDbId") Long sceneDbId,
                             @Param("projectionId") Long projectionId,
                             @Param("definitionVersion") Long definitionVersion,
                             @Param("caseId") String caseId,
                             @Param("userId") Long userId,
                             @Param("admin") boolean admin);

    List<AutomationUiDefinitionStepReadRow> selectProjectedSteps(@Param("sceneDbId") Long sceneDbId,
                                                                 @Param("projectionId") Long projectionId,
                                                                 @Param("definitionVersion") Long definitionVersion,
                                                                 @Param("caseId") String caseId,
                                                                 @Param("userId") Long userId,
                                                                 @Param("admin") boolean admin,
                                                                 @Param("offset") long offset,
                                                                 @Param("limit") int limit);

    AutomationUiDefinitionStepReadRow selectProjectedStep(@Param("sceneDbId") Long sceneDbId,
                                                          @Param("projectionId") Long projectionId,
                                                          @Param("definitionVersion") Long definitionVersion,
                                                          @Param("caseId") String caseId,
                                                          @Param("stepId") String stepId,
                                                          @Param("userId") Long userId,
                                                          @Param("admin") boolean admin);
}
