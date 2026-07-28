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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightRunnerJobResp;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService.LiveFrame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutomationPlaywrightRunnerJobControllerTest {

    private final AutomationPlaywrightRunnerJobService service = mock(AutomationPlaywrightRunnerJobService.class);
    private final AutomationPlaywrightRunnerJobController controller = new AutomationPlaywrightRunnerJobController(service);

    @Test
    void shouldReturnLatestJpegFrame() {
        byte[] jpeg = {(byte)0xFF, (byte)0xD8, 0x01, (byte)0xFF, (byte)0xD9};
        AutomationPlaywrightRunnerJobResp job = job("running", true);
        when(service.get("JOB_001")).thenReturn(job);
        when(service.getLiveFrame("JOB_001")).thenReturn(new LiveFrame(3, jpeg));

        ResponseEntity<byte[]> response = controller.liveFrame("JOB_001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).hasToString("image/jpeg");
        assertThat(response.getHeaders().getFirst("Access-Control-Expose-Headers"))
            .isEqualTo("X-Sakura-Frame-Sequence, X-Sakura-Job-Status");
        assertThat(response.getHeaders().getFirst("X-Sakura-Frame-Sequence")).isEqualTo("3");
        assertThat(response.getHeaders().getFirst("X-Sakura-Job-Status")).isEqualTo("running");
        assertThat(response.getBody()).isEqualTo(jpeg);
    }

    @Test
    void shouldDistinguishWaitingAndFinishedJobsWithoutFrame() {
        when(service.get("JOB_RUNNING")).thenReturn(job("running", true));
        when(service.get("JOB_FINISHED")).thenReturn(job("passed", false));

        assertThat(controller.liveFrame("JOB_RUNNING").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.liveFrame("JOB_FINISHED").getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    private AutomationPlaywrightRunnerJobResp job(String status, boolean liveAvailable) {
        AutomationPlaywrightRunnerJobResp job = new AutomationPlaywrightRunnerJobResp();
        job.setStatus(status);
        job.setLiveAvailable(liveAvailable);
        return job;
    }
}
