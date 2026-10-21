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

package top.continew.admin.test.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.starter.core.autoconfigure.project.ProjectProperties;

class TestTimedTaskNotificationServiceImplTest {

    @Test
    void shouldRejectDeliveryWhenRunWasCancelled() {
        TestTimedTaskRunMapper runMapper = mock(TestTimedTaskRunMapper.class);
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setStatus("CANCELLED");
        run.setNotificationStatus("SENDING");
        when(runMapper.selectByIdForUpdate(1L)).thenReturn(run);

        TestTimedTaskNotificationDeliveryService service = new TestTimedTaskNotificationDeliveryService(runMapper);
        TestTimedTaskNotificationDeliveryService.DeliveryResult result = service
            .sendRecipient(1L, "user@example.com", "subject", "content");

        assertThat(result.delivered()).isFalse();
    }

    @Test
    void shouldRejectDeliveryWhenNotificationWasSuppressed() {
        TestTimedTaskRunMapper runMapper = mock(TestTimedTaskRunMapper.class);
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setStatus("FAILED");
        run.setNotificationStatus("SKIPPED");
        when(runMapper.selectByIdForUpdate(1L)).thenReturn(run);

        TestTimedTaskNotificationDeliveryService service = new TestTimedTaskNotificationDeliveryService(runMapper);
        TestTimedTaskNotificationDeliveryService.DeliveryResult result = service
            .sendRecipient(1L, "user@example.com", "subject", "content");

        assertThat(result.delivered()).isFalse();
    }

    @Test
    void shouldNotSendNotificationForCancelledRun() {
        TestTimedTaskRunMapper runMapper = mock(TestTimedTaskRunMapper.class);
        TestTimedTaskMapper taskMapper = mock(TestTimedTaskMapper.class);
        TestPlanMapper planMapper = mock(TestPlanMapper.class);
        TestReportMapper reportMapper = mock(TestReportMapper.class);
        ProjectEnvironmentConfigMapper environmentMapper = mock(ProjectEnvironmentConfigMapper.class);
        ProjectProperties projectProperties = mock(ProjectProperties.class);
        TestTimedTaskNotificationDeliveryService deliveryService = mock(TestTimedTaskNotificationDeliveryService.class);
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setStatus("CANCELLED");
        when(runMapper.selectById(1L)).thenReturn(run);

        TestTimedTaskNotificationServiceImpl service = new TestTimedTaskNotificationServiceImpl(runMapper, taskMapper, planMapper, reportMapper, environmentMapper, projectProperties, deliveryService);
        service.send(1L);

        verify(runMapper).selectById(1L);
        verifyNoInteractions(taskMapper, planMapper, reportMapper, environmentMapper, projectProperties, deliveryService);
    }

    @Test
    void shouldNotSendNotificationWhenCancellationAlreadySuppressedIt() {
        TestTimedTaskRunMapper runMapper = mock(TestTimedTaskRunMapper.class);
        TestTimedTaskMapper taskMapper = mock(TestTimedTaskMapper.class);
        TestPlanMapper planMapper = mock(TestPlanMapper.class);
        TestReportMapper reportMapper = mock(TestReportMapper.class);
        ProjectEnvironmentConfigMapper environmentMapper = mock(ProjectEnvironmentConfigMapper.class);
        ProjectProperties projectProperties = mock(ProjectProperties.class);
        TestTimedTaskNotificationDeliveryService deliveryService = mock(TestTimedTaskNotificationDeliveryService.class);
        TestTimedTaskRunDO run = new TestTimedTaskRunDO();
        run.setStatus("FAILED");
        run.setNotificationStatus("SKIPPED");
        when(runMapper.selectById(1L)).thenReturn(run);

        TestTimedTaskNotificationServiceImpl service = new TestTimedTaskNotificationServiceImpl(runMapper, taskMapper, planMapper, reportMapper, environmentMapper, projectProperties, deliveryService);
        service.send(1L);

        verify(runMapper).selectById(1L);
        verifyNoInteractions(taskMapper, planMapper, reportMapper, environmentMapper, projectProperties, deliveryService);
    }
}
