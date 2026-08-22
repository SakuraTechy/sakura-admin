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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 默认关闭的无正文基线探针，仅用于阶段 0 的预发布和生产核验。 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@ConditionalOnProperty(prefix = "automation.ui-query.baseline", name = "enabled", havingValue = "true")
public class AutomationUiQueryBaselineFilter extends OncePerRequestFilter {

    private static final String SCENE_PATH = "/automation/automationUiScene";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return classify(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String operation = classify(request);
        if (operation == null) {
            filterChain.doFilter(request, response);
            return;
        }
        CountingHttpServletResponse countingResponse = new CountingHttpServletResponse(response);
        AutomationUiQueryBaselineRecorder.begin(operation);
        try {
            filterChain.doFilter(request, countingResponse);
        } finally {
            AutomationUiQueryBaselineRecorder.Snapshot snapshot = AutomationUiQueryBaselineRecorder.finish(response
                .getStatus(), countingResponse.getWrittenBytes());
            if (snapshot != null) {
                // 固定字段可供日志平台聚合；禁止加入 URI、请求参数或响应正文。
                log.info("UI_QUERY_BASELINE operation={} status={} elapsedMs={} controllerMs={} serializationMs={} responseBytes={} instrumentedSqlCount={} executionRows={} caseRows={} stepRows={} historyLimit={} externalCallCount={} externalCallMs={} sceneQueryMs={} executionIdsQueryMs={} executionQueryMs={} caseQueryMs={} stepQueryMs={} otherQueryMs={} maskingMs={} unaccountedControllerMs={} inMemoryPayloadBytes={} heapDeltaBytes={}", snapshot
                    .operation(), snapshot.status(), snapshot.elapsedMs(), snapshot.controllerMs(), snapshot
                        .serializationMs(), snapshot.responseBytes(), snapshot.instrumentedSqlCount(), snapshot
                            .executionRows(), snapshot.caseRows(), snapshot.stepRows(), snapshot
                                .historyLimit(), snapshot.externalCallCount(), snapshot.externalCallMs(), snapshot
                                    .sceneQueryMs(), snapshot.executionIdsQueryMs(), snapshot
                                        .executionQueryMs(), snapshot.caseQueryMs(), snapshot.stepQueryMs(), snapshot
                                            .otherQueryMs(), snapshot.maskingMs(), snapshot
                                                .unaccountedControllerMs(), snapshot.inMemoryPayloadBytes(), snapshot
                                                    .heapDeltaBytes());
            }
        }
    }

    private String classify(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if (SCENE_PATH.equals(path) && "GET".equals(method)) {
            return "scene-page";
        }
        if ((SCENE_PATH + "/list").equals(path) && "GET".equals(method)) {
            return "scene-list";
        }
        if ((SCENE_PATH + "/selected").equals(path) && "POST".equals(method)) {
            return "scene-selected";
        }
        if (path.matches(SCENE_PATH + "/[0-9]+") && "GET".equals(method)) {
            return "scene-detail";
        }
        if ("/automation/operation-catalog".equals(path) && "GET".equals(method)) {
            return "operation-catalog";
        }
        if (path.startsWith("/automation/executions") && "GET".equals(method)) {
            return "execution-query";
        }
        if (path.startsWith("/automation/playwright/artifacts/") && "GET".equals(method)) {
            return "artifact-download";
        }
        return null;
    }

    private static final class CountingHttpServletResponse extends HttpServletResponseWrapper {
        private CountingServletOutputStream outputStream;
        private PrintWriter writer;

        private CountingHttpServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() 已调用，不能再调用 getOutputStream()");
            }
            if (outputStream == null) {
                outputStream = new CountingServletOutputStream(super.getOutputStream());
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer != null) {
                return writer;
            }
            if (outputStream != null) {
                throw new IllegalStateException("getOutputStream() 已调用，不能再调用 getWriter()");
            }
            outputStream = new CountingServletOutputStream(super.getOutputStream());
            writer = new PrintWriter(new OutputStreamWriter(outputStream, Charset
                .forName(getCharacterEncoding())), true);
            return writer;
        }

        private long getWrittenBytes() {
            return outputStream == null ? 0 : outputStream.writtenBytes;
        }
    }

    private static final class CountingServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;
        private long writtenBytes;

        private CountingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            writtenBytes++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            writtenBytes += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
