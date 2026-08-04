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

package top.continew.admin.automation.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.catalog.AutomationOperationCatalog;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.service.impl.AutomationOperationCatalogServiceImpl;

class AutomationOperationStepAssemblerTest {

    private AutomationOperationStepAssembler assembler;

    @BeforeEach
    void setUp() {
        AutomationOperationCatalogServiceImpl catalogService = new AutomationOperationCatalogServiceImpl(new ObjectMapper());
        catalogService.initialize();
        assembler = new AutomationOperationStepAssembler(new ObjectMapper(), catalogService, new AutomationOperationConfigValidator());
    }

    @Test
    void shouldGenerateManualClickWithoutActiveCapabilitySnapshots() {
        StepDO step = step("点击登录", List
            .of(config("method_code", "click.element"), config("method_version", "1"), config("method_config", "{\"target_ref\":{\"xpath\":\"//button[@id='login']\"}}")));

        StepDO assembled = assembler.assembleManualStep(step);
        Map<String, String> configs = assembled.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(assembled.getOperationValue()).isEqualTo("web-click");
        assertThat(assembled.getOperationName()).isEqualTo("元素点击");
        assertThat(configs).containsEntry("action_type", "click")
            .containsEntry("source", "admin-manual")
            .containsEntry("locator", "xpath=//button[@id='login']")
            .containsEntry("catalog_version", "2026-07-30.1");
        assertThat(configs.get("playwright_step")).contains("\"action_type\":\"click\"")
            .contains("\"target_xpath\":\"//button[@id='login']\"");
        assertThat(configs.get("canonical_digest")).hasSize(64);
    }

    @Test
    void shouldLeaveRecordedStepWithoutMethodCodeUntouched() {
        StepDO step = step("录制点击", new ArrayList<>(List
            .of(config("source", "sakura-playwright"), config("action_type", "click"), config("playwright_step", "{\"action_type\":\"click\",\"target_selector\":\"#ok\"}"))));

        StepDO result = assembler.assembleManualStep(step);

        assertThat(result).isSameAs(step);
        assertThat(result.getOperationValue()).isNull();
        assertThat(result.getConfigList()).extracting(StepDO.Config::getParamsValue)
            .contains("{\"action_type\":\"click\",\"target_selector\":\"#ok\"}");
    }

    @Test
    void shouldRejectMissingRequiredManualField() {
        StepDO step = step("输入", List
            .of(config("method_code", "input.text"), config("method_version", "1"), config("method_config", "{\"value\":\"demo\"}")));

        assertThatThrownBy(() -> assembler.assembleManualStep(step)).hasMessageContaining("缺少必填参数 target_ref");
    }

    @Test
    void shouldRejectMalformedInfrastructureTargetRefBeforeSaving() {
        StepDO serverStep = step("服务器命令", List
            .of(config("method_code", "server.shell"), config("method_version", "1"), config("method_config", "{\"target_ref\":{\"scope\":\"project_config\",\"kind\":\"server\",\"config_id\":\"\"},\"command\":\"hostname\"}")));
        StepDO databaseStep = step("数据库查询", List
            .of(config("method_code", "database.query"), config("method_version", "1"), config("method_config", "{\"target_ref\":{\"scope\":\"project_config\",\"kind\":\"server\",\"config_id\":12},\"sql\":\"select 1\"}")));

        assertThatThrownBy(() -> assembler.assembleManualStep(serverStep))
            .hasMessageContaining("INFRA_TARGET_REF_INVALID");
        assertThatThrownBy(() -> assembler.assembleManualStep(databaseStep))
            .hasMessageContaining("INFRA_TARGET_KIND_MISMATCH");
    }

    @Test
    void shouldAcceptValidInfrastructureTargetRef() {
        StepDO step = step("服务器命令", List
            .of(config("method_code", "server.shell"), config("method_version", "1"), config("method_config", "{\"target_ref\":{\"scope\":\"project_config\",\"kind\":\"server\",\"config_id\":12},\"command\":\"hostname\"}")));

        StepDO assembled = assembler.assembleManualStep(step);

        assertThat(assembled.getConfigList()).extracting(StepDO.Config::getParamsName)
            .contains("method_config", "playwright_step", "canonical_digest");
    }

    @Test
    void shouldProjectDatabaseResultVariableToLegacySubject() {
        StepDO step = step("检查查询结果", List
            .of(config("method_code", "assertion.database-value"), config("method_version", "1"), config("method_config", "{\"variable_name\":\"rows[0].status\",\"expect\":\"READY\"}")));

        StepDO assembled = assembler.assembleManualStep(step);
        Map<String, String> configs = assembled.getConfigList()
            .stream()
            .collect(Collectors.toMap(StepDO.Config::getParamsName, StepDO.Config::getParamsValue));

        assertThat(configs).containsEntry("details", "condition:field;subject:${rows[0].status}")
            .containsEntry("expect", "READY");
        assertThat(configs.get("playwright_step")).contains("\"action_type\":\"assert_database_value\"");
    }

    @Test
    void shouldRejectPlaintextSecretAndUnsafeFormula() {
        StepDO secretStep = step("敏感参数", List
            .of(config("method_code", "click.element"), config("method_version", "1"), config("method_config", "{\"target_ref\":\"#login\",\"password\":\"plain\"}")));
        AutomationOperationCatalog.OperationMethod formulaMethod = new AutomationOperationCatalog.OperationMethod();
        formulaMethod.setActionType("global_variable_formula");
        formulaMethod.setLabel("计算公式");
        formulaMethod.setFormSchema(List.of());

        assertThatThrownBy(() -> assembler.assembleManualStep(secretStep)).hasMessageContaining("敏感值必须保存引用");
        assertThatThrownBy(() -> new AutomationOperationConfigValidator().validate(formulaMethod, Map
            .of("variable_name", "result", "expression", "process.exit(1)")))
            .hasMessageContaining("VARIABLE_EXPRESSION_INVALID");
    }

    private StepDO step(String name, List<StepDO.Config> configs) {
        return step(name, new ArrayList<>(configs));
    }

    private StepDO step(String name, ArrayList<StepDO.Config> configs) {
        StepDO step = new StepDO();
        step.setId("CASE_STEP_001");
        step.setName(name);
        step.setConfigList(configs);
        return step;
    }

    private StepDO.Config config(String name, String value) {
        StepDO.Config config = new StepDO.Config();
        config.setParamsName(name);
        config.setParamsValue(value);
        return config;
    }

}
