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

package top.continew.admin.automation.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

class AutomationUiSceneXmlUtilsContractTest {

    @Test
    void shouldKeepLegacyJenkinsXmlContractAndExcludePlaywrightRawFields(@TempDir Path workspace) throws Exception {
        AutomationUiSceneDO scene = sceneFixture();

        AutomationUiSceneXmlUtils.BundleContext bundle = AutomationUiSceneXmlUtils.createBundle(List
            .of(scene), "Sakura UI", "SAKURA_UI", "V1.0", "firefox", "agent-01", "https://target.example/login", "eth9", workspace, Map
                .of(7L, Map.of("CASE_FIRST", "https://frozen.example/login")));

        String sceneXml = Files.readString(bundle.testCaseDir().resolve("SCENE_LOGIN.xml"), StandardCharsets.UTF_8);
        assertThat(sceneXml).contains("<unit id=\"SCENE_LOGIN\" name=\"登录场景\" version=\"V1.0\">")
            .contains("operationValue=\"web-geturl\"")
            .contains("methodCode=\"browser.navigate.default\"")
            .contains("actionType=\"navigate\"")
            .contains("diagnosticProfile=\"navigation\"")
            .contains("action=\"web-geturl\"")
            .contains("continueOnFailure=\"true\"")
            .contains("url=\"https://frozen.example/login\"")
            .contains("action=\"web-assert-element-match\"")
            .contains("locator=\"cssSelector=#title\"")
            .contains("read_mode=\"value\"")
            .contains("match_mode=\"equals\"")
            .contains("expect=\"系统管理平台\"")
            .doesNotContain("url=\"https://target.example/login\"")
            .doesNotContain("playwright_step", "locator_meta", "raw-secret");
        assertThat(sceneXml.indexOf("CASE_FIRST")).isLessThan(sceneXml.indexOf("CASE_SECOND"));

        String testngXml = Files.readString(bundle.testngXmlPath(), StandardCharsets.UTF_8);
        assertThat(testngXml).contains("<suite name=\"Sakura UI\"")
            .contains("<parameter name=\"browser\" value=\"firefox\"/>")
            .contains("<class name=\"SAKURA_UI.V1_0.TestCases.SCENE_LOGIN\"/>")
            .contains("<group depends-on=\"CASE_FIRST\" name=\"CASE_SECOND\"/>")
            .contains("org.uncommons.reportng.HTMLReporter", "org.uncommons.reportng.JUnitXMLReporter");

        String extentXml = Files.readString(bundle.extentXmlPath(), StandardCharsets.UTF_8);
        assertThat(extentXml).contains("path=\"src/test/java/SAKURA_UI/V1_0/TestReportXml/agent-01.xml\"")
            .contains("class-name=\"com.sakura.service.ExtentReportGenerateService\"");

        Path javaSource = workspace.resolve("SAKURA_UI/V1_0/TestCases/SCENE_LOGIN.java");
        assertThat(javaSource).exists();
        assertThat(Files.readString(javaSource, StandardCharsets.UTF_8)).contains("package SAKURA_UI.V1_0.TestCases;")
            .contains("@Parameters({\"browser\", \"profile\"})")
            .contains("@Test(groups = {\"CASE_FIRST\"})")
            .contains("@Test(groups = {\"CASE_SECOND\"})")
            .contains("runService.setUnit(true);");
    }

    private AutomationUiSceneDO sceneFixture() {
        AutomationUiSceneDO scene = new AutomationUiSceneDO();
        scene.setId(7L);
        scene.setSceneId("SCENE_LOGIN");
        scene.setName("登录场景");
        scene.setVersionName("V1.0");
        scene.setCaseList(List.of(caseFixture("CASE_SECOND", "提交登录", 2, List
            .of(unifiedAssertionStep())), caseFixture("CASE_FIRST", "打开登录页", 1, List.of(openUrlStep()))));
        return scene;
    }

    private CaseDO caseFixture(String id, String name, int order, List<StepDO> steps) {
        CaseDO caseDO = new CaseDO();
        caseDO.setId(id);
        caseDO.setName(name);
        caseDO.setOrder(order);
        caseDO.setStepList(steps);
        return caseDO;
    }

    private StepDO openUrlStep() {
        StepDO step = new StepDO();
        step.setId("STEP_OPEN");
        step.setOrder(1);
        step.setType("web");
        step.setName("打开地址");
        step.setOperationType("浏览器");
        step.setOperationName("访问地址");
        step.setOperationValue("web-geturl");
        step.setContinueOnFailure(true);
        step.setConfigList(List
            .of(config("url", "https://old.example/login"), config("method_code", "browser.navigate.default"), config("method_version", "1"), config("type_code", "browser"), config("type_label", "浏览器操作"), config("method_label", "打开默认网页"), config("diagnostic_profile", "navigation"), config("action_type", "navigate"), config("playwright_step", "raw-secret"), config("locator_meta", "raw-secret")));
        return step;
    }

    private StepDO unifiedAssertionStep() {
        StepDO step = new StepDO();
        step.setId("STEP_ASSERT");
        step.setOrder(1);
        step.setType("web");
        step.setName("检查页面元素");
        step.setOperationType("检查操作");
        step.setOperationName("检查页面元素");
        step.setOperationValue("web-assert-element-match");
        step.setConfigList(List
            .of(config("locator", "cssSelector=#title"), config("read_mode", "value"), config("match_mode", "equals"), config("expect", "系统管理平台"), config("method_code", "assertion.element.match"), config("method_version", "1"), config("diagnostic_profile", "assertion"), config("action_type", "assert_element_match"), config("playwright_step", "raw-secret"), config("locator_meta", "raw-secret")));
        return step;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }
}
