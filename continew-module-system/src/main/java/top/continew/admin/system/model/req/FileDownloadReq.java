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
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 下载远程文件到服务器参数
 *
 * @author Sakura
 * @since 2026/09/03
 */
@Data
@Schema(description = "下载远程文件到服务器参数")
public class FileDownloadReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 下载URL
     */
    @Schema(description = "下载URL", example = "https://example.com/file.zip")
    @NotBlank(message = "下载URL不能为空")
    @Length(max = 2000, message = "下载URL长度不能超过 {max} 个字符")
    private String url;

    /**
     * 授权token
     */
    @Schema(description = "授权token", example = "Bearer xxx")
    @NotBlank(message = "授权token不能为空")
    @Length(max = 500, message = "授权token长度不能超过 {max} 个字符")
    private String authorization;

    /**
     * 服务器保存路径
     */
    @Schema(description = "服务器保存路径", example = "/data/ankki/license/")
    @NotBlank(message = "保存路径不能为空")
    @Length(max = 500, message = "保存路径长度不能超过 {max} 个字符")
    private String savePath;

    /**
     * 文件名
     */
    @Schema(description = "文件名", example = "certificate.zip")
    @NotBlank(message = "文件名不能为空")
    @Length(max = 255, message = "文件名长度不能超过 {max} 个字符")
    private String fileName;
}
