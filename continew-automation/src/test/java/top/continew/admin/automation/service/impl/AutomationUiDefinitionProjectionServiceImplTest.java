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

package top.continew.admin.automation.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;

@ExtendWith(MockitoExtension.class)
class AutomationUiDefinitionProjectionServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private IdentifierGenerator identifierGenerator;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AutomationUiDefinitionProjectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomationUiDefinitionProjectionServiceImpl(jdbcTemplate, identifierGenerator, new ObjectMapper(), transactionManager);
        ReflectionTestUtils.setField(service, "inlineMaxBytes", 1_048_576L);
        ReflectionTestUtils.setField(service, "inlineMaxSteps", 1_000L);
    }

    @Test
    void smallDefinitionShouldOnlyPersistBoundedMetrics() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.recordDefinitionWrite(8L, 4L, List.of(validCase()));

        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void largeDefinitionShouldQueueUsingDatabaseJsonHash() {
        ReflectionTestUtils.setField(service, "inlineMaxBytes", 1L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(identifierGenerator.nextId(any())).thenReturn(99L);

        service.recordDefinitionWrite(8L, 4L, List.of(validCase()));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues().get(0))
            .contains("definition_size_bytes", "definition_step_count", "definition_version = ?");
        assertThat(sql.getAllValues().get(1))
            .contains("SHA2(CAST(s.case_list AS CHAR), 256)", "'queued'", "ON DUPLICATE KEY UPDATE");
        verify(identifierGenerator).nextId(any());
    }

    @Test
    void validationShouldRejectDuplicateIdsAndWrongParentWithoutLeakingNodeValue() {
        CaseDO caseDO = validCase();
        StepDO duplicate = new StepDO();
        duplicate.setId("step-1");
        duplicate.setPid("wrong-parent");
        caseDO.setStepList(List.of(caseDO.getStepList().get(0), duplicate));

        assertThatThrownBy(() -> service.validateDefinition(List.of(caseDO))).isInstanceOf(RuntimeException.class)
            .hasMessageContaining("definition-projection-")
            .hasMessageNotContaining("wrong-parent");
    }

    @Test
    void validationShouldRejectControlCharactersAndOverlongIds() {
        CaseDO caseDO = validCase();
        caseDO.setId("case\nsecret");

        assertThatThrownBy(() -> service.validateDefinition(List.of(caseDO))).isInstanceOf(RuntimeException.class)
            .hasMessageNotContaining("secret");
    }

    private CaseDO validCase() {
        CaseDO caseDO = new CaseDO();
        caseDO.setId("case-1");
        StepDO step = new StepDO();
        step.setId("step-1");
        step.setPid("case-1");
        StepDO.Config playwrightStep = new StepDO.Config();
        playwrightStep.setParamsName("playwright_step");
        playwrightStep.setParamsValue("{\"action_type\":\"click\"}");
        StepDO.Config locatorMeta = new StepDO.Config();
        locatorMeta.setParamsName("locator_meta");
        locatorMeta.setParamsValue("{\"role\":\"button\"}");
        step.setConfigList(List.of(playwrightStep, locatorMeta));
        caseDO.setStepList(List.of(step));
        return caseDO;
    }
}
