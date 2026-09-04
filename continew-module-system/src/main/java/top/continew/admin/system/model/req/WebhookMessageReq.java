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

package top.continew.admin.system.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 发送企业微信消息参数
 *
 * @author Sakura
 * @since 2026/09/03
 */
@Data
@Schema(description = "发送企业微信消息参数")
public class WebhookMessageReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 企业微信Webhook地址
     */
    @Schema(description = "企业微信Webhook地址", example = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx")
    @NotBlank(message = "Webhook地址不能为空")
    @Length(max = 500, message = "Webhook地址长度不能超过 {max} 个字符")
    private String webhook;

    /**
     * 证书信息列表
     */
    @Schema(description = "证书信息列表")
    @NotEmpty(message = "证书信息列表不能为空")
    @Valid
    private List<CertificateMarkdown> markdownList;

    /**
     * 证书Markdown信息
     */
    @Data
    @Schema(description = "证书Markdown信息")
    public static class CertificateMarkdown implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 订单编号
         */
        @Schema(description = "订单编号")
        private String orderId;

        /**
         * 申请人
         */
        @Schema(description = "申请人")
        private String userName;

        /**
         * 产品名称
         */
        @Schema(description = "产品名称")
        private String productChName;

        /**
         * 产品版本
         */
        @Schema(description = "产品版本")
        private String productVersionNumber;

        /**
         * 产品型号
         */
        @Schema(description = "产品型号")
        private String typeName;

        /**
         * 证书编号
         */
        @Schema(description = "证书编号")
        private String machineCodeMd;

        /**
         * 机器码文件名
         */
        @Schema(description = "机器码文件名")
        private String uploadFileName;

        /**
         * 制作人
         */
        @Schema(description = "制作人")
        private String makeUserName;

        /**
         * 制作状态
         */
        @Schema(description = "制作状态")
        private String certificateState;

        /**
         * 制作时间
         */
        @Schema(description = "制作时间")
        private String makeTime;

        /**
         * 授权期限
         */
        @Schema(description = "授权期限")
        private String authorizationDeadlineTime;

        /**
         * 维保期限
         */
        @Schema(description = "维保期限")
        private String maintenanceWarnDate;

        /**
         * 证书文件路径
         */
        @Schema(description = "证书文件路径")
        private String fileName;
    }
}
