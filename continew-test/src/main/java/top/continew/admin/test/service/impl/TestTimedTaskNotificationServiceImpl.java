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
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.admin.test.service.TestTimedTaskNotificationService;
import top.continew.starter.messaging.mail.util.MailUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件发送失败只记录到运行记录，不能反向修改测试执行结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestTimedTaskNotificationServiceImpl implements TestTimedTaskNotificationService {

    private final TestTimedTaskRunMapper runMapper;

    @Async
    @Override
    public void send(Long runId) {
        if (runId == null || runMapper.claimNotification(runId) != 1) {
            return;
        }
        TestTimedTaskRunDO run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        List<String> recipients = run.getNotificationEmails() == null ? List.of() : run.getNotificationEmails();
        if (recipients.isEmpty()) {
            updateStatus(runId, "FAILED", "未配置通知邮箱");
            return;
        }
        List<String> errors = new ArrayList<>();
        String subject = "【Sakura 测试计划】%s - %s".formatted(run.getTaskName(), statusLabel(run.getStatus()));
        String content = buildContent(run);
        for (String recipient : recipients) {
            try {
                MailUtils.sendHtml(recipient, subject, content);
            } catch (Exception e) {
                log.warn("发送测试定时任务通知失败，runId={}，recipient={}", runId, recipient, e);
                errors.add(recipient + "：" + CharSequenceUtil.subWithLength(String.valueOf(e.getMessage()), 0, 160));
            }
        }
        if (errors.isEmpty()) {
            updateStatus(runId, "SENT", null);
        } else {
            updateStatus(runId, "FAILED", CharSequenceUtil.subWithLength(String.join("；", errors), 0, 500));
        }
    }

    private String buildContent(TestTimedTaskRunDO run) {
        StringBuilder content = new StringBuilder(512);
        content.append("<h3>Sakura 测试计划执行结果</h3><table style=\"border-collapse:collapse\">");
        row(content, "任务", run.getTaskName());
        row(content, "测试计划", run.getTestPlanName());
        row(content, "触发方式", "SCHEDULE".equals(run.getTriggerMode()) ? "定时" : "手动");
        row(content, "执行结果", statusLabel(run.getStatus()));
        row(content, "开始时间", run.getStartTime());
        row(content, "结束时间", run.getEndTime());
        row(content, "耗时", run.getRunTime() == null ? "-" : run.getRunTime() + " ms");
        if (CharSequenceUtil.isNotBlank(run.getFailureReason())) {
            row(content, "原因", run.getFailureReason());
        }
        content.append("</table>");
        appendLink(content, "查看测试报告", run.getReportUrl());
        appendLink(content, "查看 Jenkins 控制台", run.getConsoleUrl());
        return content.toString();
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
            case "CANCELLED" -> "已取消";
            case "RUNNING" -> "执行中";
            default -> "失败";
        };
    }

    private void updateStatus(Long runId, String status, String error) {
        runMapper.finishNotification(runId, status, error);
    }
}
