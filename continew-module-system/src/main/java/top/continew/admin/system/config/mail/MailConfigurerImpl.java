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

package top.continew.admin.system.config.mail;

import cn.hutool.core.map.MapUtil;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import top.continew.admin.common.constant.SysConstants;
import top.continew.admin.system.enums.OptionCategoryEnum;
import top.continew.admin.system.model.req.MailTestReq;
import top.continew.admin.system.service.OptionService;
import top.continew.starter.messaging.mail.core.MailConfig;
import top.continew.starter.messaging.mail.core.MailConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 邮件配置实现
 *
 * @author Charles7c
 * @since 2024/5/30 22:32
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailConfigurerImpl implements MailConfigurer {

    private final OptionService optionService;

    /**
     * 动态邮件配置存储在系统参数中，未配置 spring.mail.host 时 Spring Boot 不会自动创建邮件 Sender。
     */
    @Bean
    public JavaMailSenderImpl javaMailSender() {
        return new JavaMailSenderImpl();
    }

    /**
     * 使用页面当前填写的配置发送测试邮件，不修改系统参数。
     *
     * @param req 邮件测试请求
     * @throws MessagingException 邮件发送失败
     */
    public void sendTestMail(MailTestReq req) throws MessagingException {
        MailConfig mailConfig = new MailConfig();
        mailConfig.setProtocol(req.getProtocol());
        mailConfig.setHost(req.getHost());
        mailConfig.setPort(req.getPort());
        mailConfig.setUsername(req.getUsername());
        mailConfig.setPassword(req.getPassword());
        mailConfig.setFrom(req.getUsername());
        mailConfig.setSslEnabled(Boolean.TRUE.equals(req.getSslEnabled()));
        if (mailConfig.isSslEnabled()) {
            mailConfig.setSslPort(req.getSslPort());
        }
        // 让临时 Sender 与正式动态配置使用相同的 SSL、认证行为。
        mailConfig.getProperties().put("mail.smtp.ssl.enable", String.valueOf(mailConfig.isSslEnabled()));

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        apply(mailConfig, sender);
        MimeMessageHelper helper = new MimeMessageHelper(sender.createMimeMessage(), false, StandardCharsets.UTF_8
            .displayName());
        helper.setFrom(mailConfig.getFrom());
        helper.setTo(req.getRecipient());
        helper.setSubject("邮件配置测试");
        helper.setText("这是一封邮件配置测试邮件，说明 SMTP 连接和实际投递均已成功。", false);
        sender.send(helper.getMimeMessage());
    }

    @Override
    public MailConfig getMailConfig() {
        // 查询邮件配置
        Map<String, String> map = optionService.getByCategory(OptionCategoryEnum.MAIL);
        // 封装邮件配置
        MailConfig mailConfig = new MailConfig();
        mailConfig.setProtocol(MapUtil.getStr(map, "MAIL_PROTOCOL"));
        mailConfig.setHost(MapUtil.getStr(map, "MAIL_HOST"));
        mailConfig.setPort(MapUtil.getInt(map, "MAIL_PORT"));
        mailConfig.setUsername(MapUtil.getStr(map, "MAIL_USERNAME"));
        mailConfig.setPassword(MapUtil.getStr(map, "MAIL_PASSWORD"));
        mailConfig.setFrom(mailConfig.getUsername());
        mailConfig.setSslEnabled(SysConstants.YES.equals(MapUtil.getInt(map, "MAIL_SSL_ENABLED")));
        // 非空属性用于触发 starter 将 SSL、认证等配置写入 JavaMail；否则动态配置只会覆盖主机和账号。
        mailConfig.getProperties().put("mail.smtp.ssl.enable", String.valueOf(mailConfig.isSslEnabled()));
        if (mailConfig.isSslEnabled()) {
            mailConfig.setSslPort(MapUtil.getInt(map, "MAIL_SSL_PORT"));
        }
        return mailConfig;
    }
}
