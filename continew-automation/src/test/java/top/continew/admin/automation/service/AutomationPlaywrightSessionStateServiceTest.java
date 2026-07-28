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

package top.continew.admin.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.continew.admin.automation.service.AutomationPlaywrightSessionStateService.SessionFiles;

class AutomationPlaywrightSessionStateServiceTest {

    @TempDir
    private Path temporaryDirectory;

    private AutomationPlaywrightSessionStateService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AutomationPlaywrightSessionStateService(new ObjectMapper());
        setField("configuredRoot", temporaryDirectory.toString());
        setField("retentionHours", 24L);
        service.initialize();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void shouldPromoteValidCandidateAndKeepBatchesAndEnvironmentsIsolated() throws IOException {
        SessionFiles first = service.prepare("BATCH_A", 47L, "JOB_1");
        SessionFiles otherEnvironment = service.prepare("BATCH_A", 48L, "JOB_2");
        SessionFiles otherBatch = service.prepare("BATCH_B", 47L, "JOB_3");
        Files.writeString(first.candidatePath(), """
            {
              "cookies":[{"name":"sid","value":"secret"}],
              "origins":[],
              "_sakura":{
                "version":2,
                "session_storage":{
                  "origin":"https://example.test",
                  "entries":[{"name":"tab-auth","value":"secret"}]
                }
              }
            }
            """);

        service.promote(first);

        assertThat(first.currentPath()).isRegularFile();
        assertThat(first.candidatePath()).doesNotExist();
        assertThat(otherEnvironment.currentPath()).doesNotExist();
        assertThat(otherBatch.currentPath()).doesNotExist();
        service.cleanupBatch("BATCH_A");
        assertThat(first.currentPath().getParent().getParent()).doesNotExist();
        assertThat(otherBatch.candidatePath().getParent()).exists();
    }

    @Test
    void shouldRejectBrokenCandidateAndRedactSensitiveArguments() throws IOException {
        SessionFiles files = service.prepare("BATCH_A", 47L, "JOB_1");
        Files.writeString(files.candidatePath(), "{\"cookies\":\"invalid\"}");

        assertThatThrownBy(() -> service.promote(files)).hasMessageContaining("cookies 必须是数组");
        Files.writeString(files.candidatePath(), """
            {
              "cookies":[],
              "origins":[],
              "_sakura":{"session_storage":{"origin":"https://example.test","entries":"invalid"}}
            }
            """);
        assertThatThrownBy(() -> service.promote(files)).hasMessageContaining("sessionStorage 结构无效");
        assertThat(service.redactCommand(List
            .of("node", "src/index.js", "--token", "token-value", "--storage-state=C:\\secret\\current.json", "--storage-state-out", "C:\\secret\\candidate.json", "--browser", "chromium")))
            .containsExactly("node", "src/index.js", "--token", "***", "--storage-state=***", "--storage-state-out", "***", "--browser", "chromium");
    }

    @Test
    void shouldDeleteExpiredBatchDirectory() throws Exception {
        SessionFiles files = service.prepare("BATCH_EXPIRED", 47L, "JOB_1");
        Path batchDirectory = files.currentPath().getParent().getParent();
        setField("retentionHours", 1L);
        Files.setLastModifiedTime(batchDirectory, FileTime.from(Instant.now().minusSeconds(7200)));

        service.cleanupExpired();

        assertThat(batchDirectory).doesNotExist();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AutomationPlaywrightSessionStateService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
