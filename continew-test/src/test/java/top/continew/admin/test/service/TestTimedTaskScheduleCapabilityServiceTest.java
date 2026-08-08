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

package top.continew.admin.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.model.resp.TestTimedTaskCapabilityResp;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class TestTimedTaskScheduleCapabilityServiceTest {

    @Mock
    private JobService jobService;

    @Test
    void shouldReportDisabledWithoutCallingScheduleApi() {
        TestTimedTaskScheduleCapabilityService service = new TestTimedTaskScheduleCapabilityService(jobService, false, "continew-admin");

        TestTimedTaskCapabilityResp result = service.probe();

        assertThat(result.isReady()).isFalse();
        assertThat(result.isClientEnabled()).isFalse();
        assertThat(result.getMessage()).isEqualTo("调度客户端未启用");
        verify(jobService, never()).listGroup();
    }

    @Test
    void shouldReportReadyWhenConfiguredGroupExists() {
        when(jobService.listGroup()).thenReturn(List.of("continew-admin"));
        TestTimedTaskScheduleCapabilityService service = new TestTimedTaskScheduleCapabilityService(jobService, true, "continew-admin");

        TestTimedTaskCapabilityResp result = service.probe();

        assertThat(result.isReady()).isTrue();
        assertThat(result.isApiReachable()).isTrue();
        assertThat(result.isGroupAvailable()).isTrue();
    }

    @Test
    void shouldHideConnectionFailureDetails() {
        when(jobService.listGroup()).thenThrow(new IllegalStateException("credential leaked"));
        TestTimedTaskScheduleCapabilityService service = new TestTimedTaskScheduleCapabilityService(jobService, true, "continew-admin");

        TestTimedTaskCapabilityResp result = service.probe();

        assertThat(result.isReady()).isFalse();
        assertThat(result.isApiReachable()).isFalse();
        assertThat(result.getMessage()).isEqualTo("调度中心连接失败");
    }
}
