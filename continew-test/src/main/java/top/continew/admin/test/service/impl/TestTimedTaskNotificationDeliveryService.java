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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.test.mapper.TestTimedTaskRunMapper;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.starter.messaging.mail.util.MailUtils;

@Slf4j
@Service
public class TestTimedTaskNotificationDeliveryService {

    private final TestTimedTaskRunMapper runMapper;

    public TestTimedTaskNotificationDeliveryService(TestTimedTaskRunMapper runMapper) {
        this.runMapper = runMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeliveryResult sendRecipient(Long runId, String recipient, String subject, String content) {
        TestTimedTaskRunDO run = runMapper.selectByIdForUpdate(runId);
        if (run == null || "CANCELLED".equalsIgnoreCase(run.getStatus()) || !"SENDING".equalsIgnoreCase(run
            .getNotificationStatus())) {
            return new DeliveryResult(false, null);
        }
        try {
            MailUtils.sendHtml(recipient, subject, content);
            return new DeliveryResult(true, null);
        } catch (Exception e) {
            log.warn("发送测试定时任务通知失败，runId={}，recipient={}", runId, recipient, e);
            throw new RuntimeException(e);
        }
    }

    public record DeliveryResult(boolean delivered, String error) {
    }
}
