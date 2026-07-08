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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.project.mapper.ProjectModuleConfigMapper;
import top.continew.admin.project.model.entity.ProjectModuleConfigDO;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.query.TestMetricQuery;
import top.continew.admin.test.model.resp.TestMetricResp;
import top.continew.admin.test.service.TestMetricService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestMetricServiceImpl implements TestMetricService {

    private final TestPlanMapper testPlanMapper;
    private final TestReportMapper testReportMapper;
    private final TestTimedTaskMapper testTimedTaskMapper;
    private final AutomationUiSceneMapper automationUiSceneMapper;
    private final ProjectModuleConfigMapper projectModuleConfigMapper;

    @Override
    public TestMetricResp getOverview(TestMetricQuery query) {
        Long projectId = resolveProjectId(query);
        Long versionId = resolveVersionId(query);
        List<TestPlanDO> plans = testPlanMapper.lambdaQuery()
            .eq(projectId != null, TestPlanDO::getProjectId, projectId)
            .list();
        List<Long> planIds = plans.stream().map(TestPlanDO::getId).toList();

        List<TestReportDO> reports = testReportMapper.lambdaQuery()
            .eq(projectId != null, TestReportDO::getProjectId, projectId)
            .list();

        List<AutomationUiSceneDO> scenes = automationUiSceneMapper.lambdaQuery()
            .eq(projectId != null, AutomationUiSceneDO::getProjectId, projectId)
            .eq(versionId != null, AutomationUiSceneDO::getVersionId, versionId)
            .list();

        List<ProjectModuleConfigDO> modules = projectModuleConfigMapper.lambdaQuery()
            .eq(projectId != null, ProjectModuleConfigDO::getProjectId, projectId)
            .eq(versionId != null, ProjectModuleConfigDO::getVersionId, versionId)
            .list();

        long timedTaskCount = planIds.isEmpty()
            ? 0L
            : testTimedTaskMapper.lambdaQuery()
                .in(top.continew.admin.test.model.entity.TestTimedTaskDO::getTestPlanId, planIds)
                .count();

        long sceneCount = scenes.size();
        long passedSceneCount = scenes.stream().filter(item -> isPassed(item.getExecuteResult())).count();

        TestMetricResp resp = new TestMetricResp();
        resp.setProjectId(projectId);
        resp.setVersionId(versionId);
        resp.setTestPlanCount((long)plans.size());
        resp.setTestReportCount((long)reports.size());
        resp.setTimedTaskCount(timedTaskCount);
        resp.setSceneCount(sceneCount);
        resp.setPassedSceneCount(passedSceneCount);
        resp.setAutomationPassRate(percent(passedSceneCount, sceneCount));
        resp.setModuleMetric(buildModuleMetric(modules));
        resp.setSceneMetric(buildSceneMetric(scenes));
        resp.setExecutionMetric(buildExecutionMetric(scenes, reports));
        return resp;
    }

    private Long resolveProjectId(TestMetricQuery query) {
        if (query.getProjectId() != null) {
            return query.getProjectId();
        }
        return query.getUi() == null ? null : query.getUi().getProjectId();
    }

    private Long resolveVersionId(TestMetricQuery query) {
        if (query.getVersionId() != null) {
            return query.getVersionId();
        }
        return query.getUi() == null ? null : query.getUi().getVersionId();
    }

    private TestMetricResp.ModuleMetric buildModuleMetric(List<ProjectModuleConfigDO> modules) {
        TestMetricResp.ModuleMetric metric = new TestMetricResp.ModuleMetric();
        metric.setTotalCount((long)modules.size());
        metric.setWeekAddedCount(countBetween(modules.stream()
            .map(ProjectModuleConfigDO::getCreateTime)
            .toList(), startOfWeek(), now()));
        metric.setMonthAddedCount(countBetween(modules.stream()
            .map(ProjectModuleConfigDO::getCreateTime)
            .toList(), startOfMonth(), now()));
        metric.setYearAddedCount(countBetween(modules.stream()
            .map(ProjectModuleConfigDO::getCreateTime)
            .toList(), startOfYear(), now()));
        return metric;
    }

    private TestMetricResp.SceneMetric buildSceneMetric(List<AutomationUiSceneDO> scenes) {
        TestMetricResp.SceneMetric metric = new TestMetricResp.SceneMetric();
        metric.setTotalCount((long)scenes.size());
        metric.setP0Count(countScenesByLevel(scenes, "P0"));
        metric.setP1Count(countScenesByLevel(scenes, "P1"));
        metric.setP2Count(countScenesByLevel(scenes, "P2"));
        metric.setP3Count(countScenesByLevel(scenes, "P3"));
        metric.setWeekAddedCount(countBetween(scenes.stream()
            .map(AutomationUiSceneDO::getCreateTime)
            .toList(), startOfWeek(), now()));
        metric.setMonthAddedCount(countBetween(scenes.stream()
            .map(AutomationUiSceneDO::getCreateTime)
            .toList(), startOfMonth(), now()));
        metric.setYearAddedCount(countBetween(scenes.stream()
            .map(AutomationUiSceneDO::getCreateTime)
            .toList(), startOfYear(), now()));
        metric.setExecutedCount((long)scenes.stream()
            .filter(item -> item.getExecuteStatus() != null && !item.getExecuteStatus().isBlank())
            .count());
        metric.setPassedCount((long)scenes.stream().filter(item -> isPassed(item.getExecuteResult())).count());
        metric.setFailedCount((long)scenes.stream().filter(item -> isFailed(item.getExecuteResult())).count());
        metric.setSkippedCount((long)scenes.stream().filter(item -> isSkipped(item.getExecuteResult())).count());
        return metric;
    }

    private TestMetricResp.ExecutionMetric buildExecutionMetric(List<AutomationUiSceneDO> scenes,
                                                                List<TestReportDO> reports) {
        TestMetricResp.ExecutionMetric metric = new TestMetricResp.ExecutionMetric();
        metric.setTotalReportCount((long)reports.size());
        metric.setWeekRunCount(countBetween(reports.stream()
            .map(TestReportDO::getCreateTime)
            .toList(), startOfWeek(), now()));
        metric.setMonthRunCount(countBetween(reports.stream()
            .map(TestReportDO::getCreateTime)
            .toList(), startOfMonth(), now()));
        metric.setYearRunCount(countBetween(reports.stream()
            .map(TestReportDO::getCreateTime)
            .toList(), startOfYear(), now()));

        long totalRunScenes = 0L;
        long defectCount = 0L;
        long totalRunTime = 0L;
        long totalExecutedScenes = 0L;
        for (TestReportDO report : reports) {
            Map<String, Object> ui = getUiStatistic(report.getStatisticAnalysis());
            int sceneTotal = intValue(ui.get("sceneTotal"));
            int sceneFail = intValue(ui.get("sceneFail"));
            totalRunScenes += sceneTotal;
            defectCount += sceneFail;
            totalRunTime += report.getRunTime() == null ? 0L : report.getRunTime();
            if (sceneTotal > 0) {
                totalExecutedScenes += sceneTotal;
            }
        }

        long sceneCount = scenes.size();
        long executedScenes = scenes.stream()
            .filter(item -> item.getExecuteStatus() != null && !item.getExecuteStatus().isBlank())
            .count();
        long passedScenes = scenes.stream().filter(item -> isPassed(item.getExecuteResult())).count();

        metric.setTotalRunSceneCount(totalRunScenes);
        metric.setDiscoveredDefectCount(defectCount);
        metric.setSavedManHours(BigDecimal.valueOf(totalRunTime)
            .divide(BigDecimal.valueOf(3_600_000L), 2, RoundingMode.HALF_UP));
        metric.setAutomationCoverageRate(percent(executedScenes, sceneCount));
        metric.setAutomationExecuteRate(percent(totalExecutedScenes, sceneCount));
        metric.setAutomationPassRate(percent(passedScenes, sceneCount));
        metric.setDefectRate(percent(defectCount, totalRunScenes));
        return metric;
    }

    private Map<String, Object> getUiStatistic(Map<String, Object> statistic) {
        if (statistic == null) {
            return Map.of();
        }
        Object ui = statistic.get("ui");
        if (ui instanceof Map<?, ?> map) {
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                keys.add(String.valueOf(key));
            }
            return keys.stream().collect(java.util.stream.Collectors.toMap(key -> key, key -> map.get(key)));
        }
        return Map.of();
    }

    private long countScenesByLevel(List<AutomationUiSceneDO> scenes, String level) {
        return scenes.stream().filter(item -> level.equalsIgnoreCase(item.getLevel())).count();
    }

    private long countBetween(List<LocalDateTime> times, LocalDateTime start, LocalDateTime end) {
        return times.stream().filter(time -> time != null && !time.isBefore(start) && !time.isAfter(end)).count();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private LocalDateTime startOfWeek() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        return LocalDateTime.of(monday, LocalTime.MIN);
    }

    private LocalDateTime startOfMonth() {
        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        return LocalDateTime.of(firstDay, LocalTime.MIN);
    }

    private LocalDateTime startOfYear() {
        LocalDate firstDay = LocalDate.now().withDayOfYear(1);
        return LocalDateTime.of(firstDay, LocalTime.MIN);
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isPassed(String result) {
        return "PASSED".equalsIgnoreCase(result) || "全部通过".equals(result);
    }

    private boolean isFailed(String result) {
        return "FAILED".equalsIgnoreCase(result) || "不通过".equals(result);
    }

    private boolean isSkipped(String result) {
        return "SKIPPED".equalsIgnoreCase(result) || "跳过".equals(result);
    }
}
