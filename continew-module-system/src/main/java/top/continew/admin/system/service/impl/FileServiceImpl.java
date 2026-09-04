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

package top.continew.admin.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.ProgressListener;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.system.enums.FileTypeEnum;
import top.continew.admin.system.mapper.FileMapper;
import top.continew.admin.system.model.entity.FileDO;
import top.continew.admin.system.model.entity.StorageDO;
import top.continew.admin.system.model.query.FileQuery;
import top.continew.admin.system.model.req.FileDownloadReq;
import top.continew.admin.system.model.req.FileReq;
import top.continew.admin.system.model.req.WebhookMessageReq;
import top.continew.admin.system.model.resp.file.FileResp;
import top.continew.admin.system.model.resp.file.FileStatisticsResp;
import top.continew.admin.system.service.FileService;
import top.continew.admin.system.service.StorageService;
import top.continew.starter.core.constant.StringConstants;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.util.StrUtils;
import top.continew.starter.core.util.URLUtils;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件业务实现
 *
 * @author Charles7c
 * @since 2023/12/23 10:38
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends BaseServiceImpl<FileMapper, FileDO, FileResp, FileResp, FileQuery, FileReq> implements FileService {

    private final FileStorageService fileStorageService;
    @Resource
    private StorageService storageService;

    @Override
    protected void beforeDelete(List<Long> ids) {
        List<FileDO> fileList = baseMapper.lambdaQuery().in(FileDO::getId, ids).list();
        Map<Long, List<FileDO>> fileListGroup = fileList.stream().collect(Collectors.groupingBy(FileDO::getStorageId));
        for (Map.Entry<Long, List<FileDO>> entry : fileListGroup.entrySet()) {
            StorageDO storage = storageService.getById(entry.getKey());
            for (FileDO file : entry.getValue()) {
                FileInfo fileInfo = file.toFileInfo(storage);
                fileStorageService.delete(fileInfo);
            }
        }
    }

    @Override
    public FileInfo upload(MultipartFile file, String path, String storageCode) {
        return upload(file, path, storageCode, null);
    }

    @Override
    public FileInfo upload(MultipartFile file, String path, String storageCode, String saveFilename) {
        StorageDO storage;
        if (StrUtil.isBlank(storageCode)) {
            storage = storageService.getDefaultStorage();
            CheckUtils.throwIfNull(storage, "请先指定默认存储");
        } else {
            storage = storageService.getByCode(storageCode);
            CheckUtils.throwIfNotExists(storage, "StorageDO", "Code", storageCode);
        }
        UploadPretreatment uploadPretreatment = fileStorageService.of(file)
            .setPlatform(storage.getCode())
            .setHashCalculatorMd5(true)
            .putAttr(ClassUtil.getClassName(StorageDO.class, false), storage)
            .setPath(path);
        if (StrUtil.isNotBlank(saveFilename)) {
            uploadPretreatment.setSaveFilename(saveFilename);
            uploadPretreatment.setSaveThFilename(FileNameUtil.mainName(saveFilename));
        }
        // 图片文件生成缩略图
        if (FileTypeEnum.IMAGE.getExtensions().contains(FileNameUtil.extName(file.getOriginalFilename()))) {
            uploadPretreatment.thumbnail(img -> img.size(100, 100));
        }
        uploadPretreatment.setProgressMonitor(new ProgressListener() {
            @Override
            public void start() {
                log.info("开始上传");
            }

            @Override
            public void progress(long progressSize, Long allSize) {
                log.info("已上传 [{}]，总大小 [{}]", progressSize, allSize);
            }

            @Override
            public void finish() {
                log.info("上传结束");
            }
        });
        return uploadPretreatment.upload();

    }

    @Override
    public Long countByStorageIds(List<Long> storageIds) {
        if (CollUtil.isEmpty(storageIds)) {
            return 0L;
        }
        return baseMapper.lambdaQuery().in(FileDO::getStorageId, storageIds).count();
    }

    @Override
    public FileStatisticsResp statistics() {
        FileStatisticsResp resp = new FileStatisticsResp();
        List<FileStatisticsResp> statisticsList = baseMapper.statistics();
        if (CollUtil.isEmpty(statisticsList)) {
            return resp;
        }
        resp.setData(statisticsList);
        resp.setSize(statisticsList.stream().mapToLong(FileStatisticsResp::getSize).sum());
        resp.setNumber(statisticsList.stream().mapToLong(FileStatisticsResp::getNumber).sum());
        return resp;
    }

    @Override
    protected void fill(Object obj) {
        super.fill(obj);
        if (obj instanceof FileResp fileResp && !URLUtils.isHttpUrl(fileResp.getUrl())) {
            StorageDO storage = storageService.getById(fileResp.getStorageId());
            String prefix = StrUtil.appendIfMissing(storage.getDomain(), StringConstants.SLASH);
            String url = URLUtil.normalize(prefix + fileResp.getUrl());
            fileResp.setUrl(url);
            String thumbnailUrl = StrUtils.blankToDefault(fileResp.getThumbnailUrl(), url, thUrl -> URLUtil
                .normalize(prefix + thUrl));
            fileResp.setThumbnailUrl(thumbnailUrl);
            fileResp.setStorageName("%s (%s)".formatted(storage.getName(), storage.getCode()));
        }
    }

    @Override
    public String downloadFile(FileDownloadReq req) {
        try {
            log.info("开始下载远程文件: url={}, savePath={}, fileName={}", req.getUrl(), req.getSavePath(), req.getFileName());

            // 确保保存目录存在
            File saveDir = new File(req.getSavePath());
            if (!saveDir.exists()) {
                boolean created = saveDir.mkdirs();
                if (!created) {
                    throw new BusinessException("创建保存目录失败: " + req.getSavePath());
                }
            }

            // 构建完整的文件保存路径
            String fullFilePath = req.getSavePath() + File.separator + req.getFileName();
            File destFile = new File(fullFilePath);

            // 如果文件已存在，先删除
            if (destFile.exists()) {
                log.warn("文件已存在，将被覆盖: ", fullFilePath);
                FileUtil.del(destFile);
            }

            // 下载文件
            HttpResponse response = HttpRequest.get(req.getUrl())
                .header("Authorization", req.getAuthorization())
                .timeout(60000) // 60秒超时
                .executeAsync();

            if (!response.isOk()) {
                throw new BusinessException("下载文件失败，HTTP状态码: " + response.getStatus());
            }

            // 保存文件
            FileUtil.writeBytes(response.bodyBytes(), destFile);

            log.info("文件下载成功: {}", fullFilePath);
            return fullFilePath;

        } catch (Exception e) {
            log.error("下载远程文件失败", e);
            throw new BusinessException("下载文件失败: " + e.getMessage());
        }
    }

    @Override
    public void sendWebhookMessage(WebhookMessageReq req) {
        try {
            log.info("开始发送企业微信消息，证书数量: {}", req.getMarkdownList().size());

            // 保持与老项目 WeChatMessage.sendMessage 一致的 Markdown 格式
            StringBuilder markdownContent = new StringBuilder();
            List<WebhookMessageReq.CertificateMarkdown> markdownList = req.getMarkdownList();
            markdownContent.append("一键自动化制作产品证书成功，<font color=\"warning\">共")
                .append(markdownList.size())
                .append("个</font>，详情如下，请相关同事查收下载。\n");
            for (WebhookMessageReq.CertificateMarkdown markdown : markdownList) {
                if (markdownList.size() > 1) {
                    markdownContent.append("-------------------------------------------------------------------\n");
                }
                appendDynamicContentBlock(markdownContent, markdown);
            }

            // 构建企业微信Webhook请求体
            JSONObject requestBody = new JSONObject();
            requestBody.set("msgtype", "markdown");

            JSONObject markdown = new JSONObject();
            markdown.set("content", markdownContent.toString());
            requestBody.set("markdown", markdown);

            // 发送请求
            HttpResponse response = HttpRequest.post(req.getWebhook())
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(requestBody))
                .timeout(10000) // 10秒超时
                .execute();

            if (!response.isOk()) {
                throw new BusinessException("发送企业微信消息失败，HTTP状态码: " + response.getStatus());
            }

            // 检查响应结果
            String responseBody = response.body();
            JSONObject result = JSONUtil.parseObj(responseBody);
            if (result.getInt("errcode", -1) != 0) {
                throw new BusinessException("发送企业微信消息失败: " + result.getStr("errmsg"));
            }

            log.info("企业微信消息发送成功");

        } catch (Exception e) {
            log.error("发送企业微信消息失败", e);
            throw new BusinessException("发送企业微信消息失败: " + e.getMessage());
        }
    }

    /**
     * 构建老项目使用的单条证书 Markdown 内容
     */
    private void appendDynamicContentBlock(StringBuilder content, WebhookMessageReq.CertificateMarkdown markdown) {
        content.append(">申请编号：<font color=\"info\">").append(markdown.getOrderId()).append("</font>\n");
        content.append(">申请姓名：<font color=\"comment\">").append(markdown.getUserName()).append("</font>\n");
        content.append(">产品名称：<font color=\"comment\">").append(markdown.getProductChName()).append("</font>\n");
        content.append(">产品版本：<font color=\"comment\">").append(markdown.getProductVersionNumber()).append("</font>\n");
        content.append(">产品型号：<font color=\"comment\">").append(markdown.getTypeName()).append("</font>\n");
        content.append(">证书编码：<font color=\"comment\">").append(markdown.getMachineCodeMd()).append("</font>\n");
        content.append(">机器码名：<font color=\"comment\">").append(markdown.getUploadFileName()).append("</font>\n");
        content.append(">制作人名：<font color=\"comment\">").append(markdown.getMakeUserName()).append("</font>\n");
        if ("成功".equals(markdown.getCertificateState())) {
            content.append(">制作状态：<font color=\"info\">").append(markdown.getCertificateState()).append("</font>\n");
        } else {
            content.append(">制作状态：<font color=\"warning\">").append(markdown.getCertificateState()).append("</font>\n");
        }
        content.append(">制作时间：<font color=\"comment\">").append(markdown.getMakeTime()).append("</font>\n");
        content.append(">授权期限：<font color=\"comment\">").append(markdown.getAuthorizationDeadlineTime()).append("</font>\n");
        content.append(">维保期限：<font color=\"comment\">").append(markdown.getMaintenanceWarnDate()).append("</font>\n");
        if ("成功".equals(markdown.getCertificateState())) {
            content.append(">产品证书：[点击下载](").append(markdown.getFileName()).append(")\n");
        } else {
            content.append(">产品证书：<font color=\"warning\">证书制作失败，请重新申请制作！</font>\n");
        }
    }
}
