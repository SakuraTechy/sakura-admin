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

package top.continew.admin.automation.model.resp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class AutomationUiExecutionReadContractTest {

    @Test
    void executionSummaryShouldExcludeLargeAndNestedBodies() {
        Set<String> fields = fields(AutomationUiExecutionSummaryResp.class);

        assertThat(fields)
            .doesNotContain("summaryJson", "executionConfig", "caseList", "cases", "caseResults", "steps", "diagnostics", "raw", "playwrightResult");
        assertThat(fields).contains("executionDbId", "executionKey", "sceneDbId", "recordSource", "status", "result");
    }

    @Test
    void childPageDtosShouldNotEmbedDescendantsOrDiagnostics() {
        assertThat(fields(AutomationUiExecutionCaseResp.class))
            .doesNotContain("summaryJson", "steps", "diagnostics", "raw");
        assertThat(fields(AutomationUiExecutionStepResp.class))
            .doesNotContain("diagnostics", "locatorValue", "artifacts", "raw");
    }

    @Test
    void artifactMetadataShouldNotExposeStorageLocator() {
        Set<String> fields = fields(AutomationUiExecutionArtifactResp.class);

        assertThat(fields).contains("artifactDbId", "artifactType", "sizeBytes", "storageStatus")
            .doesNotContain("fileId", "sha256", "relativePath", "url", "physicalPath");
    }

    @Test
    void pageAndCursorResponsesShouldHaveMutuallyExclusivePaginationFields() {
        assertThat(fields(AutomationUiExecutionPageResp.Offset.class)).contains("total", "page", "size")
            .doesNotContain("nextCursor", "hasMore");
        assertThat(fields(AutomationUiExecutionPageResp.Cursor.class)).contains("nextCursor", "hasMore")
            .doesNotContain("total", "page", "size");
    }

    private Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
    }
}
