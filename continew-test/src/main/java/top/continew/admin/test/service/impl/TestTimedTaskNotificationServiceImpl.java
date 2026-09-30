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

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.EscapeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestTimedTaskMapper;
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.service.TestTimedTaskNotificationService;
import top.continew.starter.core.autoconfigure.project.ProjectProperties;
import top.continew.starter.messaging.mail.util.MailUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮件发送失败只记录到运行记录，不能反向修改测试执行结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestTimedTaskNotificationServiceImpl implements TestTimedTaskNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss");

    private final TestTimedTaskRunMapper runMapper;
    private final TestTimedTaskMapper taskMapper;
    private final TestPlanMapper testPlanMapper;
    private final TestReportMapper reportMapper;
    private final ProjectProperties projectProperties;

    @Async
    @Override
    public void send(Long runId) {
        TestTimedTaskRunDO run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        List<String> recipients = resolveRecipients(run);
        if (recipients.isEmpty()) {
            updateStatus(runId, "FAILED", "未配置通知邮箱");
            return;
        }
        List<String> errors = new ArrayList<>();
        String subject = "【SakurA Platform 测试计划】%s - %s".formatted(run.getTaskName(), statusLabel(run.getStatus()));
        String content = buildContent(run);
        for (String recipient : recipients) {
            try {
                sendToRecipient(recipient, subject, content);
                log.info("测试定时任务通知发送成功，runId={}，recipient={}", runId, recipient);
            } catch (Exception e) {
                // 单个收件人失败不能中断后续地址；SMTP 异常可能以运行时异常形式抛出。
                log.warn("发送测试定时任务通知失败，继续发送后续收件人，runId={}，recipient={}", runId, recipient, e);
                errors.add(recipient + "：" + CharSequenceUtil.subWithLength(String.valueOf(e.getMessage()), 0, 160));
            }
        }
        log.info("测试定时任务通知发送完成，runId={}，total={}，failed={}", runId, recipients.size(), errors.size());
        if (errors.isEmpty()) {
            updateStatus(runId, "SENT", null);
        } else {
            updateStatus(runId, "FAILED", CharSequenceUtil.subWithLength(String.join("；", errors), 0, 500));
        }
    }

    private void sendToRecipient(String recipient, String subject, String content) throws Exception {
        MailUtils.sendHtml(recipient, subject, content);
    }

    private List<String> resolveRecipients(TestTimedTaskRunDO run) {
        LinkedHashSet<String> uniqueRecipients = new LinkedHashSet<>();
        addRecipients(uniqueRecipients, run.getNotificationEmails());

        // 执行记录可能由旧版本创建而只保存首个邮箱；当前任务配置是补齐收件人的事实来源。
        TestTimedTaskDO task = run.getTimedTaskId() == null ? null : taskMapper.selectById(run.getTimedTaskId());
        if (task != null) {
            addRecipients(uniqueRecipients, task.getNotificationEmails());
            if (task.getNotificationEmails() == null || task.getNotificationEmails().isEmpty()) {
                addRecipients(uniqueRecipients, CharSequenceUtil.isBlank(task.getExecuteEmail())
                    ? List.of()
                    : List.of(task.getExecuteEmail()));
            }
        }

        return new ArrayList<>(uniqueRecipients);
    }

    private void addRecipients(LinkedHashSet<String> recipients, List<String> values) {
        if (values == null) {
            return;
        }
        for (String recipient : values) {
            if (CharSequenceUtil.isNotBlank(recipient)) {
                recipients.add(recipient.trim().toLowerCase());
            }
        }
    }

    private String buildContent(TestTimedTaskRunDO run) {
        TestPlanDO plan = run.getTestPlanId() == null ? null : testPlanMapper.selectById(run.getTestPlanId());
        TestReportDO report = run.getTestReportId() == null ? null : reportMapper.selectById(run.getTestReportId());
        Map<String, Object> statistic = resolveUiStatistic(report);
        StringBuilder content = new StringBuilder(1024);
        content.append("<h3>SakurA Platform 测试计划执行结果</h3><table style=\"border-collapse:collapse\">");
        row(content, "测试任务", run.getTaskName());
        row(content, "测试计划", run.getTestPlanName());
        row(content, "计划类型", plan == null ? "-" : plan.getType());
        row(content, "触发方式", "SCHEDULE".equals(run.getTriggerMode()) ? "定时执行" : "手动执行");
        row(content, "开始时间", formatDateTime(run.getStartTime()));
        row(content, "结束时间", formatDateTime(run.getEndTime()));
        row(content, "执行耗时", formatRunTime(run));
        row(content, "执行结果", statusLabel(run.getStatus()));
        if (CharSequenceUtil.isNotBlank(run.getFailureReason())) {
            row(content, "失败原因", run.getFailureReason());
        }
        content.append("</table>");
        appendStatistic(content, statistic);
        appendLink(content, "查看测试报告", buildReportPageUrl(run, report));
        appendLink(content, "查看 Jenkins 控制台", run.getConsoleUrl());
        return content.toString();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMATTER);
    }

    private String formatRunTime(TestTimedTaskRunDO run) {
        Long runTime = run.getStartTime() != null && run.getEndTime() != null
            ? Math.max(0, Duration.between(run.getStartTime(), run.getEndTime()).toMillis())
            : run.getRunTime();
        if (runTime == null) {
            return "-";
        }
        // 开始和结束时间是定时任务的完整执行区间，不能使用测试报告内部耗时替代。
        return BigDecimal.valueOf(runTime).movePointLeft(3).setScale(2, RoundingMode.HALF_UP).toPlainString() + "s";
    }

    private void appendStatistic(StringBuilder content, Map<String, Object> statistic) {
        content.append("<h4>执行统计</h4><table style=\"border-collapse:collapse;width:100%;max-width:900px\">")
            .append("<tr>");
        statisticCell(content, "总场景", number(statistic.get("sceneTotal")), "#111");
        statisticCell(content, "已完成", completedCount(statistic), "#155eef");
        statisticCell(content, "通过", number(statistic.get("scenePass")), "#00a854");
        statisticCell(content, "失败", number(statistic.get("sceneFail")), "#f5222d");
        statisticCell(content, "阻塞", number(statistic.get("sceneBlocked")), "#ff7d00");
        statisticCell(content, "取消", number(statistic.get("sceneCancelled")), "#111");
        statisticCell(content, "跳过", number(statistic.get("sceneSkip")), "#111");
        content.append("</tr></table>");
    }

    private void statisticCell(StringBuilder content, String label, int value, String color) {
        content.append("<td style=\"padding:8px 10px;border:1px solid #ddd;text-align:center;min-width:80px\">")
            .append("<div style=\"font-size:20px;font-weight:600;color:")
            .append(color)
            .append("\">")
            .append(value)
            .append("</div><div style=\"color:#666;font-size:12px\">")
            .append(escape(label))
            .append("</div></td>");
    }

    private int completedCount(Map<String, Object> statistic) {
        if (statistic.containsKey("sceneCompleted")) {
            return number(statistic.get("sceneCompleted"));
        }
        return number(statistic.get("scenePass")) + number(statistic.get("sceneFail")) + number(statistic
            .get("sceneCancelled")) + number(statistic.get("sceneSkip"));
    }

    private Map<String, Object> resolveUiStatistic(TestReportDO report) {
        if (report == null || report.getStatisticAnalysis() == null) {
            return Map.of();
        }
        Object ui = report.getStatisticAnalysis().get("ui");
        if (!(ui instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String buildReportPageUrl(TestTimedTaskRunDO run, TestReportDO report) {
        if (run.getTestReportId() == null || projectProperties == null || CharSequenceUtil.isBlank(projectProperties
            .getUrl())) {
            return run.getReportUrl();
        }
        Long testPlanId = run.getTestPlanId() == null && report != null ? report.getTestPlanId() : run.getTestPlanId();
        String baseUrl = projectProperties.getUrl().replaceAll("/+$", "");
        String reportUrl = "%s/test/testReport?id=%s".formatted(baseUrl, run.getTestReportId());
        return testPlanId == null ? reportUrl : "%s&testPlanId=%s".formatted(reportUrl, testPlanId);
    }

    private void row(StringBuilder content, String label, Object value) {
        content.append("<tr><td style=\"padding:6px 12px;border:1px solid #ddd;color:#666\">")
            .append(escape(label))
            .append("</td><td style=\"padding:6px 12px;border:1px solid #ddd\">")
            .append(escape(value))
            .append("</td></tr>");
    }

    private void appendLink(StringBuilder content, String label, String url) {
        if (CharSequenceUtil.isBlank(url)) {
            return;
        }
        content.append("<p><a href=\"").append(escape(url)).append("\">").append(escape(label)).append("</a></p>");
    }

    private String escape(Object value) {
        return EscapeUtil.escapeHtml4(value == null ? "-" : String.valueOf(value));
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PASSED" -> "通过";
            case "SKIPPED" -> "已跳过";
            case "RUNNING" -> "执行中";
            default -> "失败";
        };
    }

    private void updateStatus(Long runId, String status, String error) {
        runMapper.lambdaUpdate()
            .eq(TestTimedTaskRunDO::getId, runId)
            .set(TestTimedTaskRunDO::getNotificationStatus, status)
            .set(TestTimedTaskRunDO::getNotificationError, error)
            .update();
    }
}
