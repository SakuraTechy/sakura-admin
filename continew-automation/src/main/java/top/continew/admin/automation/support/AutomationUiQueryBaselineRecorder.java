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

import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

/**
 * UI 自动化旧查询基线探针。
 *
 * <p>探针只保存计数和耗时，不保存资源 ID、SQL、参数或响应正文。</p>
 */
public final class AutomationUiQueryBaselineRecorder {

    private static final ThreadLocal<Probe> CURRENT = new ThreadLocal<>();

    private AutomationUiQueryBaselineRecorder() {
    }

    public static void begin(String operation) {
        CURRENT.set(new Probe(operation, System.nanoTime()));
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static void recordSql() {
        recordSql(1);
    }

    public static void recordSql(int count) {
        Probe probe = CURRENT.get();
        if (probe != null) {
            probe.instrumentedSqlCount += Math.max(0, count);
        }
    }

    public static void recordHistoryLimit(int limit) {
        Probe probe = CURRENT.get();
        if (probe != null) {
            probe.historyLimit = Math.max(probe.historyLimit, limit);
        }
    }

    public static void recordExecutionRows(int count) {
        Probe probe = CURRENT.get();
        if (probe != null) {
            probe.executionRows += Math.max(0, count);
        }
    }

    public static void recordCaseRows(int count) {
        Probe probe = CURRENT.get();
        if (probe != null) {
            probe.caseRows += Math.max(0, count);
        }
    }

    public static void recordStepRows(int count) {
        Probe probe = CURRENT.get();
        if (probe != null) {
            probe.stepRows += Math.max(0, count);
        }
    }

    public static void recordExternalCall(long startedNanos) {
        Probe probe = CURRENT.get();
        if (probe != null && startedNanos > 0) {
            probe.externalCallCount++;
            probe.externalCallNanos += Math.max(0, System.nanoTime() - startedNanos);
        }
    }

    public static long startExternalCall() {
        return startTimedSection();
    }

    public static long startTimedSection() {
        return CURRENT.get() == null ? 0 : System.nanoTime();
    }

    public static void recordTiming(Phase phase, long startedNanos) {
        Probe probe = CURRENT.get();
        if (probe != null && phase != null && startedNanos > 0) {
            probe.phaseNanos.merge(phase, Math.max(0, System.nanoTime() - startedNanos), Long::sum);
        }
    }

    public static long startHeapSample() {
        return CURRENT.get() == null ? -1 : usedHeapBytes();
    }

    public static void recordHeapSample(long usedHeapBeforeBytes) {
        Probe probe = CURRENT.get();
        if (probe != null && usedHeapBeforeBytes >= 0) {
            probe.heapDeltaBytes = Math.max(probe.heapDeltaBytes, Math.max(0, usedHeapBytes() - usedHeapBeforeBytes));
        }
    }

    public static void recordInMemoryPayloadBytes(long bytes) {
        Probe probe = CURRENT.get();
        if (probe != null) {
            probe.inMemoryPayloadBytes = Math.max(probe.inMemoryPayloadBytes, Math.max(0, bytes));
        }
    }

    public static void markBodyReady() {
        Probe probe = CURRENT.get();
        if (probe != null && probe.bodyReadyNanos == 0) {
            probe.bodyReadyNanos = System.nanoTime();
        }
    }

    public static Snapshot finish(int status, long responseBytes) {
        Probe probe = CURRENT.get();
        CURRENT.remove();
        if (probe == null) {
            return null;
        }
        long finishedNanos = System.nanoTime();
        long bodyReadyNanos = probe.bodyReadyNanos == 0 ? finishedNanos : probe.bodyReadyNanos;
        long controllerNanos = Math.max(0, bodyReadyNanos - probe.startedNanos);
        long stepQueryNanos = probe.phaseNanos.getOrDefault(Phase.STEP_QUERY, 0L);
        // case RowMapper 内仍存在 step N+1；扣除嵌套 step 耗时后才能得到不重复的 case 查询/映射耗时。
        long caseQueryNanos = Math.max(0, probe.phaseNanos.getOrDefault(Phase.CASE_QUERY, 0L) - stepQueryNanos);
        long accountedControllerNanos = probe.phaseNanos.getOrDefault(Phase.SCENE_QUERY, 0L) + probe.phaseNanos
            .getOrDefault(Phase.EXECUTION_IDS_QUERY, 0L) + probe.phaseNanos
                .getOrDefault(Phase.EXECUTION_QUERY, 0L) + caseQueryNanos + stepQueryNanos + probe.phaseNanos
                    .getOrDefault(Phase.OTHER_QUERY, 0L) + probe.phaseNanos
                        .getOrDefault(Phase.MASKING, 0L) + probe.externalCallNanos;
        long unaccountedControllerNanos = Math.max(0, controllerNanos - accountedControllerNanos);
        return new Snapshot(probe.operation, status, elapsedMillis(probe.startedNanos, finishedNanos), TimeUnit.NANOSECONDS
            .toMillis(controllerNanos), elapsedMillis(bodyReadyNanos, finishedNanos), Math
                .max(0, responseBytes), probe.instrumentedSqlCount, probe.executionRows, probe.caseRows, probe.stepRows, probe.historyLimit, probe.externalCallCount, TimeUnit.NANOSECONDS
                    .toMillis(probe.externalCallNanos), phaseMillis(probe, Phase.SCENE_QUERY), phaseMillis(probe, Phase.EXECUTION_IDS_QUERY), phaseMillis(probe, Phase.EXECUTION_QUERY), TimeUnit.NANOSECONDS
                        .toMillis(caseQueryNanos), TimeUnit.NANOSECONDS
                            .toMillis(stepQueryNanos), phaseMillis(probe, Phase.OTHER_QUERY), phaseMillis(probe, Phase.MASKING), TimeUnit.NANOSECONDS
                                .toMillis(unaccountedControllerNanos), probe.inMemoryPayloadBytes, probe.heapDeltaBytes);
    }

    static void clear() {
        CURRENT.remove();
    }

    private static long elapsedMillis(long startNanos, long endNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, endNanos - startNanos));
    }

    private static long phaseMillis(Probe probe, Phase phase) {
        return TimeUnit.NANOSECONDS.toMillis(probe.phaseNanos.getOrDefault(phase, 0L));
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0, runtime.totalMemory() - runtime.freeMemory());
    }

    public enum Phase {
        SCENE_QUERY, EXECUTION_IDS_QUERY, EXECUTION_QUERY, CASE_QUERY, STEP_QUERY, OTHER_QUERY, MASKING
    }

    public record Snapshot(String operation, int status, long elapsedMs, long controllerMs, long serializationMs,
                           long responseBytes, int instrumentedSqlCount, int executionRows, int caseRows, int stepRows,
                           int historyLimit, int externalCallCount, long externalCallMs, long sceneQueryMs,
                           long executionIdsQueryMs, long executionQueryMs, long caseQueryMs, long stepQueryMs,
                           long otherQueryMs, long maskingMs, long unaccountedControllerMs, long inMemoryPayloadBytes,
                           long heapDeltaBytes) {
    }

    private static final class Probe {
        private final String operation;
        private final long startedNanos;
        private long bodyReadyNanos;
        private int instrumentedSqlCount;
        private int executionRows;
        private int caseRows;
        private int stepRows;
        private int historyLimit;
        private int externalCallCount;
        private long externalCallNanos;
        private final EnumMap<Phase, Long> phaseNanos = new EnumMap<>(Phase.class);
        private long inMemoryPayloadBytes;
        private long heapDeltaBytes;

        private Probe(String operation, long startedNanos) {
            this.operation = operation;
            this.startedNanos = startedNanos;
        }
    }
}
