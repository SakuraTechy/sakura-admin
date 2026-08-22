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

package top.continew.admin.automation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.query.AutomationUiExecutionQuery;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiExecutionScopeReq;
import top.continew.admin.automation.model.req.AutomationUiPageReq;
import top.continew.admin.automation.model.req.AutomationUiSceneSummariesReq;
import top.continew.starter.extension.crud.model.query.PageQuery;

class AutomationUiLayeredReadControllerContractTest {

    @Test
    void everyLayeredExecutionReadShouldRequireSceneGetPermission() throws NoSuchMethodException {
        assertPermission(AutomationUiExecutionQueryController.class
            .getMethod("page", AutomationUiExecutionQuery.class, AutomationUiPageReq.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiExecutionQueryController.class
            .getMethod("detail", Long.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiExecutionQueryController.class
            .getMethod("cases", Long.class, AutomationUiPageReq.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiExecutionQueryController.class
            .getMethod("artifacts", Long.class, AutomationUiPageReq.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiExecutionCaseQueryController.class
            .getMethod("steps", Long.class, AutomationUiPageReq.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiExecutionStepQueryController.class
            .getMethod("detail", Long.class), "automation:automationUiScene:get");
    }

    @Test
    void sceneSummaryDefinitionAndRevisionRoutesShouldUseDeclaredPermissions() throws NoSuchMethodException {
        assertPermission(AutomationUiSceneController.class
            .getMethod("summaryPage", AutomationUiSceneQuery.class, String.class, Long.class, Long.class, Integer.class, PageQuery.class), "automation:automationUiScene:list");
        assertPermission(AutomationUiSceneController.class
            .getMethod("summaryPagePost", AutomationUiSceneQuery.class, String.class, Long.class, Long.class, Integer.class, PageQuery.class), "automation:automationUiScene:list");
        assertPermission(AutomationUiSceneController.class
            .getMethod("summaries", AutomationUiSceneSummariesReq.class), "automation:automationUiScene:list");
        assertPermission(AutomationUiSceneController.class
            .getMethod("executionSummary", Long.class, AutomationUiExecutionScopeReq.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiSceneController.class
            .getMethod("definition", Long.class, jakarta.servlet.http.HttpServletRequest.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiSceneController.class
            .getMethod("definitionCases", Long.class, int.class, int.class, String.class, jakarta.servlet.http.HttpServletRequest.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiSceneController.class
            .getMethod("definitionCase", Long.class, String.class, jakarta.servlet.http.HttpServletRequest.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiSceneController.class
            .getMethod("definitionSteps", Long.class, String.class, int.class, int.class, jakarta.servlet.http.HttpServletRequest.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiSceneController.class
            .getMethod("definitionStep", Long.class, String.class, String.class, jakarta.servlet.http.HttpServletRequest.class), "automation:automationUiScene:get");
        assertPermission(AutomationUiExecutionQueryController.class
            .getMethod("revisions", java.util.List.class), "automation:automationUiScene:list");
    }

    private void assertPermission(Method method, String permission) {
        assertThat(method.getAnnotation(SaIgnore.class)).as(method.toGenericString()).isNull();
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertThat(annotation).as(method.toGenericString()).isNotNull();
        assertThat(Arrays.asList(annotation.value())).contains(permission);
    }
}
