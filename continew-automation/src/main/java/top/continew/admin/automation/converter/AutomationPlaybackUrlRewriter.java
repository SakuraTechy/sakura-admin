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

package top.continew.admin.automation.converter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import top.continew.admin.automation.model.resp.playwright.AutomationPlaywrightCaseResp;
import top.continew.starter.core.exception.BusinessException;

/**
 * 根据产品环境改写执行时用例 URL。
 *
 * <p>该转换只作用于 extractor 生成的响应副本，禁止回写 caseList，
 * 从而保证原始 playwright_step 和 locator_meta 始终保持不变。</p>
 */
@Component
public class AutomationPlaybackUrlRewriter {

    private static final List<String> STEP_URL_FIELDS = List.of("url", "start_url", "end_url");

    public void rewrite(AutomationPlaywrightCaseResp testCase, String targetAddress, String configuredPort) {
        String sourceStartUrl = StringUtils.firstNonBlank(testCase.getStart_url(), testCase.getStartUrl());
        String rewrittenStartUrl = rewriteUrl(sourceStartUrl, targetAddress, configuredPort);
        testCase.setStartUrl(rewrittenStartUrl);
        testCase.setStart_url(rewrittenStartUrl);

        if (testCase.getSteps() != null) {
            for (Map<String, Object> step : testCase.getSteps()) {
                rewriteStep(step, targetAddress, configuredPort);
            }
        }
        String environmentOrigin = extractOrigin(rewrittenStartUrl);
        testCase.setEnvironmentOrigin(environmentOrigin);
        testCase.setEnvironment_origin(environmentOrigin);
    }

    public String rewriteUrl(String sourceUrl, String targetAddress, String configuredPort) {
        URI source = parseAbsoluteHttpUri(sourceUrl);
        if (source == null) {
            return sourceUrl;
        }
        TargetOrigin target = resolveTargetOrigin(source, targetAddress, configuredPort);
        try {
            return new URI(target.scheme(), null, target.host(), target.port(), source.getRawPath(), source
                .getRawQuery(), source.getRawFragment()).toASCIIString();
        } catch (URISyntaxException e) {
            throw new BusinessException("回放地址改写失败，sourceUrl=" + sourceUrl + "，targetAddress=" + targetAddress);
        }
    }

    private void rewriteStep(Map<String, Object> step, String targetAddress, String configuredPort) {
        if (step == null) {
            return;
        }
        for (String field : STEP_URL_FIELDS) {
            Object value = step.get(field);
            if (value != null) {
                step.put(field, rewriteUrl(String.valueOf(value), targetAddress, configuredPort));
            }
        }
        String actionType = String.valueOf(step.getOrDefault("action_type", ""));
        Object value = step.get("value");
        if ("navigate".equalsIgnoreCase(actionType) && value != null) {
            step.put("value", rewriteUrl(String.valueOf(value), targetAddress, configuredPort));
        }
    }

    private TargetOrigin resolveTargetOrigin(URI source, String targetAddress, String configuredPort) {
        String address = StringUtils.trimToEmpty(targetAddress);
        if (address.isBlank()) {
            throw new BusinessException("产品环境未配置可用的前端域名或服务器 IP");
        }
        String normalized = address.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")
            ? address
            : source.getScheme() + "://" + address;
        try {
            URI target = URI.create(normalized);
            String scheme = StringUtils.defaultIfBlank(target.getScheme(), source.getScheme()).toLowerCase(Locale.ROOT);
            String host = target.getHost();
            if (StringUtils.isBlank(host) || !("http".equals(scheme) || "https".equals(scheme))) {
                throw new BusinessException("产品环境前端地址无效：" + targetAddress);
            }
            int port = resolvePort(target.getPort(), source.getPort(), configuredPort);
            return new TargetOrigin(scheme, host, port);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("产品环境前端地址无效：" + targetAddress);
        }
    }

    private int resolvePort(int targetPort, int sourcePort, String configuredPort) {
        String portValue = StringUtils.trimToEmpty(configuredPort);
        if (!portValue.isBlank()) {
            try {
                int port = Integer.parseInt(portValue);
                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }
                return port;
            } catch (NumberFormatException e) {
                throw new BusinessException("产品环境前端端口无效：" + configuredPort);
            }
        }
        return targetPort >= 0 ? targetPort : sourcePort;
    }

    private URI parseAbsoluteHttpUri(String value) {
        String source = StringUtils.trimToEmpty(value);
        if (source.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(source);
            String scheme = StringUtils.lowerCase(uri.getScheme());
            if (!("http".equals(scheme) || "https".equals(scheme)) || StringUtils.isBlank(uri.getHost())) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extractOrigin(String url) {
        URI uri = parseAbsoluteHttpUri(url);
        if (uri == null) {
            return "";
        }
        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    private record TargetOrigin(String scheme, String host, int port) {
    }
}
