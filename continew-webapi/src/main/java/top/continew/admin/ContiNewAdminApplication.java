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

package top.continew.admin;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alicp.jetcache.anno.config.EnableMethodCache;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.core.autoconfigure.project.ProjectProperties;
import top.continew.starter.extension.crud.annotation.EnableCrudRestController;
import top.continew.starter.web.annotation.EnableGlobalResponse;
import top.continew.starter.web.model.R;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Provider;
import java.security.Security;

/**
 * 启动程序
 *
 * @author Charles7c
 * @since 2022/12/8 23:15
 */
@Slf4j
@EnableFileStorage
@EnableMethodCache(basePackages = "top.continew.admin")
@EnableGlobalResponse
@EnableCrudRestController
@EnableFeignClients
@RestController
@SpringBootApplication
@RequiredArgsConstructor
@MapperScan({"top.continew.admin.system.mapper"})
public class ContiNewAdminApplication implements ApplicationRunner {

    private static final String SECURITY_DIAGNOSE_SWITCH = "sakura.security.diagnose";

    private final ProjectProperties projectProperties;
    private final ServerProperties serverProperties;

    public static void main(String[] args) {
        SpringApplication.run(ContiNewAdminApplication.class, args);
    }

    @Hidden
    @SaIgnore
    @GetMapping("/")
    public R index() {
        //        return R.ok(projectProperties);
        return R.ok("%s service started successfully.".formatted(projectProperties.getName()), null);
    }

    @Override
    public void run(ApplicationArguments args) throws UnknownHostException {
        logSecurityProviders();
        //        String hostAddress = NetUtil.getLocalhostStr();
        //        Integer port = serverProperties.getPort();
        //        String contextPath = serverProperties.getServlet().getContextPath();
        //        String baseUrl = URLUtil.normalize("%s:%s%s".formatted(hostAddress, port, contextPath));
        //        String hostAddress = NetUtil.getLocalhost().getHostAddress();
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        Integer port = serverProperties.getPort();
        String contextPath = serverProperties.getServlet().getContextPath();
        String baseUrl = URLUtil.normalize("%s:%s%s".formatted(hostAddress, port, contextPath));
        log.info("----------------------------------------------");
        log.info("{} service started successfully.", projectProperties.getName());
        log.info("API地址：{}", baseUrl);
        Knife4jProperties knife4jProperties = SpringUtil.getBean(Knife4jProperties.class);
        if (!knife4jProperties.isProduction()) {
            log.info("API文档：{}/doc.html", baseUrl);
        }
        log.info("在线文档：https://sakura.hk.cn");
        log.info("常见问题：https://sakura.hk.cn/src/zh/3.其它/1.常见问题");
        log.info("----------------------------------------------");
    }

    private void logSecurityProviders() {
        if (!Boolean.getBoolean(SECURITY_DIAGNOSE_SWITCH)) {
            return;
        }
        log.info("[Security Diagnose] switch '{}=true' enabled", SECURITY_DIAGNOSE_SWITCH);
        log.info("[Security Diagnose] java.home={}", System.getProperty("java.home"));
        Provider[] providers = Security.getProviders();
        log.info("[Security Diagnose] providers count={}", providers.length);
        for (int i = 0; i < providers.length; i++) {
            Provider provider = providers[i];
            String providerClass = provider.getClass().getName();
            String providerCodeSource = provider.getClass().getProtectionDomain().getCodeSource() == null
                ? "null"
                : String.valueOf(provider.getClass().getProtectionDomain().getCodeSource().getLocation());
            log.info("[Security Diagnose] provider[{}] name={}, version={}, class={}, source={}", i + 1, provider
                .getName(), provider.getVersionStr(), providerClass, providerCodeSource);
        }
    }
}
