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

package top.continew.admin.common.jenkins;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.lang3.StringUtils;

/**
 * 连接 Jenkins
 */
public class JenkinsConnect {

    private JenkinsConnect() {
    }

    /**
     * Http 客户端工具
     *
     * 如果有些 API 该Jar工具包未提供，可以用此Http客户端操作远程接口，执行命令
     * 
     * @return
     */
    public static JenkinsHttpClient getClient() {
        JenkinsHttpClient jenkinsHttpClient = null;
        try {
            jenkinsHttpClient = new JenkinsHttpClient(new URI(configuredUrl()), configuredUsername(), configuredPassword());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Jenkins URL 配置无效", e);
        }
        return jenkinsHttpClient;
    }

    /**
     * 连接 Jenkins
     */
    public static JenkinsServer connection() {
        JenkinsServer jenkinsServer = null;
        try {
            jenkinsServer = new JenkinsServer(new URI(configuredUrl()), configuredUsername(), configuredPassword());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Jenkins URL 配置无效", e);
        }
        return jenkinsServer;
    }

    /**
     * 连接 Jenkins
     */
    public static JenkinsServer connection(String url, String userName, String passWord) {
        JenkinsServer jenkinsServer = null;
        try {
            jenkinsServer = new JenkinsServer(new URI(url), userName, passWord);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Jenkins URL 配置无效", e);
        }
        return jenkinsServer;
    }

    private static String configuredUrl() {
        return requiredSetting("sakura.jenkins.url", "SAKURA_JENKINS_URL");
    }

    private static String configuredUsername() {
        return requiredSetting("sakura.jenkins.username", "SAKURA_JENKINS_USERNAME");
    }

    private static String configuredPassword() {
        return requiredSetting("sakura.jenkins.password", "SAKURA_JENKINS_PASSWORD");
    }

    private static String requiredSetting(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (StringUtils.isBlank(value)) {
            value = System.getenv(environmentName);
        }
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("缺少 Jenkins 配置：" + propertyName + " 或环境变量 " + environmentName);
        }
        return value.trim();
    }
}
