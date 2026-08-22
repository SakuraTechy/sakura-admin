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

package top.continew.admin.automation.support;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationUiQueryBaselineFilterTest {

    private final Logger logger = (Logger)LoggerFactory.getLogger(AutomationUiQueryBaselineFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        AutomationUiQueryBaselineRecorder.clear();
    }

    @Test
    void shouldCountOutputStreamBytesWithoutLoggingBody() throws Exception {
        byte[] body = "stream-body".getBytes(StandardCharsets.UTF_8);

        runFilter((request, response) -> response.getOutputStream().write(body));

        assertBaselineLog("scene-detail", body.length);
    }

    @Test
    void shouldCountWriterBytesUsingResponseEncoding() throws Exception {
        String body = "中文正文";

        runFilter((request, response) -> {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(body);
            response.getWriter().flush();
        });

        assertBaselineLog("scene-detail", body.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void shouldIgnoreOperationCatalogSubpaths() throws Exception {
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/automation/operation-catalog/executors/playwright/runner-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AutomationUiQueryBaselineFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(appender.list).isEmpty();
        assertThat(AutomationUiQueryBaselineRecorder.isActive()).isFalse();
    }

    private void runFilter(jakarta.servlet.FilterChain filterChain) throws Exception {
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/automation/automationUiScene/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AutomationUiQueryBaselineFilter().doFilter(request, response, filterChain);

        assertThat(AutomationUiQueryBaselineRecorder.isActive()).isFalse();
    }

    private void assertBaselineLog(String operation, int responseBytes) {
        assertThat(appender.list).singleElement().satisfies(event -> {
            String message = event.getFormattedMessage();
            assertThat(message).contains("operation=" + operation, "responseBytes=" + responseBytes);
            assertThat(message).doesNotContain("stream-body", "中文正文");
        });
    }
}
