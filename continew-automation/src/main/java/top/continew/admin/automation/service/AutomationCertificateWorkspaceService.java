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

package top.continew.admin.automation.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Playwright Runner 证书工作区服务。
 *
 * @author Codex
 */
public interface AutomationCertificateWorkspaceService {

    /**
     * 上传证书到场景所属项目、版本的受控工作区。
     *
     * @param sceneDbId 场景数据库主键
     * @param file      证书文件
     * @return Runner 可读取的相对引用
     */
    CertificateFile upload(Long sceneDbId, MultipartFile file);

    /** 将环境证书保存到系统文件管理的项目/版本 license 目录，环境绑定只保存返回的资产 ID。 */
    CertificateAsset uploadAsset(Long projectId, String versionName, MultipartFile file);

    /** 返回 Runner 根目录下的相对引用，不能写回场景主数据。 */
    String runnerReference(Long assetId, Long projectId);

    /** 返回受控证书资产的本机真实路径，仅用于鉴权下载和 Runner 物化。 */
    java.nio.file.Path assetPath(Long assetId, Long projectId);

    record CertificateFile(String reference, String fileName, long size) {
    }

    record CertificateAsset(Long assetId, String fileName, long size, String sha256) {
    }
}
